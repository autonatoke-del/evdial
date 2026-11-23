package ru.autonatoke.evdial;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

/** Artwork and contrast inside the phone. System wallpaper is a separate, explicit action. */
public final class PhoneAppearance {
    private static final String[] MODES={"Однотонный","Приглушённый","Выразительный"};
    private static final int WHITE=0xffedf5ef, MUTED=0xffb7cbc6, MINT=0xff9aefb4, BLUE=0xffa1dcf8;
    private PhoneAppearance(){}

    static void show(Activity activity){
        activity.startActivity(new Intent(activity,ThemeStudio.class));
    }

    private static void gallery(Activity activity){
        if(activity.isFinishing()||activity.getFragmentManager().isStateSaved()
                ||activity.getFragmentManager().findFragmentByTag("phone-wallpaper-gallery")!=null)return;
        new ThemePicker().show(activity.getFragmentManager(),"phone-wallpaper-gallery");
    }

    private static void refresh(Activity activity){
        Appearance.refresh(activity);
        if(activity instanceof Dialer)((Dialer)activity).refresh();
    }

    private static void background(Activity activity){
        new StationSheet.Builder(activity).setTitle("Яркость фона звонилки")
                .setSingleChoiceItems(MODES,Appearance.backgroundMode(activity),(dialog,which)->{
                    Appearance.backgroundMode(activity,which);refresh(activity);
                }).setNegativeButton("Закрыть",null).show();
    }

    /** DialogFragment keeps the uncommitted choice and closes its window with the Activity. */
    public static final class ThemePicker extends DialogFragment {
        private String selected;
        private int mode;
        private View hero;
        private TextView title, description, apply;
        private final ArrayList<ThemeTile> tiles=new ArrayList<>();
        private final ArrayList<TextView> contrasts=new ArrayList<>();

        public ThemePicker(){}

        @Override public Dialog onCreateDialog(Bundle state){
            Activity a=getActivity();
            selected=state==null?PhoneThemes.selectedId(a):state.getString("theme","");
            if(PhoneThemes.find(selected)==null)selected=PhoneThemes.values()[0].id;
            int current=Appearance.backgroundMode(a);
            mode=state==null?(!PhoneThemes.hasSelected(a)||current==0?2:current):state.getInt("mode",2);
            mode=Math.max(0,Math.min(2,mode));
            tiles.clear();contrasts.clear();

            Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout panel=vertical(a);panel.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,14));
            panel.setBackground(shape(a,0xff11201c,24));
            LinearLayout bar=new LinearLayout(a);bar.setGravity(Gravity.CENTER_VERTICAL);panel.addView(bar,-1,-2);
            TextView heading=text(a,"Обои в звонилке",24,WHITE,true);
            bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
            TextView close=text(a,"×",28,MUTED,false);close.setGravity(Gravity.CENTER);
            close.setContentDescription("Закрыть без изменений");close.setBackground(ripple(a,0xff22352e,18));
            bar.addView(close,dp(a,48),dp(a,48));close.setOnClickListener(v->dismiss());
            TextView intro=text(a,"Восемь вариантов EVDial. Выберите фон и яркость.",16,MUTED,false);
            intro.setPadding(0,dp(a,7),0,dp(a,13));panel.addView(intro,-1,-2);

            ScrollView scroll=new ScrollView(a);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
            panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout content=vertical(a);content.setPadding(0,0,0,dp(a,12));scroll.addView(content,-1,-2);
            LinearLayout featured=new LinearLayout(a);featured.setGravity(Gravity.CENTER_VERTICAL);
            featured.setPadding(dp(a,10),dp(a,10),dp(a,10),dp(a,10));
            featured.setBackground(shape(a,0xff1a2d29,18));content.addView(featured,-1,-2);
            FrameLayout preview=new FrameLayout(a);preview.setBackground(shape(a,0xff0a1517,13));preview.setClipToOutline(true);
            hero=new View(a);preview.addView(hero,new FrameLayout.LayoutParams(-1,-1));
            featured.addView(preview,dp(a,112),dp(a,210));
            LinearLayout words=vertical(a);words.setPadding(dp(a,14),0,0,0);
            featured.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            TextView overline=text(a,"ВЫБРАННЫЙ ФОН",12,BLUE,true);overline.setLetterSpacing(.08f);words.addView(overline);
            title=text(a,"",23,WHITE,true);title.setPadding(0,dp(a,8),0,dp(a,8));words.addView(title,-1,-2);
            description=text(a,"",16,MUTED,false);words.addView(description,-1,-2);
            TextView scope=text(a,"Набор номера\nКонтакты\nЭкран звонка",15,BLUE,false);
            scope.setPadding(0,dp(a,13),0,0);words.addView(scope,-1,-2);

            TextView light=text(a,"Яркость",18,WHITE,true);light.setPadding(0,dp(a,17),0,dp(a,8));content.addView(light,-1,-2);
            LinearLayout levels=new LinearLayout(a);content.addView(levels,-1,-2);
            String[] shortModes={"Без фона","Мягкий","Яркий"};
            for(int i=0;i<3;i++){
                final int value=i;
                TextView chip=text(a,shortModes[i],16,WHITE,true);chip.setGravity(Gravity.CENTER);
                chip.setMinHeight(dp(a,50));chip.setPadding(dp(a,6),dp(a,8),dp(a,6),dp(a,8));
                chip.setContentDescription(MODES[i]);chip.setFocusable(true);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)lp.leftMargin=dp(a,7);
                levels.addView(chip,lp);contrasts.add(chip);
                chip.setOnClickListener(v->{mode=value;update();});
            }
            TextView choose=text(a,"Коллекция",20,WHITE,true);choose.setPadding(0,dp(a,22),0,dp(a,10));content.addView(choose,-1,-2);
            PhoneThemes.Theme[] themes=PhoneThemes.values();
            int available=Math.min(a.getResources().getDisplayMetrics().widthPixels,dp(a,600))-dp(a,44);
            int tileWidth=Math.max(dp(a,100),available/2);
            int imageHeight=Math.min(dp(a,300),Math.round(tileWidth*1.82f));
            for(int i=0;i<themes.length;i+=2){
                LinearLayout row=new LinearLayout(a);row.setBaselineAligned(false);
                LinearLayout.LayoutParams rowSize=new LinearLayout.LayoutParams(-1,-2);rowSize.bottomMargin=dp(a,12);
                content.addView(row,rowSize);
                for(int j=i;j<Math.min(i+2,themes.length);j++){
                    PhoneThemes.Theme theme=themes[j];
                    ThemeTile tile=new ThemeTile(a,theme,imageHeight);tiles.add(tile);
                    LinearLayout.LayoutParams tileSize=new LinearLayout.LayoutParams(0,-2,1);
                    if(j>i)tileSize.leftMargin=dp(a,10);row.addView(tile,tileSize);
                    tile.setOnClickListener(v->{selected=theme.id;update();});
                }
                if(i+1==themes.length)row.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));
            }
            apply=text(a,"Применить в звонилке",17,0xff0b2318,true);
            apply.setGravity(Gravity.CENTER);apply.setMinHeight(dp(a,56));apply.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));
            apply.setBackground(ripple(a,MINT,16));apply.setFocusable(true);
            LinearLayout.LayoutParams actionSize=new LinearLayout.LayoutParams(-1,-2);actionSize.topMargin=dp(a,12);panel.addView(apply,actionSize);
            apply.setOnClickListener(v->{
                Activity host=getActivity();if(host==null||host.isFinishing())return;
                PhoneThemes.select(host,selected);Appearance.backgroundMode(host,mode);refresh(host);dismiss();
                Toast.makeText(host,"Фон звонилки изменён",Toast.LENGTH_SHORT).show();
            });
            dialog.setContentView(panel);
            Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            update();
            return dialog;
        }

        @Override public void onStart(){
            super.onStart();Activity a=getActivity();Dialog dialog=getDialog();
            if(a==null||dialog==null)return;
            int height=Math.max(dp(a,200),Math.min(a.getResources().getDisplayMetrics().heightPixels-dp(a,48),
                    Math.round(a.getResources().getDisplayMetrics().heightPixels*.91f)));
            dialog.getWindow().setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels,dp(a,600)),height);
        }

        @Override public void onSaveInstanceState(Bundle state){
            super.onSaveInstanceState(state);state.putString("theme",selected);state.putInt("mode",mode);
        }

        private void update(){
            Activity a=getActivity();if(a==null||hero==null)return;
            PhoneThemes.Theme theme=PhoneThemes.find(selected);if(theme==null)return;
            title.setText(theme.name);description.setText(theme.description);
            Appearance.applyPreview(a,hero,PhoneThemes.resource(a,theme),mode);
            for(int i=0;i<contrasts.size();i++){
                TextView chip=contrasts.get(i);chip.setSelected(i==mode);chip.setTextColor(i==mode?0xff0d271b:WHITE);
                chip.setBackground(ripple(a,i==mode?MINT:0xff24392f,13));
            }
            for(ThemeTile tile:tiles){
                boolean chosen=tile.theme.id.equals(selected);
                tile.setSelected(chosen);tile.setContentDescription(tile.theme.name+(chosen?", выбран":""));
                GradientDrawable frame=shape(a,chosen?0xff214236:0xff182c27,17);
                frame.setStroke(dp(a,chosen?2:1),chosen?MINT:0xff365249);
                tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x4476cda9),frame,null));
                tile.check.setVisibility(chosen?View.VISIBLE:View.GONE);
                tile.label.setTextColor(chosen?MINT:WHITE);
                // Collection thumbnails stay visible even when the solid-colour mode is selected.
                Appearance.applyPreview(a,tile.picture,PhoneThemes.resource(a,tile.theme),2);
            }
        }
    }

    private static final class ThemeTile extends LinearLayout {
        final PhoneThemes.Theme theme;
        final View picture;
        final TextView label,check;
        ThemeTile(Activity a,PhoneThemes.Theme theme,int height){
            super(a);this.theme=theme;setOrientation(VERTICAL);setPadding(dp(a,6),dp(a,6),dp(a,6),dp(a,10));
            setFocusable(true);setClickable(true);
            FrameLayout frame=new FrameLayout(a);frame.setBackground(shape(a,0xff0c171a,12));frame.setClipToOutline(true);
            addView(frame,new LinearLayout.LayoutParams(-1,height));
            picture=new View(a);frame.addView(picture,new FrameLayout.LayoutParams(-1,-1));
            check=text(a,"✓",19,0xff0c2619,true);check.setGravity(Gravity.CENTER);check.setBackground(shape(a,MINT,16));
            check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(dp(a,30),dp(a,30),Gravity.END|Gravity.TOP);
            badge.topMargin=dp(a,8);badge.rightMargin=dp(a,8);frame.addView(check,badge);
            label=text(a,theme.name,18,WHITE,true);label.setPadding(dp(a,6),dp(a,10),dp(a,4),0);
            addView(label,-1,-2);
        }
    }

    private static void wallpaper(Activity activity){
        LinearLayout panel=vertical(activity);
        FrameLayout preview=new FrameLayout(activity);preview.setBackground(shape(activity,0xff101b19,16));preview.setClipToOutline(true);
        View artwork=new View(activity);preview.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
        Appearance.applyPreview(activity,artwork,PhoneThemes.selectedResource(activity),2);
        LinearLayout.LayoutParams photoSize=new LinearLayout.LayoutParams(Station.dp(activity,144),Station.dp(activity,265));
        photoSize.gravity=Gravity.CENTER_HORIZONTAL;panel.addView(preview,photoSize);
        TextView caption=text(activity,PhoneThemes.selectedName(activity),21,WHITE,true);
        caption.setGravity(Gravity.CENTER);caption.setPadding(0,dp(activity,12),0,dp(activity,8));panel.addView(caption,-1,-2);
        TextView description=text(activity,"Это обои самого телефона. Android предложит выбрать главный экран или экран блокировки и обрезать изображение.",16,MUTED,false);
        panel.addView(description,-1,-2);
        new StationSheet.Builder(activity).setTitle("Обои экрана телефона").setView(panel)
                .setPositiveButton("Открыть установку обоев…",(dialog,which)->installWallpaper(activity))
                .setNegativeButton("Закрыть",null).show();
    }

    private static void installWallpaper(Activity activity){
        new Thread(()->{
            try{
                Uri image=WallpaperShareProvider.prepare(activity);
                activity.runOnUiThread(()->{
                    if(activity.isFinishing()||activity.isDestroyed())return;
                    try{
                        Intent crop=WallpaperManager.getInstance(activity).getCropAndSetWallpaperIntent(image);
                        grant(crop,image);activity.startActivity(crop);
                    }catch(ActivityNotFoundException|SecurityException|IllegalArgumentException unavailable){
                        String mime=activity.getContentResolver().getType(image);
                        if(mime==null)mime="image/png";
                        Intent useAs=new Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(image,mime).putExtra("mimeType",mime);
                        grant(useAs,image);
                        try{activity.startActivity(Intent.createChooser(useAs,"Установить обои экрана телефона"));}
                        catch(ActivityNotFoundException|SecurityException failure){
                            Toast.makeText(activity,"На телефоне не найдено приложение для установки обоев",Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }catch(java.io.IOException failure){
                activity.runOnUiThread(()->{
                    if(!activity.isFinishing()&&!activity.isDestroyed())
                        Toast.makeText(activity,"Не удалось подготовить обои",Toast.LENGTH_LONG).show();
                });
            }
        },"WallpaperPreview").start();
    }

    private static void grant(Intent intent,Uri image){
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("Обои EVDial",image));
    }
    private static int dp(Activity a,int n){return Station.dp(a,n);}
    private static LinearLayout vertical(Activity a){
        LinearLayout view=new LinearLayout(a);view.setOrientation(LinearLayout.VERTICAL);return view;
    }
    private static TextView text(Activity a,String value,int size,int color,boolean medium){
        TextView view=new TextView(a);view.setText(value);view.setTextSize(size);view.setTextColor(color);
        if(medium)view.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return view;
    }
    private static GradientDrawable shape(Activity a,int color,int radius){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(a,radius));return shape;
    }
    private static RippleDrawable ripple(Activity a,int color,int radius){
        return new RippleDrawable(ColorStateList.valueOf(0x4063bfc3),shape(a,color,radius),null);
    }
}
