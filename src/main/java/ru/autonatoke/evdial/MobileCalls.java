package ru.autonatoke.evdial;
import android.app.*;
import android.content.*;
import android.telecom.*;
import java.util.ArrayList;
import java.util.List;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
public class MobileCalls extends InCallService {
 static MobileCalls live;
 CallNotice ringNotice;
 private static final int ONGOING = 19;
 final ArrayList<Call> calls=new ArrayList<>();
 Runnable changed;
 private final Handler audioHandler = new Handler(Looper.getMainLooper());
 private final SpeakerRequest speakerRequest = new SpeakerRequest();
 private final ArrayList<CallEndpoint> endpoints = new ArrayList<>();
 private Call speakerCall;
 private CallEndpoint reportedEndpoint, returnEndpoint;
 private CallAudioState reportedAudio;
 private Boolean reportedMute;
 private int returnRoute = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
 private int routeAttempts, previousSpeakerCallState = -1;
 private long inFlightRevision = -1;
 private boolean routeQueued;
 private final Runnable routeRetry = () -> {routeQueued=false;applySpeakerRequest();};
 final Call.Callback monitor=new Call.Callback(){
  @Override public void onStateChanged(Call call,int state){render();}
  @Override public void onDetailsChanged(Call call,Call.Details d){render();}
 };
 @Override public void onCreate(){super.onCreate();ringNotice=new CallNotice(this,MobileScreen.class,CallNotice.SIM);}
 public void onCallAdded(Call c){super.onCallAdded(c);live=this;calls.add(c);c.registerCallback(monitor);render();if(c.getState()!=Call.STATE_RINGING)startActivity(new Intent(this,MobileScreen.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
 public void onCallRemoved(Call c){c.unregisterCallback(monitor);calls.remove(c);if(c==speakerCall)clearSpeakerRequest();render();super.onCallRemoved(c);}
 public void onCallAudioStateChanged(CallAudioState s){reportedAudio=s;if(s!=null)reportedMute=s.isMuted();audioChanged();}
 public void onCallEndpointChanged(CallEndpoint endpoint){
  if(android.os.Build.VERSION.SDK_INT>=34){
   reportedEndpoint=endpoint;
   audioChanged();
  }
 }
 @Override public void onAvailableCallEndpointsChanged(List<CallEndpoint> available) {
  endpoints.clear();endpoints.addAll(available);
  audioChanged();
 }
 @Override public void onMuteStateChanged(boolean muted) {
  reportedMute=muted;
  if(changed!=null)changed.run();
 }

 static boolean audioControlsAllowed(int state) {
  return state==Call.STATE_NEW || state==Call.STATE_SELECT_PHONE_ACCOUNT ||
    state==Call.STATE_CONNECTING || state==Call.STATE_DIALING || state==Call.STATE_ACTIVE;
 }
 boolean canControlAudio(Call call) {
  return live==this && call!=null && current()==call && audioControlsAllowed(call.getState());
 }
 private CallAudioState audioState() {
  CallAudioState current=getCallAudioState();return current!=null?current:reportedAudio;
 }
 private Boolean actualSpeaker() {
  if(Build.VERSION.SDK_INT>=34){
   CallEndpoint e=reportedEndpoint!=null?reportedEndpoint:getCurrentCallEndpoint();
   if(e!=null)return e.getEndpointType()==CallEndpoint.TYPE_SPEAKER;
  }
  CallAudioState a=audioState();return a==null?null:a.getRoute()==CallAudioState.ROUTE_SPEAKER;
 }
 boolean speakerSelected(Call call) {
  return call==speakerCall?speakerRequest.selected(actualSpeaker()):Boolean.TRUE.equals(actualSpeaker());
 }
 boolean speakerPending(Call call) { return call==speakerCall && speakerRequest.pending(); }
 boolean muteKnown() { return reportedMute!=null || audioState()!=null; }
 boolean audioMuted() { CallAudioState a=audioState();return reportedMute!=null?reportedMute:a!=null&&a.isMuted(); }

 void toggleSpeaker(Call call) {
  if(!canControlAudio(call))return;
  if(speakerCall!=call){clearSpeakerRequest();speakerCall=call;}
  // Restore the previous headset/earpiece instead of forcing the phone's earpiece.
  if(!speakerRequest.selected(actualSpeaker())){
   CallAudioState a=audioState();
   if(a!=null && a.getRoute()!=CallAudioState.ROUTE_SPEAKER)returnRoute=a.getRoute();
   if(Build.VERSION.SDK_INT>=34){
    CallEndpoint e=reportedEndpoint!=null?reportedEndpoint:getCurrentCallEndpoint();
    if(e!=null && e.getEndpointType()!=CallEndpoint.TYPE_SPEAKER)returnEndpoint=e;
   }
  }
  speakerRequest.toggle(actualSpeaker());
  routeAttempts=0;inFlightRevision=-1;
  previousSpeakerCallState=call.getState();
  cancelRouteRetry();
  applySpeakerRequest();
  if(changed!=null)changed.run();
 }

 private void clearSpeakerRequest() {
  cancelRouteRetry();
  speakerRequest.clear();speakerCall=null;returnEndpoint=null;
  returnRoute=CallAudioState.ROUTE_WIRED_OR_EARPIECE;
  routeAttempts=0;inFlightRevision=-1;previousSpeakerCallState=-1;
 }
 private void reconcileSpeakerRequest() {
  if(speakerCall==null)return;
  if(!canControlAudio(speakerCall)){clearSpeakerRequest();return;}
  int state=speakerCall.getState();
  if(previousSpeakerCallState!=state){previousSpeakerCallState=state;routeAttempts=0;}
  speakerRequest.observe(actualSpeaker(),state!=Call.STATE_ACTIVE);
  if(!speakerRequest.pending()){cancelRouteRetry();return;}
  if(routeAttempts<8 && !routeQueued)postRouteRetry(250);
 }
 private void audioChanged() {
  reconcileSpeakerRequest();
  updateProximity(audioState());
  if(changed!=null)changed.run();
 }
 private void cancelRouteRetry() { audioHandler.removeCallbacks(routeRetry);routeQueued=false; }
 private void postRouteRetry(long delay) { routeQueued=true;audioHandler.postDelayed(routeRetry,delay); }
 private void scheduleRouteRetry() {
  cancelRouteRetry();
  if(!speakerRequest.pending())return;
  if(routeAttempts<8)postRouteRetry(400);
  else if(speakerCall!=null && speakerCall.getState()==Call.STATE_ACTIVE){
   clearSpeakerRequest();
   android.widget.Toast.makeText(this,"Не удалось переключить звук. Нажмите «Динамик» ещё раз.",android.widget.Toast.LENGTH_SHORT).show();
   if(changed!=null)changed.run();
  }
 }
 private void applySpeakerRequest() {
  cancelRouteRetry();
  if(speakerCall==null || !canControlAudio(speakerCall)){clearSpeakerRequest();return;}
  speakerRequest.observe(actualSpeaker(),speakerCall.getState()!=Call.STATE_ACTIVE);
  if(!speakerRequest.pending() || inFlightRevision==speakerRequest.revision())return;
  if(routeAttempts++>=8)return;
  final long request=speakerRequest.revision();
  final Call target=speakerCall;
  try {
   if(Build.VERSION.SDK_INT>=34 && !endpoints.isEmpty()){
    CallEndpoint desired=chooseEndpoint(speakerRequest.desired());
    if(desired==null){scheduleRouteRetry();return;}
    inFlightRevision=request;
    requestCallEndpointChange(desired,getMainExecutor(),new OutcomeReceiver<Void,CallEndpointException>(){
     public void onResult(Void ignored){completeRouteRequest(target,request);}
     public void onError(CallEndpointException error){completeRouteRequest(target,request);}
    });
   } else {
    // Android 8-13, and OEM initialization before the endpoint list arrives.
    setAudioRoute(speakerRequest.desired()?CallAudioState.ROUTE_SPEAKER:legacyReturnRoute());
    scheduleRouteRetry();
   }
  } catch(IllegalArgumentException|IllegalStateException|SecurityException unavailable) {
   inFlightRevision=-1;scheduleRouteRetry();
  }
 }
 private void completeRouteRequest(Call target,long request) {
  // Never let a late callback undo a second tap or a new call.
  if(speakerCall!=target || speakerRequest.revision()!=request)return;
  inFlightRevision=-1;
  speakerRequest.observe(actualSpeaker(),target.getState()!=Call.STATE_ACTIVE);
  scheduleRouteRetry();
  if(changed!=null)changed.run();
 }
 @android.annotation.TargetApi(34)
 private CallEndpoint chooseEndpoint(boolean speaker) {
  if(speaker){for(CallEndpoint e:endpoints)if(e.getEndpointType()==CallEndpoint.TYPE_SPEAKER)return e;return null;}
  if(returnEndpoint!=null){for(CallEndpoint e:endpoints)if(e.getIdentifier().equals(returnEndpoint.getIdentifier()))return e;}
  for(int type:new int[]{CallEndpoint.TYPE_WIRED_HEADSET,CallEndpoint.TYPE_EARPIECE,CallEndpoint.TYPE_BLUETOOTH})
   for(CallEndpoint e:endpoints)if(e.getEndpointType()==type)return e;
  return null;
 }
 private int legacyReturnRoute() {
  CallAudioState a=audioState();
  if(a!=null && (a.getSupportedRouteMask()&returnRoute)!=0)return returnRoute;
  return CallAudioState.ROUTE_WIRED_OR_EARPIECE;
 }
 public void onBringToForeground(boolean keypad){startActivity(new Intent(this,MobileScreen.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
 Call current(){for(Call c:calls)if(c.getState()==Call.STATE_RINGING)return c;for(Call c:calls)if(c.getState()!=Call.STATE_HOLDING&&c.getState()!=Call.STATE_DISCONNECTED)return c;return calls.isEmpty()?null:calls.get(0);}
 static String number(Call c){return c.getDetails().getHandlePresentation()!=TelecomManager.PRESENTATION_ALLOWED||c.getDetails().getHandle()==null?"Номер скрыт":c.getDetails().getHandle().getSchemeSpecificPart();}
 private void updateProximity(CallAudioState audio){
  boolean earpiece=audio!=null && audio.getRoute()==CallAudioState.ROUTE_EARPIECE;
  if(android.os.Build.VERSION.SDK_INT>=34){
   CallEndpoint endpoint=getCurrentCallEndpoint();
   if(endpoint!=null)earpiece=endpoint.getEndpointType()==CallEndpoint.TYPE_EARPIECE;
  }
  applyProximity(earpiece);
 }
 private void applyProximity(boolean earpiece){
  boolean talking=false;
  for(Call call:calls){
   int state=call.getState();
   if(state==Call.STATE_ACTIVE || state==Call.STATE_DIALING || state==Call.STATE_CONNECTING){talking=true;break;}
  }
  ((Phone)getApplication()).proximity.cellular(talking && earpiece);
 }
 void render(){
  reconcileSpeakerRequest();
  updateProximity(audioState());
  NotificationManager m=getSystemService(NotificationManager.class);Call c=current();
  if(c!=null&&c.getState()==Call.STATE_RINGING){
   m.cancel(ONGOING);
   Call.Details d=c.getDetails();
   String name=null;
   if(d.getHandlePresentation()==TelecomManager.PRESENTATION_ALLOWED){
    if(android.os.Build.VERSION.SDK_INT>=30)name=d.getContactDisplayName();
    if(name==null&&d.getCallerDisplayNamePresentation()==TelecomManager.PRESENTATION_ALLOWED)name=d.getCallerDisplayName();
   }
   ringNotice.show(c,number(c),name);
  }else{
   ringNotice.clear();
   if(c==null||c.getState()==Call.STATE_DISCONNECTED)m.cancel(ONGOING);
   else {m.createNotificationChannel(new NotificationChannel("simcall","Разговор по SIM",NotificationManager.IMPORTANCE_LOW));m.notify(ONGOING,new Notification.Builder(this,"simcall").setSmallIcon(android.R.drawable.sym_action_call).setContentTitle(Directory.name(this,number(c))).setContentText("Вызов по SIM").setContentIntent(CallNotice.open(this,MobileScreen.class,ONGOING)).setOngoing(true).build());}
  }
  if(changed!=null)changed.run();
 }
 public void onDestroy(){clearSpeakerRequest();((Phone)getApplication()).proximity.cellular(false);ringNotice.clear();getSystemService(NotificationManager.class).cancel(ONGOING);live=null;if(changed!=null)changed.run();super.onDestroy();}
}
