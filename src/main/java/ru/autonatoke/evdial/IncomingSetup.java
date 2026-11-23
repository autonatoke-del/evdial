package ru.autonatoke.evdial;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Actual Android settings with refreshed permission state and vendor-specific guidance. */
public final class IncomingSetup extends Activity {
    private static final int SETTINGS=101,ROLE=102,NOTIFICATIONS=103,MICROPHONE=104;
    private static final int MINT=0xff9af2b3,BLUE=0xff99d9f2,AMBER=0xffffc28b,INK=0xfff1f7f1,MUTED=0xffb4c5be;
    private Phone phone;
    private LinearLayout page;
    private ScrollView scroll;
    private boolean enableSip;
    private int guide;
    private final Runnable statusChanged=()->runOnUiThread(this::render);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        phone=(Phone)getApplication();
        guide=state==null?detectedGuide():state.getInt("guide",detectedGuide());
        enableSip=state==null?getIntent().getBooleanExtra("enableSip",false):state.getBoolean("enableSip");
        getWindow().setStatusBarColor(Dialer.BG);getWindow().setNavigationBarColor(Dialer.BG);
    }
    @Override protected void onResume(){super.onResume();maybeEnableSip();phone.observer=statusChanged;render();}
    @Override protected void onPause(){if(phone.observer==statusChanged)phone.observer=null;super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("guide",guide);state.putBoolean("enableSip",enableSip);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);maybeEnableSip();render();}

    private boolean has(String permission){return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    private boolean notificationsEnabled(){return getSystemService(NotificationManager.class).areNotificationsEnabled()
            &&(Build.VERSION.SDK_INT<33||has(Manifest.permission.POST_NOTIFICATIONS));}
    private void maybeEnableSip(){
        if(!enableSip||phone.account==null||!has(Manifest.permission.RECORD_AUDIO)||!notificationsEnabled())return;
        if(CallNotice.incomingChannel(this).getImportance()==NotificationManager.IMPORTANCE_NONE)return;
        phone.setListening(true);enableSip=false;
    }
    private void render(){
        if(isFinishing()||isDestroyed())return;
        int offset=scroll==null?0:scroll.getScrollY();
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Dialer.BG);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(18),dp(8),dp(18),dp(28));
        scroll.addView(page);setContentView(scroll);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=label("‹",30,INK);back.setGravity(Gravity.CENTER);back.setContentDescription("Назад");
        back.setBackground(surface(0xff17231e));back.setOnClickListener(v->finish());top.addView(back,dp(48),dp(48));
        TextView brand=label("EVDial · ТЕЛЕФОН",13,MINT);brand.setPadding(dp(12),0,0,0);top.addView(brand);page.addView(top);
        TextView title=label("Входящие на\nзаблокированном экране",27,INK);title.setPadding(0,dp(20),0,dp(10));page.addView(title);
        paragraph("Разрешения проверяются заново, когда вы возвращаетесь из настроек Android.");

        NotificationManager manager=getSystemService(NotificationManager.class);
        NotificationChannel channel=CallNotice.incomingChannel(this);
        boolean defaultPhone=PhoneRole.held(this),notify=notificationsEnabled();
        boolean banner=channel.getImportance()>=NotificationManager.IMPORTANCE_HIGH;
        boolean lockVisible=channel.getLockscreenVisibility()!=Notification.VISIBILITY_SECRET;
        boolean full=Build.VERSION.SDK_INT<34||manager.canUseFullScreenIntent();
        int done=(defaultPhone?1:0)+(notify?1:0)+(banner&&lockVisible?1:0)+(full?1:0);
        card("Основные настройки",done+" из 4 доступны",done==4?MINT:AMBER,
                "Для городского номера роль телефона по умолчанию не обязательна. Дополнительные ограничения производителя — ниже.",null);

        section("Звонок появляется на экране");
        card("Телефон по умолчанию",defaultPhone?"EVDial выбран":"Выбрать EVDial",defaultPhone?MINT:AMBER,
                "Нужно для входящих и исходящих звонков через SIM.",()->PhoneRole.request(this,ROLE,SETTINGS));
        card("Уведомления приложения",notify?"Разрешены":"Нужен доступ",notify?MINT:AMBER,
                "Разрешите уведомления, чтобы увидеть входящий вызов.",this::notificationSettings);
        card("Входящие звонки",banner&&lockVisible?"Баннер доступен":"Проверить категорию",banner&&lockVisible?MINT:AMBER,
                "Включите всплывающий показ и экран блокировки в категории «Входящие звонки».",()->open(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,CallNotice.RING_CHANNEL)));
        if(Build.VERSION.SDK_INT>=34)card("Полноэкранные вызовы",full?"Разрешены":"Разрешить",full?MINT:AMBER,
                "Android 14 и новее запрашивает отдельный доступ к полноэкранным оповещениям.",()->open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,packageUri())));
        else card("Полноэкранные вызовы","Отдельное разрешение не требуется",MINT,
                "На этой версии Android показ управляется уведомлениями и настройками производителя.",null);

        section("Городская линия · SIP");
        String sipStatus=phone.account==null?"Настроить линию":phone.listening()?"Приём включён":"Приём выключен";
        card("Входящие через интернет",sipStatus,phone.listening()?MINT:BLUE,
                phone.account==null?"Укажите SIP-логин и пароль вашей городской линии.":phone.registration,
                ()->{
                    if(phone.account==null){setResult(RESULT_OK,new Intent().putExtra("configureSip",true));finish();return;}
                    if(phone.listening()){enableSip=false;phone.setListening(false);render();return;}
                    enableSip=true;
                    if(!has(Manifest.permission.RECORD_AUDIO)){microphoneSettings();return;}
                    if(!notificationsEnabled()){notificationSettings();return;}
                    if(CallNotice.incomingChannel(this).getImportance()==NotificationManager.IMPORTANCE_NONE){
                        open(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())
                                .putExtra(Settings.EXTRA_CHANNEL_ID,CallNotice.RING_CHANNEL));return;
                    }
                    maybeEnableSip();render();
                });
        PowerManager power=getSystemService(PowerManager.class);
        boolean unrestricted=power!=null&&power.isIgnoringBatteryOptimizations(getPackageName());
        card("Работа в фоне",unrestricted?"Оптимизация Android отключена":"Проверить ограничения",unrestricted?MINT:BLUE,
                "Для входящих через SIP разрешите работу приложения в фоне. Дополнительные ограничения Xiaomi и Samsung проверяются отдельно.",
                ()->open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));

        section("Инструкция для вашего телефона");
        String[] brands={"Android","Xiaomi / Redmi / POCO","Samsung Galaxy"};
        card(brands[guide],"Выбрать другую инструкцию",BLUE,Build.MANUFACTURER+" · "+Build.MODEL,()->{
            new StationSheet.Builder(this).setTitle("Ваш телефон").setSingleChoiceItems(brands,guide,(dialog,which)->{
                guide=which;render();
            }).setNegativeButton("Закрыть",null).show();
        });
        if(guide==1){
            step("1", "Экран блокировки и всплывающие окна", "Настройки → Приложения → EVDial → Другие разрешения. Разрешите показ на экране блокировки и открытие окон в фоне, если такие пункты есть. В некоторых версиях: Защита конфиденциальности → Другие разрешения.");
            step("2", "Уведомления Xiaomi", "Настройки → Уведомления и строка состояния → Экран блокировки / Плавающие уведомления. Включите EVDial.");
            step("3", "Фоновый автозапуск для SIP", "Настройки → Приложения → Разрешения → Фоновый автозапуск. Включите EVDial; затем в настройках батареи приложения проверьте ограничение фоновой работы.");
            action("Открыть карточку приложения",this::appSettings);
        }else if(guide==2){
            step("1", "Уведомления на экране блокировки", "Настройки → Уведомления → Уведомления на экране блокировки. Разрешите отображение. В уведомлениях EVDial включите категорию «Входящие звонки» и всплывающий показ.");
            step("2", "Категории уведомлений", "Если категории скрыты: Настройки → Уведомления → Дополнительные параметры → Управление категориями уведомлений для приложений. Затем вернитесь в уведомления EVDial.");
            step("3", "Не переводить SIP в сон", "Настройки → Обслуживание устройства → Батарея → Ограничения в фоновом режиме. Уберите EVDial из спящих и глубоко спящих приложений; добавьте в «Никогда не спящие приложения».");
            action("Открыть исключения сна Samsung",()->open(new Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY")
                    .setPackage("com.samsung.android.lool").putExtra("activity_type",2)));
            action("Карточка приложения",this::appSettings);
        }else{
            step("1", "Телефон и уведомления", "Выберите EVDial телефоном по умолчанию для SIM. Разрешите уведомления и категорию «Входящие звонки». На Android 14 и новее включите полноэкранные оповещения.");
            step("2", "Работа городской линии в фоне", "В карточке приложения откройте «Батарея» или «Использование батареи» и разрешите работу в фоне. Для SIP нужны интернет и включённый приём городской линии.");
            action("Открыть карточку приложения",this::appSettings);
        }
        paragraph("Названия пунктов могут отличаться в разных версиях Android, HyperOS/MIUI и One UI. Настройки производителя приложение не может надёжно проверить автоматически.");
        section("Проверка после настройки");
        paragraph("Заблокируйте телефон и позвоните на него с другого номера. Проверьте появление экрана вызова, ответ и завершение. Для городской линии повторите отдельно входящий через SIP. При необходимости проверьте режим «Не беспокоить».");
        action("Готово",this::finish);
        final ScrollView current=scroll;current.post(()->current.scrollTo(0,offset));
    }

    private void microphoneSettings(){
        boolean asked=getPreferences(MODE_PRIVATE).getBoolean("askedMicrophone",false);
        if(!asked||shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)){
            getPreferences(MODE_PRIVATE).edit().putBoolean("askedMicrophone",true).apply();
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE);
        }else appSettings();
    }
    private void notificationSettings(){
        if(Build.VERSION.SDK_INT>=33&&!has(Manifest.permission.POST_NOTIFICATIONS)){
            boolean asked=getPreferences(MODE_PRIVATE).getBoolean("askedNotifications",false);
            if(!asked||shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)){
                getPreferences(MODE_PRIVATE).edit().putBoolean("askedNotifications",true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS);return;
            }
        }
        open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
    }
    private Uri packageUri(){return Uri.fromParts("package",getPackageName(),null);}
    private void appSettings(){open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,packageUri()));}
    private void open(Intent intent){
        try{startActivityForResult(intent,SETTINGS);}
        catch(ActivityNotFoundException|SecurityException failure){
            try{startActivityForResult(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,packageUri()),SETTINGS);}
            catch(ActivityNotFoundException|SecurityException missing){Toast.makeText(this,"Откройте Настройки → Приложения → EVDial",Toast.LENGTH_LONG).show();}
        }
    }
    private int detectedGuide(){
        if("xiaomi".equalsIgnoreCase(Build.MANUFACTURER)||"redmi".equalsIgnoreCase(Build.BRAND)||"poco".equalsIgnoreCase(Build.BRAND))return 1;
        return "samsung".equalsIgnoreCase(Build.MANUFACTURER)?2:0;
    }
    private int dp(int size){return Station.dp(this,size);}
    private TextView label(String text,int size,int color){TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(color);return view;}
    private void paragraph(String text){TextView view=label(text,16,MUTED);view.setLineSpacing(dp(3),1);view.setPadding(0,dp(6),0,dp(10));page.addView(view,-1,-2);}
    private void section(String text){TextView view=label(text,20,INK);view.setTypeface(Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));view.setPadding(0,dp(24),0,dp(8));page.addView(view,-1,-2);}
    private RippleDrawable surface(int color){GradientDrawable fill=new GradientDrawable();fill.setColor(color);fill.setCornerRadius(dp(18));return new RippleDrawable(ColorStateList.valueOf(0x357bd8b0),fill,null);}
    private void card(String title,String state,int accent,String detail,Runnable click){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(14),dp(16),dp(14));
        box.setBackground(surface(0xff17251f));
        TextView heading=label(title,18,INK);heading.setTypeface(Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));box.addView(heading,-1,-2);
        if(!state.isEmpty()){
            TextView status=label(state+(click!=null?"  ›":""),16,accent);status.setPadding(0,dp(6),0,dp(5));box.addView(status,-1,-2);
        }
        if(detail!=null&&!detail.isEmpty()){TextView body=label(detail,15,MUTED);body.setLineSpacing(dp(2),1);box.addView(body,-1,-2);}
        if(click!=null){box.setFocusable(true);box.setOnClickListener(v->click.run());}
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(8);page.addView(box,params);
    }
    private void step(String number,String title,String description){card(number+". "+title,"",BLUE,description,null);}
    private void action(String text,Runnable click){
        TextView button=label(text,17,MINT);button.setGravity(Gravity.CENTER);button.setMinHeight(dp(52));
        button.setPadding(dp(12),dp(10),dp(12),dp(10));button.setBackground(surface(0xff233c30));button.setFocusable(true);button.setOnClickListener(v->click.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(10);page.addView(button,params);
    }
}
