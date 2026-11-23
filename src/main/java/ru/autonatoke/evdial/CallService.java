package ru.autonatoke.evdial;
import android.app.*;
import android.content.Intent;
import android.os.IBinder;
public class CallService extends Service {
 public int onStartCommand(Intent intent,int flags,int id){
  NotificationManager manager=getSystemService(NotificationManager.class);
  manager.createNotificationChannel(new NotificationChannel("call","Активный разговор",NotificationManager.IMPORTANCE_LOW));
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,Dialer.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE);
  startForeground(7,new Notification.Builder(this,"call").setSmallIcon(android.R.drawable.sym_action_call).setContentTitle("Городская линия EVDial").setContentText(((Phone)getApplication()).active!=null?"Открыть вызов":"Приём входящих SIP включён").setContentIntent(open).setOngoing(true).build());
  Phone phone=(Phone)getApplication();if(phone.sipCore().getCallsNb()==0&&!phone.listening())stopSelf();
  return phone.listening()?START_STICKY:START_NOT_STICKY;
 }
 public IBinder onBind(Intent i){return null;}
}
