package ru.autonatoke.evdial;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.widget.Toast;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabColorSchemeParams;

/** Browser-managed tab: a visible origin, browser security updates, no JavaScript bridge. */
final class EvLinks {
 static void open(Activity a,String address){
  Uri u=Uri.parse(address==null?"":address.trim());String s=u.getScheme();
  if((!"https".equalsIgnoreCase(s)&&!"http".equalsIgnoreCase(s))||u.getHost()==null||u.getHost().isEmpty()||u.getUserInfo()!=null){
   Toast.makeText(a,ElectricUi.tr(a,"Нужен адрес сайта http или https","Enter an http or https website"),Toast.LENGTH_LONG).show();return;
  }
  try{
   CustomTabsIntent tab=new CustomTabsIntent.Builder().setShowTitle(true)
    .setDefaultColorSchemeParams(new CustomTabColorSchemeParams.Builder().setToolbarColor(UiProfiles.active(a).panel).setNavigationBarColor(UiProfiles.active(a).base).build())
    .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK).setShareState(CustomTabsIntent.SHARE_STATE_OFF).build();
   tab.launchUrl(a,u);
  }catch(ActivityNotFoundException|SecurityException e){
   try{a.startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(ActivityNotFoundException|SecurityException noBrowser){Toast.makeText(a,ElectricUi.tr(a,"Установите браузер для открытия сайта","Install a browser to open websites"),Toast.LENGTH_LONG).show();}
  }
 }
 static void diagnost(Activity a){
  android.content.SharedPreferences p=a.getSharedPreferences("drive",0);
  if(p.getBoolean("diagnost_external_ack",false)){open(a,"https://autonatoke.ru/diagnost");return;}
  new StationSheet.Builder(a).setTitle("DIAGNOST AI ↗")
   .setMessage(ElectricUi.tr(a,"Откроется внешний сервис диагностики. Контакты, номер из набора и журнал звонков автоматически не передаются. Условия сервиса могут отличаться от бесплатной звонилки.","This opens an external diagnostic service. Contacts, dialed numbers and call history are not automatically shared. Service terms may differ from this free dialer."))
   .setPositiveButton(ElectricUi.tr(a,"Открыть","Open"),(d,w)->{p.edit().putBoolean("diagnost_external_ack",true).apply();open(a,"https://autonatoke.ru/diagnost");})
   .setNegativeButton(ElectricUi.tr(a,"Отмена","Cancel"),null).show();
 }
}
