package ru.autonatoke.evdial;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** Visual profiles share a single dialer, account, contacts, permissions and call state. */
final class UiProfiles {
    static final String EV = "ev", AKOP = "akop", LIGHT = "light", PEARL = "pearl", MINIMAL = "minimal";
    private static final String KEY = "ui_profile";
    static final String BUTTON = "button_opacity", PANEL = "panel_opacity", KEYPAD = "keypad_opacity",
            NAV = "navigation_opacity", ACCENT = "accent_strength", CORNERS = "corner_radius", SCALE = "font_percent";
    static final class Preset {
        final String id, label, description;
        final int base, panel, accent, secondary, ink, muted, wallpaper;
        Preset(String id, String label, String description,
               int base, int panel, int accent, int secondary, int ink, int muted, int wallpaper){
            this.id=id;this.label=label;this.description=description;
            this.base=base;this.panel=panel;this.accent=accent;this.secondary=secondary;
            this.ink=ink;this.muted=muted;this.wallpaper=wallpaper;
        }
    }
    private static final Preset[] PRESETS = {
        new Preset(EV, "Электромобили", "Графит · неоновый зелёный · голубой",
                0xff0c151a,0xff1d3540,0xff79f4a6,0xff6eddfa,0xfff1fbff,0xffb9d6df,R.drawable.wp_neon),
        new Preset(LIGHT, "Светлая", "Чистый белый · зелёный акцент",
                0xfff6f9fb,0xffe5edf0,0xff137c58,0xff2c8299,0xff17242a,0xff526670,R.drawable.wp_ice),
        new Preset(PEARL, "Жемчуг", "Тёплый белый · мягкое золото",
                0xfffffaf5,0xfff1e9e4,0xffa76a51,0xffb38a72,0xff302522,0xff71615d,R.drawable.wp_aurora),
        new Preset(MINIMAL, "Минимализм", "Чёрный · белый · минимум отвлекающего",
                0xff101418,0xff22282c,0xffd8f6ed,0xff99b5b9,0xfff7fafb,0xffa8b4b8,R.drawable.wp_carbon)
    };
    private UiProfiles() {}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("drive",Context.MODE_PRIVATE);}
    static Preset[] values(){return PRESETS.clone();}
    static Preset find(String id){for(Preset p:PRESETS)if(p.id.equals(id))return p;return PRESETS[0];}
    static Preset active(Context c){
        SharedPreferences preferences=prefs(c);
        String saved=preferences.getString(KEY,EV);
        Preset current=find(saved);
        // Removed or unknown profiles fall back to the existing EV preferences.
        if(!current.id.equals(saved))preferences.edit().putString(KEY,current.id).apply();
        return current;
    }
    static String id(Context c){return active(c).id;}
    static void select(Context c,String id){prefs(c).edit().putString(KEY,find(id).id).apply();}
    static int value(Context c,String key){
        int defaultValue = BUTTON.equals(key)?88:PANEL.equals(key)?82:KEYPAD.equals(key)?90:
                NAV.equals(key)?92:ACCENT.equals(key)?100:CORNERS.equals(key)?20:SCALE.equals(key)?100:100;
        return prefs(c).getInt("skin_"+id(c)+"_"+key,defaultValue);
    }
    static void set(Context c,String key,int value){prefs(c).edit().putInt("skin_"+id(c)+"_"+key,value).apply();}
    static String signature(Context c){
        StringBuilder result=new StringBuilder(id(c));
        for(String k:new String[]{BUTTON,PANEL,KEYPAD,NAV,ACCENT,CORNERS,SCALE})result.append(':').append(value(c,k));
        result.append(':').append(PhoneThemes.selectedId(c));
        result.append(':').append(prefs(c).getBoolean("custom_wallpaper_"+id(c),false));
        result.append(':').append(prefs(c).getInt("contrast_"+id(c),2));
        return result.toString();
    }
    static void reset(Context c){
        SharedPreferences.Editor editor=prefs(c).edit();
        for(String k:new String[]{BUTTON,PANEL,KEYPAD,NAV,ACCENT,CORNERS,SCALE})editor.remove("skin_"+id(c)+"_"+k);
        editor.apply();
    }
    static int mix(int a,int b,float amount){
        float t=Math.max(0,Math.min(1,amount));
        return Color.rgb(Math.round(Color.red(a)*(1-t)+Color.red(b)*t),
                Math.round(Color.green(a)*(1-t)+Color.green(b)*t),
                Math.round(Color.blue(a)*(1-t)+Color.blue(b)*t));
    }
    static int opacity(int color,int percent){return (Math.round(Math.max(0,Math.min(100,percent))*255f/100f)<<24)|(color&0xffffff);}
    static int accent(Context c){return mix(active(c).panel,active(c).accent,value(c,ACCENT)/100f);}
    static int secondary(Context c){return active(c).secondary;}
    static int text(Context c){return active(c).ink;}
    static int muted(Context c){return active(c).muted;}
    static int button(Context c){return opacity(active(c).panel,value(c,BUTTON));}
    static int panel(Context c){return opacity(active(c).panel,value(c,PANEL));}
    static int keypad(Context c){return opacity(active(c).panel,value(c,KEYPAD));}
    static int navigation(Context c){return opacity(active(c).base,value(c,NAV));}
    static int corner(Context c,int old){return Math.max(4,Math.round(old*value(c,CORNERS)/20f));}
    static int type(Context c,int sp){return Math.max(10,Math.round(sp*value(c,SCALE)/100f));}
}
