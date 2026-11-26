package ru.autonatoke.evdial;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps optional SIP reception alive; microphone access is tied to a call. */
public class CallService extends Service {
 private Phone phone;
 private boolean refreshing,refreshAgain,stopping,promoted;
 private int promotedType;

 @Override public void onCreate(){
  super.onCreate();phone=(Phone)getApplication();phone.serviceCreated(this);
 }

 @Override public int onStartCommand(Intent intent,int flags,int id){
  stopping=false;phone.serviceCreated(this);
  if(!refreshForeground())return START_NOT_STICKY;
  // A system restart resumes reception only, never a previously active call.
  return phone.listening()?START_STICKY:START_NOT_STICKY;
 }

 boolean refreshForeground(){
  if(stopping)return false;
  if(refreshing){refreshAgain=true;return true;}
  refreshing=true;
  try{
   do{
    refreshAgain=false;
    if(!phone.serviceNeeded()){stopCallService();return false;}
    // This second check closes a revoke race after startForegroundService().
    if(!phone.microphoneGranted()){phone.pauseSipService(Phone.MICROPHONE_REQUIRED);return false;}
    boolean audio=phone.microphoneServiceNeeded();
    NotificationManager manager=getSystemService(NotificationManager.class);
    manager.createNotificationChannel(new NotificationChannel("call","Городская линия SIP",NotificationManager.IMPORTANCE_LOW));
    PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,Dialer.class)
      .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    String status=audio?"Вызов через SIP · открыть":phone.ringing()?"Входящий SIP · открыть вызов":
      BuildConfig.GOOGLE_PLAY_BUILD?"Приём входящих SIP включён · микрофон не используется":"Приём входящих SIP включён";
    Notification notice=new Notification.Builder(this,"call").setSmallIcon(android.R.drawable.sym_action_call)
      .setContentTitle("Городская линия EVDial").setContentText(status).setContentIntent(open).setOngoing(true).build();
    int type=ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST;
    if(BuildConfig.GOOGLE_PLAY_BUILD&&Build.VERSION.SDK_INT>=30){
     // specialUse describes persistent user-enabled registration/ringing, not
     // audio capture. Its declaration and justification are reviewed by Play.
     type=audio?ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE:
       Build.VERSION.SDK_INT>=34?ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE:0;
    }
    try{
     if(!promoted||promotedType!=type){promote(notice,type);promoted=true;promotedType=type;}
     else manager.notify(7,notice);
    }
    catch(SecurityException|IllegalStateException restricted){
     // Permission checks alone cannot establish while-in-use eligibility.
     // If Android denies promotion, no pending native call is started.
     phone.pauseSipService(Phone.SERVICE_RESTRICTED);return false;
    }
    phone.runPendingAudioStart();
   }while(refreshAgain&&!stopping);
   return !stopping;
  }finally{refreshing=false;}
 }

 void promote(Notification notification,int type){
  if(Build.VERSION.SDK_INT>=29)startForeground(7,notification,type);
  else startForeground(7,notification);
 }

 void stopCallService(){
  stopping=true;promoted=false;phone.serviceStopped(this);
  stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
 }

 @Override public void onDestroy(){phone.serviceStopped(this);super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
