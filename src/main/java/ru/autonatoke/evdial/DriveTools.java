package ru.autonatoke.evdial;
import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
final class DriveTools {
 static SharedPreferences prefs(Activity a){return a.getSharedPreferences("drive",Context.MODE_PRIVATE);}
 static void background(Activity a,View v){Appearance.applyBackground(a,v);}
 static String tr(Activity a,String ru,String en){return "ru".equals(a.getResources().getConfiguration().locale.getLanguage())?ru:en;}
 static void assistance(Activity a){
  LinearLayout content=new LinearLayout(a);content.setOrientation(android.widget.LinearLayout.VERTICAL);
  StationSheet dialog=new StationSheet.Builder(a).setTitle(tr(a,"EV Помощь","EV Help")).setView(content).setNegativeButton(tr(a,"Закрыть","Close"),null).create();
  section(a,content,tr(a,"Водителю электромобиля","For EV drivers"));
  row(a,content,tr(a,"Зарядные станции","Charging stations"),R.drawable.driver_station_map,()->{dialog.dismiss();Charging.open(a);});
  row(a,content,tr(a,"Если автомобиль не едет","Vehicle won't drive"),R.drawable.driver_help,()->{dialog.dismiss();tips(a,false);});
  row(a,content,tr(a,"Проблема с зарядкой","Charging problems"),R.drawable.driver_charge,()->{dialog.dismiss();tips(a,true);});
  section(a,content,tr(a,"Диагностика","Diagnostics"));
  row(a,content,"DIAGNOST AI ↗",R.drawable.driver_ai,()->{dialog.dismiss();EvLinks.diagnost(a);});
  section(a,content,tr(a,"Проект","Project"));
  row(a,content,tr(a,"О приложении","About"),R.drawable.driver_car,()->{dialog.dismiss();about(a);});
  dialog.show();
 }
 private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
 private static void section(Activity a,LinearLayout root,String txt){TextView t=new TextView(a);t.setText(txt);t.setTextSize(14);t.setTextColor(0xffb7d5d7);t.setPadding(dp(a,10),dp(a,10),0,dp(a,6));root.addView(t,-1,-2);}
 private static void row(Activity a,LinearLayout root,String title,int res,Runnable action){
  TextView t=new TextView(a);t.setText(title);t.setTextColor(0xffe5fbfa);t.setTextSize(17);t.setTypeface(Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));t.setGravity(Gravity.CENTER_VERTICAL);t.setMinHeight(dp(a,54));t.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8));
  android.graphics.drawable.Drawable ico=a.getDrawable(res).mutate();ico.setTint(0xff85e5de);ico.setBounds(0,0,dp(a,24),dp(a,24));t.setCompoundDrawablesRelative(ico,null,null,null);t.setCompoundDrawablePadding(dp(a,12));
  GradientDrawable bg=new GradientDrawable();bg.setColor(0xff172928);bg.setCornerRadius(dp(a,14));t.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x4475e3c1),bg,null));t.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(a,5);root.addView(t,p);
 }
 private static void tips(Activity a,boolean charge){
  String title=charge?tr(a,"Зарядка не начинается","Charging problems"):tr(a,"Автомобиль не едет","Vehicle won't drive");
  String message=charge?tr(a,"При перегреве разъёма или запахе гари не пытайтесь заряжать. Проверьте фиксацию кабеля и статус станции. Если безопасно, попробуйте другую станцию. Не вскрывайте высоковольтные узлы.","Stop if the connector overheats or smells burnt. Check the cable lock and station status. Try another station if safe. Never open high-voltage components.")
  :tr(a,"Остановитесь в безопасном месте и проверьте сообщения на приборной панели. Не прикасайтесь к оранжевым проводам и повреждённой батарее. При дыме или угрозе пожара отойдите и вызовите экстренные службы.","Stop safely and read dashboard warnings. Do not touch orange HV wires or a damaged battery. If there is smoke or a fire risk, move away and contact emergency services.");
  new StationSheet.Builder(a).setTitle(title).setMessage(message).setPositiveButton("DIAGNOST AI ↗",(d,i)->Station.ai(a)).setNegativeButton(tr(a,"Назад","Back"),(d,i)->assistance(a)).show();
 }
 private static void about(Activity a){
  new StationSheet.Builder(a).setTitle("EVDial · "+AppUpdates.version(a)).setMessage(tr(a,
   "Бесплатная EV-звонилка. SIM, SIP, контакты, EV-помощь и зарядные станции. Справочник — офлайн-выборка России от 22.04.2026, без информации о текущей занятости. DIAGNOST AI — внешний сервис. Разработчик: Autonatoke. Нет рекламного SDK.",
   "Free EV phone: SIM, SIP, contacts, roadside help and charging directory. The directory is an offline Russian subset dated 2026-04-22, with no live availability. DIAGNOST AI is external. Developer: Autonatoke. No ad SDK."
  )).setPositiveButton(tr(a,"Проверить обновления","Check for updates"),(d,w)->AppUpdates.open(a)).setNegativeButton("OK",null).show();
 }
}