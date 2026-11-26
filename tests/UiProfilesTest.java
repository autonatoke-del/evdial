package ru.autonatoke.evdial;
import android.content.*;
import java.util.*;
public final class UiProfilesTest {
 private static int assertions;
 private static void check(boolean pass,String message){assertions++;if(!pass)throw new AssertionError(message);}
 static final class MemoryPrefs implements SharedPreferences, SharedPreferences.Editor {
  final Map<String,Object> values=new HashMap<>();
  public String getString(String key,String def){return (String)values.getOrDefault(key,def);}
  public int getInt(String key,int def){return (int)values.getOrDefault(key,def);}
  public boolean getBoolean(String key,boolean def){return (boolean)values.getOrDefault(key,def);}
  public Editor edit(){return this;}
  public Editor putString(String k,String v){values.put(k,v);return this;}
  public Editor putInt(String k,int v){values.put(k,v);return this;}
  public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}
  public Editor remove(String k){values.remove(k);return this;}
  public void apply(){}
 }
 static final class Device extends Context {
  final MemoryPrefs data=new MemoryPrefs();
  public SharedPreferences getSharedPreferences(String n,int m){return data;}
 }
 public static void main(String[] args){
  Device phone=new Device();
  check(UiProfiles.values().length==4,"Four current visual profiles");
  check(UiProfiles.LIGHT.equals(UiProfiles.find(UiProfiles.LIGHT).id),"Light profile exists");
  check(UiProfiles.PEARL.equals(UiProfiles.find(UiProfiles.PEARL).id),"Pearl profile exists");
  check(UiProfiles.MINIMAL.equals(UiProfiles.find(UiProfiles.MINIMAL).id),"Minimal profile exists");
  check(UiProfiles.EV.equals(UiProfiles.id(phone)),"Legacy default is EV");
  check(UiProfiles.value(phone,UiProfiles.BUTTON)==88,"Default button opacity");
  UiProfiles.set(phone,UiProfiles.BUTTON,0);
  check(UiProfiles.button(phone)>>>24==0,"Fully transparent buttons");
  UiProfiles.set(phone,UiProfiles.PANEL,0);
  check(UiProfiles.panel(phone)>>>24==0,"Fully transparent panels");
  UiProfiles.set(phone,UiProfiles.KEYPAD,0);
  check(UiProfiles.keypad(phone)>>>24==0,"Fully transparent dialpad");
  UiProfiles.select(phone,UiProfiles.PEARL);
  check("Жемчуг".equals(UiProfiles.active(phone).label),"Pearl theme label");
  check(UiProfiles.value(phone,UiProfiles.BUTTON)==88,"New profile owns separate default");
  UiProfiles.set(phone,UiProfiles.BUTTON,73);
  UiProfiles.select(phone,UiProfiles.LIGHT);
  check("Светлая".equals(UiProfiles.active(phone).label),"Light theme label");
  check(UiProfiles.value(phone,UiProfiles.BUTTON)==88,"Light has independent settings");
  UiProfiles.set(phone,UiProfiles.BUTTON,47);
  UiProfiles.select(phone,UiProfiles.PEARL);
  check(UiProfiles.value(phone,UiProfiles.BUTTON)==73,"Pearl saved settings restored");
  UiProfiles.select(phone,UiProfiles.MINIMAL);
  check("Минимализм".equals(UiProfiles.active(phone).label),"Minimal visual mode");
  check(UiProfiles.value(phone,UiProfiles.BUTTON)==88,"Minimal has independent controls");
  UiProfiles.select(phone,UiProfiles.AKOP);
  check(UiProfiles.EV.equals(UiProfiles.id(phone)),"Removed Akop maps to EV");
  check(UiProfiles.value(phone,UiProfiles.BUTTON)==0,"Legacy Akop does not invent another theme");
  phone.data.putString("ui_profile",UiProfiles.AKOP);
  check(UiProfiles.EV.equals(UiProfiles.id(phone)),"Persisted legacy Akop value migrates to EV");
  check(UiProfiles.EV.equals(phone.data.getString("ui_profile","")),"Legacy value normalized in preferences");
  phone.data.putInt("skin_rubina_button_opacity",73).putString("ui_profile","rubina");
  check(UiProfiles.EV.equals(UiProfiles.id(phone)),"Removed persisted theme migrates to EV");
  check(UiProfiles.EV.equals(phone.data.getString("ui_profile","")),"Removed theme migration is persisted");
  check(UiProfiles.value(phone,UiProfiles.BUTTON)==0,"Migration preserves the existing EV settings");
  check(UiProfiles.active(phone).wallpaper==R.drawable.wp_neon,"Migration uses the EV profile artwork");
  UiProfiles.select(phone,"rubina");
  check(UiProfiles.EV.equals(UiProfiles.id(phone)),"A removed theme cannot be reselected");
  phone.data.putString("ui_profile","unknown-profile");
  check(UiProfiles.EV.equals(UiProfiles.id(phone)),"Unknown profile falls back safely");
  check(UiProfiles.EV.equals(phone.data.getString("ui_profile","")),"Unknown profile is normalized");
  UiProfiles.select(phone,UiProfiles.PEARL);
  String before=UiProfiles.signature(phone);
  UiProfiles.set(phone,UiProfiles.NAV,0);
  check(!before.equals(UiProfiles.signature(phone)),"UI signature detects changes");
  UiProfiles.reset(phone);
  check(UiProfiles.value(phone,UiProfiles.BUTTON)==88,"Reset only current profile");
  UiProfiles.select(phone,UiProfiles.LIGHT);
  check(UiProfiles.value(phone,UiProfiles.BUTTON)==47,"Light settings survive across four themes");
  UiProfiles.select(phone,UiProfiles.EV);
  check(UiProfiles.value(phone,UiProfiles.BUTTON)==0,"Original profile retained after others reset");
  check(UiProfiles.opacity(0xffabcdef,0)==0x00abcdef,"Zero opacity exactly zero");
  check(UiProfiles.opacity(0xffabcdef,100)==0xffabcdef,"Full opacity exact");
  check(UiProfiles.opacity(0xffabcdef,200)==0xffabcdef,"Alpha clamps");
  System.out.println("PASS: "+assertions+" persisted theme-profile behavior assertions");
 }
}
