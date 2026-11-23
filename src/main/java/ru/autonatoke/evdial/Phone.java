package ru.autonatoke.evdial;
import android.app.Application;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.linphone.core.*;

public class Phone extends Application {
 private Core core;
 CallProximity proximity;
 CallNotice ringNotice;
 Account account;
 Call active;
 Runnable observer;
 String host, registration="Линия не подключена", phase="";
 private SharedPreferences prefs;
 public void onCreate(){
  super.onCreate();
  ScreenOrientation.register(this);
  ringNotice=new CallNotice(this,SipCallScreen.class,CallNotice.SIP);
  proximity=new CallProximity(this); prefs=getSharedPreferences("line",MODE_PRIVATE);
  if(listening() || !value("user").isEmpty()) {
   sipCore();
   if(!value("user").isEmpty())try{connect(value("user"),value("host"),secret(),value("transport"));}catch(Exception e){registration="Введите параметры линии повторно";}
  }
 }
 // A cold SIM InCallService must be able to ring before loading the native SIP engine.
 synchronized Core sipCore(){
  if(core!=null)return core;
  Factory f=Factory.instance();
  core=f.createCoreWithConfig(f.createConfigFromString("[sip]\nstore_auth_info=0\n"),this);
  core.setMaxCalls(1); core.setPushNotificationEnabled(false);
  core.setVideoCaptureEnabled(false); core.setVideoDisplayEnabled(false);
  core.addListener(new CoreListenerStub(){
   public void onAudioDeviceChanged(Core c,AudioDevice device){updateSipProximity();}
   public void onAudioDevicesListUpdated(Core c){updateSipProximity();}
   public void onCallLogUpdated(Core c,CallLog log){SipHistory.record(Phone.this,log);}
   public void onAccountRegistrationStateChanged(Core c,Account a,RegistrationState s,String message){
    if(!a.equals(account)) return;
    registration=s==RegistrationState.Ok?"Линия подключена":s==RegistrationState.Progress?"Подключаемся…":s==RegistrationState.Failed?"Ошибка регистрации · "+message:"Линия отключена";
    refresh();
   }
   public void onCallStateChanged(Core c,Call call,Call.State state,String message){
    if(call.getDir()==Call.Dir.Incoming && incomingRinging(state)){
     if(!listening()){call.decline(Reason.Busy);return;}
     active=call;phase="Входящий · "+caller();
     ringNotice.show(call,caller(),null);refresh();return;
    }
    // Linphone may release the previous call after a new incoming call starts.
    if(active!=null && active!=call &&
       (state==Call.State.End||state==Call.State.Error||state==Call.State.Released))return;
    if(call.getDir()==Call.Dir.Incoming && active==null)return;
    ringNotice.dismiss();
    switch(state){
     case End: case Error: case Released:
      ringNotice.clear();
      active=null; if(state!=Call.State.Released)phase=state==Call.State.Error?"Ошибка вызова · "+message:"Вызов завершён";
      updateService();break;
     default: active=call;
      switch(state){case StreamsRunning:case Connected:phase="Идёт разговор";break;case OutgoingRinging:case OutgoingEarlyMedia:phase="Идут гудки";break;default:phase="Соединение…";}
    } refresh();
   }
  });
  core.start();
  if(Build.VERSION.SDK_INT>=31){
   AudioManager audio=getSystemService(AudioManager.class);
   if(audio!=null)audio.addOnCommunicationDeviceChangedListener(getMainExecutor(),device->updateSipProximity());
  }
  return core;
 }
 boolean listening(){return prefs.getBoolean("incoming",false);}
 private static boolean incomingRinging(Call.State state){return state==Call.State.IncomingReceived||state==Call.State.IncomingEarlyMedia;}
 boolean ringing(){return active!=null&&incomingRinging(active.getState());}
 String caller(){String n=active==null?null:active.getRemoteAddress().getUsername();return n==null?"Неизвестный номер":n;}
 void setListening(boolean on){if(on)sipCore();prefs.edit().putBoolean("incoming",on).apply();updateService();refresh();}
 void updateService(){Intent i=new Intent(this,CallService.class);if(listening()||active!=null)startForegroundService(i);else stopService(i);}
 void updateSipProximity(){
  Call call=active;
  boolean atEar=false;
  if(call!=null){
   switch(call.getState()){
    case OutgoingInit: case OutgoingProgress: case OutgoingRinging: case OutgoingEarlyMedia:
    case Connected: case StreamsRunning: case Updating: case UpdatedByRemote: case Resuming:
     AudioDevice out=call.getOutputAudioDevice();
     if(out==null)out=core.getOutputAudioDevice();
     if(out!=null && out.getType()!=AudioDevice.Type.Unknown){
      atEar=out.getType()==AudioDevice.Type.Earpiece;
     }else if(Build.VERSION.SDK_INT>=31){
      AudioManager audio=getSystemService(AudioManager.class);
      AudioDeviceInfo route=audio==null?null:audio.getCommunicationDevice();
      atEar=route!=null && route.getType()==AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
     }
     break;
    default: break;
   }
  }
  proximity.sip(atEar);
 }
 void refresh(){updateSipProximity();if(observer!=null)observer.run();}
 String value(String key){return prefs.getString(key,"");}
 private SecretKey key() throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias("evdial-sip")){
   KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");
   gen.init(new KeyGenParameterSpec.Builder("evdial-sip",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();
  }return (SecretKey)store.getKey("evdial-sip",null);
 }
 String secret() throws Exception{
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(value("iv"),0)));return new String(c.doFinal(Base64.decode(value("secret"),0)),"UTF-8");
 }
 void save(String user,String domain,String pass,String transport) throws Exception{
  if(pass.isEmpty()&&!value("secret").isEmpty())pass=secret();
  if(pass.isEmpty())throw new IllegalArgumentException("Введите SIP-пароль");
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
  String enc=Base64.encodeToString(c.doFinal(pass.getBytes("UTF-8")),2);
  connect(user,domain,pass,transport);
  prefs.edit().putString("user",user).putString("host",domain).putString("transport",transport).putString("secret",enc).putString("iv",Base64.encodeToString(c.getIV(),2)).apply();
 }
 private void connect(String user,String domain,String pass,String tr){
  if(!user.matches("[a-zA-Z0-9_.+%-]+")||!domain.matches("[a-zA-Z0-9.-]+(:[0-9]{1,5})?"))throw new IllegalArgumentException("Проверьте SIP-логин и адрес сервера");
  sipCore();
  Factory f=Factory.instance();Address server=f.createAddress("sip:"+domain),id=f.createAddress("sip:"+user+"@"+domain);
  if(server==null||id==null)throw new IllegalArgumentException("Некорректный адрес SIP");
  TransportType t="TLS".equals(tr)?TransportType.Tls:"TCP".equals(tr)?TransportType.Tcp:TransportType.Udp;
  server.setTransport(t);
  AccountParams p=core.createAccountParams();p.setIdentityAddress(id);p.setServerAddress(server);p.setRegisterEnabled(true);
  core.clearAccounts();core.clearAllAuthInfo();core.addAuthInfo(f.createAuthInfo(user,null,pass,null,null,id.getDomain()));
  host=domain;registration="Подключаемся…";phase="";account=core.createAccount(p);core.addAccount(account);core.setDefaultAccount(account);refresh();
 }
 boolean ready(){return account!=null&&account.getState()==RegistrationState.Ok;}
 void dial(String number){
  if(!ready()||active!=null)return;
  core.setMicEnabled(true); Address to=Factory.instance().createAddress("sip:"+number+"@"+host);
  if(to==null)throw new IllegalArgumentException("Проверьте номер");
  startForegroundService(new Intent(this,CallService.class));
  Call call=core.inviteAddress(to);if(call==null){stopService(new Intent(this,CallService.class));throw new IllegalStateException("Не удалось начать вызов");}
 }
}
