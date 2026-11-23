package ru.autonatoke.evdial;

import android.app.Activity;
import android.content.Context;

/** Bundled artwork for this phone application. Selection never changes system wallpaper. */
public final class PhoneThemes {
    private static final String PREFS="drive", KEY="phone_wallpaper";

    public static final class Theme {
        public final String id, name, description, drawable;
        private Theme(String id,String name,String description,String drawable){
            this.id=id;this.name=name;this.description=description;this.drawable=drawable;
        }
    }

    private static final Theme[] THEMES={
        new Theme("station","Ночная зарядка","Фотографический EV-пейзаж","wp_station"),
        new Theme("neon","Электрический импульс","Зелёный неон и голубые разряды","wp_neon"),
        new Theme("world","Энергия мира","Инфраструктура и электрические маршруты","wp_world"),
        new Theme("motion","Скорость света","Электроседан в световом тоннеле","wp_motion"),
        new Theme("impulse","Импульс","Зелёный свет и глубокий графит","wp_impulse"),
        new Theme("ice","Ледяной ток","Голубой акцент и холодный свет","wp_ice"),
        new Theme("night","Ночной город","Огни города на тёмном фоне","wp_night"),
        new Theme("energy","Энергия","Медные обмотки и бирюзовый свет","wp_energy"),
        new Theme("titan","Титан","Металл и спокойные оттенки","wp_titan"),
        new Theme("aurora","Аврора","Электрическое сияние и бирюзовые контуры","wp_aurora"),
        new Theme("carbon","Карбон","Тёмный карбон и холодный металл","wp_carbon"),
        new Theme("circuit","Силовой контур","Зелёные дуги и электрические линии","wp_circuit")
    };

    private PhoneThemes(){}

    public static Theme[] values(){return THEMES.clone();}

    public static Theme find(String id){
        for(Theme theme:THEMES)if(theme.id.equals(id))return theme;
        return null;
    }

    public static String selectedId(Context context){
        String profile=UiProfiles.id(context);
        String key=UiProfiles.EV.equals(profile)?KEY:KEY+"_"+profile;
        String def=UiProfiles.RUBINA.equals(profile)?"carbon":UiProfiles.LIGHT.equals(profile)?"ice":UiProfiles.PEARL.equals(profile)?"aurora":"neon";
        String id=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(key,def);
        return find(id)==null?"":id;
    }

    public static boolean hasSelected(Context context){return !selectedId(context).isEmpty();}

    public static String selectedName(Context context){
        if(WallpaperStore.enabled(context))return "Моё фото";
        Theme theme=find(selectedId(context));return theme==null?"Стандартный":theme.name;
    }

    public static int selectedResource(Context context){return resource(context,find(selectedId(context)));}

    public static int resource(Context context,Theme theme){
        if(theme==null)return R.drawable.flux;
        int image=context.getResources().getIdentifier(theme.drawable,"drawable",context.getPackageName());
        return image==0?R.drawable.flux:image;
    }

    public static void select(Activity activity,String id){
        if(find(id)!=null){
            String profile=UiProfiles.id(activity);String key=UiProfiles.EV.equals(profile)?KEY:KEY+"_"+profile;
            activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(key,id).putBoolean("custom_wallpaper_"+profile,false).apply();
        }
    }
}
