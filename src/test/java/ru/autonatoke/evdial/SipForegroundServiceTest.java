package ru.autonatoke.evdial;

import android.Manifest;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises permission races and service transitions without native SIP or a network. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, application=SipForegroundServiceTest.NoNativePhone.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class SipForegroundServiceTest {
 public static class NoNativePhone extends Phone {
  final List<String> actions=new ArrayList<>();
  int startRequests,stopRequests;
  boolean microphoneEnabled;
  RuntimeException startFailure;
  @Override boolean ready(){return true;}
  @Override public ComponentName startForegroundService(Intent intent){
   startRequests++;
   if(startFailure!=null)throw startFailure;
   return new ComponentName(this,CallService.class);
  }
  @Override public boolean stopService(Intent intent){stopRequests++;return true;}
  @Override synchronized Core sipCore(){
   Core fake=(Core)Proxy.newProxyInstance(Core.class.getClassLoader(),new Class<?>[]{Core.class},(proxy,method,args)->{
    if(method.getName().equals("setMicEnabled")){
     microphoneEnabled=(Boolean)args[0];actions.add(microphoneEnabled?"microphone":"microphone-off");return null;
    }
    throw new AssertionError("Unexpected native SIP operation: "+method.getName());
   });
   ReflectionHelpers.setField(this,"core",fake);return fake;
  }
 }

 public static class RecordingService extends CallService {
  int promotions,stops,lastType;
  Notification lastNotice;
  RuntimeException promotionFailure;
  @Override void promote(Notification notice,int type){
   promotions++;lastType=type;lastNotice=notice;
   ((NoNativePhone)getApplication()).actions.add("promote");
   if(promotionFailure!=null)throw promotionFailure;
   // The OS boundary is recorded, never started. Device while-in-use eligibility
   // remains a separate release check; these tests cover application decisions.
  }
  @Override void stopCallService(){stops++;super.stopCallService();}
 }

 private NoNativePhone phone;
 private int accepted,terminated,acceptResult;

 @Before public void setup(){
  phone=(NoNativePhone)RuntimeEnvironment.getApplication();
  phone.getSharedPreferences("line",Phone.MODE_PRIVATE).edit().putBoolean("incoming",true).apply();
  shadowOf(phone).grantPermissions(Manifest.permission.RECORD_AUDIO);
 }

 @Test public void revokedMicrophonePausesPersistedReceptionBeforeRequestingAService(){
  shadowOf(phone).denyPermissions(Manifest.permission.RECORD_AUDIO);
  phone.updateService();
  assertEquals(0,phone.startRequests);
  assertFalse(phone.listening());
  assertEquals(Phone.MICROPHONE_REQUIRED,ShadowToast.getTextOfLatestToast());
  assertTrue(phone.actions.isEmpty());
 }

 @Test public void permissionRevokedAfterStartRequestPreventsPromotionAndAudio(){
  Call target=incoming();
  assertEquals(0,phone.accept(target));
  assertEquals(1,phone.startRequests);
  assertEquals(0,accepted);
  shadowOf(phone).denyPermissions(Manifest.permission.RECORD_AUDIO);
  ServiceController<RecordingService> controller=Robolectric.buildService(RecordingService.class).create();
  try{
   RecordingService service=controller.get();
   assertEquals(Service.START_NOT_STICKY,service.onStartCommand(new Intent(),0,1));
   assertEquals(0,service.promotions);
   assertEquals(0,accepted);
   assertEquals(1,terminated);
   assertFalse(phone.listening());
   assertNull(ReflectionHelpers.getField(phone,"pendingAudioStart"));
  }finally{controller.destroy();}
 }

 @Test public void idleReceptionUsesSpecialUseInPlayAndDoesNotRequestAudio(){
  ServiceController<RecordingService> controller=Robolectric.buildService(RecordingService.class).create();
  try{
   RecordingService service=controller.get();
   assertEquals(Service.START_STICKY,service.onStartCommand(new Intent(),0,1));
   assertEquals(idleType(),service.lastType);
   assertEquals(Arrays.asList("promote"),phone.actions);
   if(BuildConfig.GOOGLE_PLAY_BUILD)assertTrue(service.lastNotice.extras.getString(Notification.EXTRA_TEXT).contains("микрофон не используется"));
  }finally{controller.destroy();}
 }

 @Test public void incomingRingingDoesNotPromoteToMicrophone(){
  incoming();
  ServiceController<RecordingService> controller=Robolectric.buildService(RecordingService.class).create();
  try{
   RecordingService service=controller.get();service.onStartCommand(new Intent(),0,1);
   assertEquals(idleType(),service.lastType);
   assertEquals(0,accepted);
   assertEquals(Arrays.asList("promote"),phone.actions);
  }finally{controller.destroy();}
 }

 @Test public void explicitAnswerWaitsForPromotionBeforeNativeMicrophoneAndAccept(){
  Call target=incoming();
  assertEquals(0,phone.accept(target));
  assertTrue(phone.actions.isEmpty());
  assertEquals(0,accepted);
  ServiceController<RecordingService> controller=Robolectric.buildService(RecordingService.class).create();
  try{
   RecordingService service=controller.get();service.onStartCommand(new Intent(),0,1);
   assertEquals(audioType(),service.lastType);
   assertEquals(Arrays.asList("promote","microphone","accept"),phone.actions);
   assertEquals(1,accepted);
  }finally{controller.destroy();}
 }

 @Test public void platformPromotionFailureNeverAnswersAndLeavesReceptionPaused(){
  assertEquals(0,phone.accept(incoming()));
  ServiceController<RecordingService> controller=Robolectric.buildService(RecordingService.class).create();
  try{
   RecordingService service=controller.get();service.promotionFailure=new SecurityException("while-in-use denied");
   assertEquals(Service.START_NOT_STICKY,service.onStartCommand(new Intent(),0,1));
   assertEquals(0,accepted);
   assertEquals(1,terminated);
   assertFalse(phone.listening());
   assertEquals(Arrays.asList("promote"),phone.actions);
   assertEquals(Phone.SERVICE_RESTRICTED,ShadowToast.getTextOfLatestToast());
  }finally{controller.destroy();}
 }

 @Test public void forbiddenBackgroundStartIsHandledBeforeNativeAnswer(){
  phone.startFailure=new ForegroundServiceStartNotAllowedException("background");
  assertEquals(-1,phone.accept(incoming()));
  assertEquals(1,phone.startRequests);
  assertEquals(0,accepted);
  assertEquals(1,terminated);
  assertFalse(phone.listening());
  assertEquals(Phone.SERVICE_RESTRICTED,ShadowToast.getTextOfLatestToast());
 }

 @Test public void runningServiceRefreshDoesNotRequestAnotherBackgroundStart(){
  ServiceController<RecordingService> controller=Robolectric.buildService(RecordingService.class).create();
  try{
   RecordingService service=controller.get();service.onStartCommand(new Intent(),0,1);
   phone.updateService();phone.updateService();
   assertEquals(0,phone.startRequests);
   assertEquals("Unchanged type only updates its notification",1,service.promotions);
  }finally{controller.destroy();}
 }

 @Test public void endedCallDropsMicrophoneTypeButKeepsEnabledReception(){
  assertEquals(0,phone.accept(incoming()));
  ServiceController<RecordingService> controller=Robolectric.buildService(RecordingService.class).create();
  try{
   RecordingService service=controller.get();service.onStartCommand(new Intent(),0,1);
   assertEquals(audioType(),service.lastType);
   phone.active=null;ReflectionHelpers.setField(phone,"callAudioRequested",false);
   phone.updateService();
   assertTrue(phone.listening());
   assertEquals(idleType(),service.lastType);
   assertEquals(BuildConfig.GOOGLE_PLAY_BUILD?2:1,service.promotions);
  }finally{controller.destroy();}
 }

 @Test public void disablingIdleReceptionStopsServiceAndDoesNotInitializeNativeSip(){
  ServiceController<RecordingService> controller=Robolectric.buildService(RecordingService.class).create();
  try{
   RecordingService service=controller.get();service.onStartCommand(new Intent(),0,1);
   phone.setListening(false);
   assertFalse(phone.listening());
   assertEquals(1,service.stops);
   assertNull(ReflectionHelpers.getField(phone,"callService"));
   assertEquals(Arrays.asList("promote"),phone.actions);
  }finally{controller.destroy();}
 }

 @Test public void staleRestartWithoutReceptionOrCallStopsWithoutNativeInitialization(){
  phone.getSharedPreferences("line",Phone.MODE_PRIVATE).edit().putBoolean("incoming",false).apply();
  ServiceController<RecordingService> controller=Robolectric.buildService(RecordingService.class).create();
  try{
   RecordingService service=controller.get();
   assertEquals(Service.START_NOT_STICKY,service.onStartCommand(null,0,1));
   assertEquals(0,service.promotions);
   assertEquals(1,service.stops);
   assertTrue(phone.actions.isEmpty());
  }finally{controller.destroy();}
 }

 @Test public void oldReleasedCannotCancelTheNextOutgoingActionWaitingForItsService(){
  phone.getSharedPreferences("line",Phone.MODE_PRIVATE).edit().putBoolean("incoming",false).apply();
  Call old=(Call)Proxy.newProxyInstance(Call.class.getClassLoader(),new Class<?>[]{Call.class},(proxy,method,args)->{
   if(method.getName().equals("getDir"))return Call.Dir.Outgoing;
   throw new AssertionError("Unexpected old-call operation: "+method.getName());
  });
  phone.active=old;
  phone.callStateChanged(old,Call.State.End,"");
  assertNull(phone.active);

  int[] newCallStarts={0};
  Runnable next=()->newCallStarts[0]++;
  ReflectionHelpers.setField(phone,"pendingAudioStart",next);
  ReflectionHelpers.setField(phone,"callAudioRequested",true);
  phone.updateService();
  assertEquals(1,phone.startRequests);
  phone.callStateChanged(old,Call.State.Released,"");
  assertSame(next,ReflectionHelpers.getField(phone,"pendingAudioStart"));
  assertTrue(phone.microphoneServiceNeeded());
  assertEquals(0,newCallStarts[0]);

  ServiceController<RecordingService> controller=Robolectric.buildService(RecordingService.class).create();
  try{
   controller.get().onStartCommand(new Intent(),0,1);
   assertEquals(audioType(),controller.get().lastType);
   assertEquals(1,newCallStarts[0]);
  }finally{controller.destroy();}
 }

 @Test public void repeatedOutgoingTapCannotReplaceTheActionWaitingForPromotion(){
  int[] originalStarts={0};
  Runnable original=()->originalStarts[0]++;
  ReflectionHelpers.setField(phone,"pendingAudioStart",original);
  ReflectionHelpers.setField(phone,"callAudioRequested",true);
  phone.updateService();
  phone.dial("15550009999"); // ready() is true; reaching native address creation would fail this test.
  assertSame(original,ReflectionHelpers.getField(phone,"pendingAudioStart"));
  assertEquals(1,phone.startRequests);
  ServiceController<RecordingService> controller=Robolectric.buildService(RecordingService.class).create();
  try{
   controller.get().onStartCommand(new Intent(),0,1);
   assertEquals(1,originalStarts[0]);
  }finally{controller.destroy();}
 }

 @Test public void failedAnswerDisablesNativeMicrophoneAndReturnsToRingingServiceType(){
  acceptResult=-1;
  Call target=incoming();
  assertEquals(0,phone.accept(target));
  ServiceController<RecordingService> controller=Robolectric.buildService(RecordingService.class).create();
  try{
   RecordingService service=controller.get();service.onStartCommand(new Intent(),0,1);
   assertEquals(1,accepted);
   assertTrue(phone.ringing());
   assertFalse(phone.microphoneEnabled);
   assertTrue(phone.actions.contains("microphone-off"));
   assertFalse(phone.microphoneServiceNeeded());
   assertEquals(idleType(),service.lastType);
   assertNull(ReflectionHelpers.getField(phone,"pendingAudioStart"));
  }finally{controller.destroy();}
 }

 private int idleType(){return BuildConfig.GOOGLE_PLAY_BUILD?ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE:ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST;}
 private int audioType(){return BuildConfig.GOOGLE_PLAY_BUILD?ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE:ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST;}
 private Call incoming(){
  final Call.State[] state={Call.State.IncomingReceived};
  Call call=(Call)Proxy.newProxyInstance(Call.class.getClassLoader(),new Class<?>[]{Call.class},(proxy,method,args)->{
   switch(method.getName()){
    case "getState":return state[0];
    case "accept":accepted++;phone.actions.add("accept");if(acceptResult==0)state[0]=Call.State.Connected;return acceptResult;
    case "terminate":terminated++;return 0;
    default:throw new AssertionError("Unexpected native call operation: "+method.getName());
   }
  });
  phone.active=call;return call;
 }
}
