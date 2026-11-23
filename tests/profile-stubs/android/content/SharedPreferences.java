package android.content;
public interface SharedPreferences {
 String getString(String name,String defaultValue);
 int getInt(String name,int defaultValue);
 boolean getBoolean(String name,boolean defaultValue);
 Editor edit();
 interface Editor {Editor putString(String key,String value);Editor putInt(String key,int value);Editor putBoolean(String key,boolean value);Editor remove(String key);void apply();}
}
