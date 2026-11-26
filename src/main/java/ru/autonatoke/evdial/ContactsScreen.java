package ru.autonatoke.evdial;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.Icon;

import android.view.inputmethod.InputMethodManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.linphone.core.Address;
import org.linphone.core.Call;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ContactsScreen extends Activity {
    private float swipeStartX,swipeStartY;
    private boolean swipeTracking,swipeFromFavorites;
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){
        if(e.getPointerCount()>1){swipeTracking=false;return super.dispatchTouchEvent(e);}
        if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
            swipeStartX=e.getX();swipeStartY=e.getY();swipeTracking=true;
            swipeFromFavorites=false;
            if(favoritesPanel!=null&&favoritesPanel.getVisibility()==View.VISIBLE){
                int[] location=new int[2];
                favoritesPanel.getLocationOnScreen(location);
                float y=e.getRawY();
                swipeFromFavorites=y>=location[1]&&y<location[1]+favoritesPanel.getHeight();
            }
        }else if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP&&swipeTracking){
            swipeTracking=false;
            float dx=e.getX()-swipeStartX,dy=e.getY()-swipeStartY;
            if(!swipeFromFavorites&&Math.abs(dx)>ElectricUi.dp(this,95)&&Math.abs(dx)>Math.abs(dy)*1.8f&&!searching){
                if(recent&&dx<0){switchSwipeTab("dial");return true;}
                if(!recent&&dx>0){switchSwipeTab("dial");return true;}
                if(recent&&dx>0){return super.dispatchTouchEvent(e);}
                if(!recent&&dx<0){return super.dispatchTouchEvent(e);}
            }
        }else if(e.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)swipeTracking=false;
        return super.dispatchTouchEvent(e);
    }
    private void switchSwipeTab(String tab){
        Intent result=new Intent().putExtra("tab",tab);
        setResult(RESULT_OK,result);finish();
    }
    public static final String ACTION_OPEN_CONTACT="ru.autonatoke.evdial.OPEN_CONTACT";
    public static final String EXTRA_CONTACT_NUMBER="contactNumber", EXTRA_CONTACT_LOOKUP="contactLookup";
    private static final int BG=Dialer.BG, CARD=Dialer.CARD, WHITE=Dialer.FG,
            MUTED=Dialer.MUTED, MINT=Dialer.GREEN, BLUE=0xff96dafa, NAV=0xff101613, MISSED=0xffe3bf95;
    private static final int CONTACTS_PERMISSION=31, CALLS_PERMISSION=32, CHOOSE_PHONE=33, PHONE_SETTINGS=34;
    private static final int FAVORITES_PERMISSION=35;
    private final ExecutorService reader=Executors.newSingleThreadExecutor();
    private String shownStyle;
    private List<Entry> people=Collections.emptyList(), history=Collections.emptyList(), visible=Collections.emptyList();
    private Future<?> pendingFilter;
    private int filterGeneration;
    private boolean loading;
    private SearchTerm displayedTerm;
    private static final String[] DIRECTIONS={"Все вызовы","Входящие","Исходящие","Пропущенные"};
    private int direction;
    private TextView heading, directionButton;
    private View addButton;
    private LinearLayout favoritesPanel;
    private List<Entry> favoritesSource;
    private int favoritesCount;
    private boolean recent, automaticallyRequested;
    private int generation;
    private boolean resumed, observingCalls, historyAvailable, hadCallLogAccess;
    private BroadcastReceiver phoneRoleChanges;
    private final Handler changes = new Handler(Looper.getMainLooper());
    private final Runnable refreshHistory = this::reload;
    private final ContentObserver callLogChanges = new ContentObserver(changes) {
        @Override public void onChange(boolean selfChange) {
            if (!resumed) return;
            changes.removeCallbacks(refreshHistory);
            changes.postDelayed(refreshHistory, 300);
        }
    };
    private LinearLayout body, emptyState;
    private View chargingDecoration;
    private TextView emptyAction;
    private ru.autonatoke.evdial.Phone phone;
    private final Runnable observe=this::lineChanged;
    private boolean searching;
    private int restorePosition,restoreOffset;
    private EditText search;
    private TextView count, empty, grant, setupHint;
    private ListView list;
    private Rows adapter;
    private String error="";
    private StationSheet contactSheet;
    private android.app.Dialog historyDialog;
    private Entry cardSelection, historySelection, pendingCard, pendingFavorite;
    private int cardGeneration;

    private final java.util.Map<String,Integer> callGroupCounts=new java.util.HashMap<>();
    private static class Entry {
        String title, detail, route, journalKey, contactLookup;
        final List<String> numbers=new ArrayList<>(), labels=new ArrayList<>();
        final List<String> emails=new ArrayList<>();
        Uri card, photo, largePhoto;
        long date, durationSeconds=-1;
        boolean starred, missed;
        int direction;
        Entry(String title) { this.title=title; }
        String number() { return numbers.isEmpty() ? "" : numbers.get(0); }
        boolean isCall() { return route!=null; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        shownStyle=UiProfiles.signature(this);
        recent=getIntent().getBooleanExtra("recent",false);
        if(state!=null)direction=state.getInt("direction",0);
        automaticallyRequested=state!=null&&state.getBoolean("requested");
        phone=(ru.autonatoke.evdial.Phone)getApplication();
        pendingCard=restoredCard(state);
        if(pendingCard==null&&state==null&&(getIntent().hasExtra(EXTRA_CONTACT_NUMBER)||getIntent().hasExtra(EXTRA_CONTACT_LOOKUP))){
            String n=getIntent().getStringExtra(EXTRA_CONTACT_NUMBER);
            pendingCard=new Entry(TextUtils.isEmpty(n)?"Контакт":n);
            if(!TextUtils.isEmpty(n))pendingCard.numbers.add(n);
            pendingCard.card=contactUri(getIntent().getStringExtra(EXTRA_CONTACT_LOOKUP));
        }
        getWindow().setNavigationBarColor(UiProfiles.active(this).base);
        getWindow().setStatusBarColor(UiProfiles.active(this).base);
        body=new LinearLayout(this);
        body.setId(android.R.id.custom);
        body.setOrientation(LinearLayout.VERTICAL);
        DriveTools.background(this,body);
        body.setFocusableInTouchMode(true);
        setContentView(body);

        LinearLayout bar=Dialer.sectionHeader(this,body);
        bar.setPadding(dp(16),dp(5),dp(10),dp(3));
        bar.addView(icon(R.drawable.dir_search,"Найти",v->setSearch(!searching,true)),dp(48),dp(48));
        bar.addView(icon(R.drawable.ev_settings,"Настройки",v->navigate("settings")),dp(48),dp(48));

        search=new EditText(this);
        search.setSingleLine();
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setHint("Имя или номер во всех записях");
        search.setTextSize(16);
        search.setTextColor(WHITE);
        search.setHintTextColor(MUTED);
        search.setPadding(dp(16),dp(5),dp(12),dp(5));search.setMinimumHeight(dp(48));
        search.setBackground(background(CARD,15));
        LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(-1,-2);
        searchParams.setMargins(dp(18),0,dp(18),dp(4));
        body.addView(search,searchParams);

        list=new ListView(this);
        list.setPadding(dp(18),0,dp(18),dp(12));
        list.setClipToPadding(false);
        list.setDivider(null);
        list.setCacheColorHint(0);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        FrameLayout viewport = new FrameLayout(this);
        body.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        list.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        viewport.addView(list,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=column();
        list.addHeaderView(header,null,false);
        LinearLayout section=line();
        section.setPadding(0,dp(10),0,dp(7));
        header.addView(section,-1,-2);
        heading=text(recent?"Журнал звонков":"Контакты",29,WHITE);
        heading.setTypeface(Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        section.addView(heading,weight());
        if(recent){
            directionButton=chip("",13,"");
            directionButton.setPadding(dp(10),dp(4),dp(10),dp(4));directionButton.setMinHeight(dp(48));
            section.addView(directionButton,-2,-2);
            directionButton.setOnClickListener(v->new StationSheet.Builder(this).setTitle("Показывать вызовы")
                    .setSingleChoiceItems(DIRECTIONS,direction,(d,index)->setDirection(index))
                    .setNegativeButton("Отмена",null).show());
            updateDirectionButton();
        }else{
            ImageView add=icon(R.drawable.dir_add,"Добавить контакт",v->addContact());
            section.addView(add,dp(48),dp(48));addButton=add;
        }
        setupHint=text("",15,MUTED);
        setupHint.setPadding(dp(2),0,dp(2),dp(10));
        setupHint.setVisibility(View.GONE);
        header.addView(setupHint,-1,-2);
        grant=chip("",14,"");grant.setMinHeight(dp(48));grant.setPadding(dp(12),dp(6),dp(12),dp(6));header.addView(grant,-1,-2);
        count=text("",13,MUTED);
        count.setGravity(Gravity.CENTER_VERTICAL);
        count.setPadding(dp(2),dp(4),dp(2),dp(6));
        header.addView(count,-1,-2);
        favoritesPanel=column();
        favoritesPanel.setVisibility(View.GONE);
        header.addView(favoritesPanel,-1,-2);
        emptyState=column();
        emptyState.setPadding(dp(16),dp(14),dp(16),dp(14));
        emptyState.setBackground(background(CARD,18));
        header.addView(emptyState,-1,-2);
        empty=text("",17,WHITE);
        emptyState.addView(empty);
        emptyAction=chip("",14,"");
        emptyAction.setPadding(dp(15),dp(6),dp(15),dp(6));emptyAction.setMinimumHeight(dp(48));
        emptyAction.setTextColor(BG);
        emptyAction.setBackground(surface(MINT,14));
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-2,-2);
        ep.topMargin=dp(12);
        emptyState.addView(emptyAction,ep);
        emptyState.setVisibility(View.GONE);
        adapter=new Rows();list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,i,id)->{
            int index=i-list.getHeaderViewsCount();
            if(index>=0&&index<visible.size()){
                Entry entry=visible.get(index);
                callDisplayed(entry);
            }
        });
        list.setOnItemLongClickListener((p,v,i,id)->{
            int index=i-list.getHeaderViewsCount();if(index<0||index>=visible.size())return false;
            subscriber(visible.get(index));return true;
        });
        chargingDecoration=new ChargingVignette(this);
        chargingDecoration.setVisibility(PhoneThemes.hasSelected(this)?View.GONE:View.VISIBLE);
        body.addView(chargingDecoration,-1,-2);
        Dialer.tabs(this,body,recent?"recent":"contacts",tab->{
            if(tab.equals(recent?"recent":"contacts")){setSearch(false,false);list.smoothScrollToPosition(0);}
            else navigate(tab);
        });
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void onTextChanged(CharSequence s,int a,int b,int c){list.setSelection(0);filter();}
            public void afterTextChanged(Editable e){}
        });
        boolean launchSearch=state==null&&getIntent().getBooleanExtra("openSearch",false);
        setSearch(state!=null?state.getBoolean("searching"):launchSearch,launchSearch);
        if(launchSearch && getIntent().hasExtra("query")){
            search.setText(getIntent().getStringExtra("query"));search.setSelection(search.length());
        }
        if(state!=null){
            search.setText(state.getString("query",""));
            restorePosition=state.getInt("position");
            restoreOffset=state.getInt("offset");
        }
    }
    private LinearLayout column(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private void navigate(String tab){
        setResult(RESULT_OK,new Intent().putExtra("tab",tab));finish();
    }
    private void addContact(){
        open(new Intent(Intent.ACTION_INSERT,ContactsContract.Contacts.CONTENT_URI),"Нет приложения для создания контакта");
    }
    private void setDirection(int index){
        direction=index;updateDirectionButton();list.setSelection(0);filter();
    }
    private void updateDirectionButton(){
        directionButton.setText(DIRECTIONS[direction]+"  ⌄");
        directionButton.setContentDescription("Фильтр журнала: "+DIRECTIONS[direction]);
    }
    private void setSearch(boolean on,boolean keyboard){
        boolean changed=searching!=on;
        searching=on;search.setVisibility(on?View.VISIBLE:View.GONE);
        heading.setText(on?"Общий поиск":recent?"Журнал звонков":"Контакты");
        if(addButton!=null)addButton.setVisibility(on?View.GONE:View.VISIBLE);
        if(directionButton!=null)directionButton.setVisibility(on?View.GONE:View.VISIBLE);
        if(changed && resumed){
            if(recent){gate(PhoneRole.held(this));filter();}else reload();
        }

        InputMethodManager manager=getSystemService(InputMethodManager.class);
        if(on&&keyboard){
            search.requestFocus();search.post(()->manager.showSoftInput(search,InputMethodManager.SHOW_IMPLICIT));
        }else if(!on){
            if(keyboard)manager.hideSoftInputFromWindow(search.getWindowToken(),0);
            body.requestFocus();if(search.length()>0)search.setText("");
        }
    }
    @Override public void onBackPressed(){
        if(searching&&!getIntent().getBooleanExtra("openSearch",false)){setSearch(false,true);return;}
        super.onBackPressed();
    }
    private void lineChanged(){
        if(phone.ringing()){if(!isFinishing())navigate("dial");return;}
    }
    @Override protected void onPause(){
        resumed=false;
        if(phoneRoleChanges!=null){unregisterReceiver(phoneRoleChanges);phoneRoleChanges=null;}
        updateCallObserver(false);
        changes.removeCallbacks(refreshHistory);
        generation++;filterGeneration++;
        if(pendingFilter!=null)pendingFilter.cancel(true);
        if(phone.observer==observe)phone.observer=null;super.onPause();
    }
    @Override protected void onResume(){
        super.onResume();
        if(shownStyle!=null&&!shownStyle.equals(UiProfiles.signature(this))){recreate();return;}
        resumed=true;phone.observer=observe;lineChanged();if(isFinishing())return;
        if(phoneRoleChanges==null)phoneRoleChanges=PhoneRole.watch(this,this::reload);
        Appearance.applyBackground(this,body);
        chargingDecoration.setVisibility(PhoneThemes.hasSelected(this)?View.GONE:View.VISIBLE);
        Directory.invalidate();ContactAvatar.invalidateCache();reload();
        if(!automaticallyRequested && !recent && !allowed(Manifest.permission.READ_CONTACTS)){
            automaticallyRequested=true;
            ask(Manifest.permission.READ_CONTACTS,CONTACTS_PERMISSION);
        }
    }
    @Override protected void onSaveInstanceState(Bundle b){
        super.onSaveInstanceState(b);b.putBoolean("requested",automaticallyRequested);b.putString("query",search.getText().toString());
        b.putInt("direction",direction);b.putBoolean("searching",searching);b.putInt("position",list.getFirstVisiblePosition());
        View top=list.getChildAt(0);b.putInt("offset",top==null?0:top.getTop());
        saveCard(b,cardSelection!=null?cardSelection:historySelection!=null?historySelection:pendingCard);
    }
    @Override protected void onDestroy(){
        generation++;cardGeneration++;
        if(contactSheet!=null){contactSheet.setOnDismissListener(null);contactSheet.dismiss();contactSheet=null;}
        if(historyDialog!=null){
            historyDialog.setOnCancelListener(null);historyDialog.setOnDismissListener(null);
            historyDialog.dismiss();historyDialog=null;
        }
        reader.shutdownNow();super.onDestroy();
    }
    private boolean allowed(String p){return checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=CHOOSE_PHONE&&request!=PHONE_SETTINGS)return;
        if(PhoneRole.held(this)){
            NotificationManager manager=getSystemService(NotificationManager.class);
            if(!manager.areNotificationsEnabled()
                    ||CallNotice.incomingChannel(this).getImportance()<NotificationManager.IMPORTANCE_HIGH
                    ||(Build.VERSION.SDK_INT>=34&&!manager.canUseFullScreenIntent()))
                navigate("incomingSetup");
        }else if(request==CHOOSE_PHONE)PhoneRole.offerSettings(this,PHONE_SETTINGS);
    }
    private void ask(String permission,int request){
        if(Manifest.permission.READ_CALL_LOG.equals(permission)&&!PhoneRole.held(this)){
            PhoneRole.request(this,CHOOSE_PHONE,PHONE_SETTINGS);return;
        }
        if(getPreferences(0).getBoolean(permission,false)&&!shouldShowRequestPermissionRationale(permission)){
            open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package",getPackageName(),null)),"Откройте разрешения в настройках Android");
            return;
        }
        requestPermissions(new String[]{permission},request);
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(p.length>0&&g.length>0)getPreferences(0).edit().putBoolean(p[0],g[0]!=PackageManager.PERMISSION_GRANTED).apply();
        if(r==FAVORITES_PERMISSION){
            Entry selected=pendingFavorite;pendingFavorite=null;
            if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED&&selected!=null)changeFavorite(selected);
            else Toast.makeText(this,"Избранное также можно изменить в контактах телефона",Toast.LENGTH_LONG).show();
        }
        reload();
    }
    private void updateCallObserver(boolean enable) {
        if (enable == observingCalls) return;
        if (enable) {
            try {
                getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI,true,callLogChanges);
                observingCalls=true;
            } catch (SecurityException ignored) {
                // Access can change while returning from system settings.
            }
        } else {
            getContentResolver().unregisterContentObserver(callLogChanges);
            observingCalls=false;
        }
    }
    private void gate(boolean selected){
        grant.setVisibility(View.VISIBLE);
        setupHint.setVisibility(View.GONE);
        boolean needContacts=searching&&!allowed(Manifest.permission.READ_CONTACTS);
        boolean wantCalls=(recent||searching)&&!needContacts;
        if(wantCalls&&!selected){
            setupHint.setText(searching?"Чтобы добавить SIM-вызовы в поиск, выберите «EVDial» телефоном по умолчанию.":
                    "Сейчас вызовы SIM обслуживает другой телефон по умолчанию. Android откроет системный выбор. Выберите «EVDial», чтобы принимать звонки SIM здесь и видеть их историю.");
            setupHint.setVisibility(View.VISIBLE);
            grant.setText("Выбрать «EVDial» телефоном");
            grant.setOnClickListener(v->PhoneRole.request(this,CHOOSE_PHONE,PHONE_SETTINGS));
            return;
        }
        String p=wantCalls?Manifest.permission.READ_CALL_LOG:Manifest.permission.READ_CONTACTS;
        if(allowed(p)){grant.setVisibility(View.GONE);return;}
        if(searching||wantCalls){
            setupHint.setText(wantCalls?"Разрешите доступ к журналу вызовов, чтобы видеть историю SIM.":
                    "Разрешите доступ к контактам, чтобы искать и по телефонной книге.");
            setupHint.setVisibility(View.VISIBLE);
        }
        if(getPreferences(0).getBoolean(p,false)&&!shouldShowRequestPermissionRationale(p)){
            grant.setText("Открыть разрешения приложения");
            grant.setOnClickListener(v->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null)),"Откройте настройки Android"));
        } else {
            grant.setText(wantCalls?"Разрешить историю SIM":"Разрешить доступ к контактам");
            grant.setOnClickListener(v->ask(p,wantCalls?CALLS_PERMISSION:CONTACTS_PERMISSION));
        }
    }
    private void reload(){
        if(!resumed)return;
        boolean callLogAccess=PhoneRole.canReadCallLog(this);
        if(!callLogAccess)discardSimHistory();
        hadCallLogAccess=callLogAccess;
        boolean includeHistory=recent||searching;
        boolean selected=includeHistory&&PhoneRole.held(this);
        gate(selected);
        int ticket=++generation;
        boolean readContacts=allowed(Manifest.permission.READ_CONTACTS);
        boolean readCalls=includeHistory&&callLogAccess;
        historyAvailable=readCalls;
        updateCallObserver(readCalls);
        List<Entry> events=includeHistory?sipHistory():new ArrayList<>();
        loading=true;
        if(visible.isEmpty()){count.setText("Загрузка…");count.setVisibility(View.VISIBLE);emptyState.setVisibility(View.GONE);}
        reader.submit(()->{
            List<Entry> contacts=new ArrayList<>();
            List<String> issues=new ArrayList<>();
            if(readContacts){
                try{contacts(contacts);}catch(RuntimeException failure){issues.add("контакты");}
            }
            if(readCalls){
                try{mobileHistory(events);}catch(RuntimeException failure){issues.add("журнал SIM");}
            }
            events.sort((a,b)->Long.compare(b.date,a.date));
            if(!contacts.isEmpty()&&!events.isEmpty()){
                Map<String,Entry> byNumber=new HashMap<>();
                for(Entry person:contacts)for(String number:person.numbers)
                    byNumber.putIfAbsent(historyKey(number),person);
                for(Entry event:events){
                    Entry person=byNumber.get(event.journalKey);
                    if(person!=null){
                        event.title=person.title;event.card=person.card;event.photo=person.photo;
                        event.largePhoto=person.largePhoto;event.contactLookup=person.contactLookup;event.starred=person.starred;
                    }
                }
            }
            String message=issues.isEmpty()?"":"Не удалось прочитать "+TextUtils.join(" и ",issues);
            runOnUiThread(()->{if(ticket!=generation||isDestroyed())return;
                boolean stillAllowed=PhoneRole.canReadCallLog(this);
                if(!stillAllowed){discardSimHistory();hadCallLogAccess=false;}
                Entry reopen=cardSelection;
                error=message;loading=false;people=contacts;
                history=stillAllowed?events:withoutSimHistory(events);filter();
                if(reopen!=null&&contactSheet!=null&&contactSheet.isShowing())subscriber(reopen);
            });
        });
    }
    private static List<Entry> withoutSimHistory(List<Entry> source){
        List<Entry> result=new ArrayList<>();
        for(Entry entry:source)if(!"SIM".equals(entry.route))result.add(entry);
        return result;
    }
    private void discardSimHistory(){
        historyAvailable=false;updateCallObserver(false);
        history=withoutSimHistory(history);visible=withoutSimHistory(visible);
        filterGeneration++;
        if(pendingFilter!=null)pendingFilter.cancel(true);
        if(pendingCard!=null&&"SIM".equals(pendingCard.route))pendingCard=null;
        if(hadCallLogAccess){
            cardGeneration++;
            if(contactSheet!=null){contactSheet.setOnDismissListener(null);contactSheet.dismiss();contactSheet=null;}
            if(historyDialog!=null){
                historyDialog.setOnCancelListener(null);historyDialog.setOnDismissListener(null);
                historyDialog.dismiss();historyDialog=null;
            }
            cardSelection=null;historySelection=null;
        }
        callGroupCounts.clear();adapter.notifyDataSetChanged();
    }
    private static boolean hasSameContactNumber(List<String> numbers,String candidate){
        if(TextUtils.isEmpty(candidate))return false;
        String key=PhoneNumberUtils.normalizeNumber(candidate);
        for(String number:numbers){
            if(candidate.equals(number))return true;
            if(!TextUtils.isEmpty(key)&&key.length()>=5&&key.equals(PhoneNumberUtils.normalizeNumber(number)))return true;
        }
        return false;
    }
    private void contacts(List<Entry> result){
        String[] fields={Phone.CONTACT_ID,Phone.LOOKUP_KEY,Phone.DISPLAY_NAME,Phone.NUMBER,
                Phone.TYPE,Phone.LABEL,Phone.STARRED,Phone.PHOTO_THUMBNAIL_URI,Phone.PHOTO_URI};
        Map<Long,Entry> grouped=new LinkedHashMap<>();
        try(Cursor c=getContentResolver().query(Phone.CONTENT_URI,fields,null,null,
                Phone.DISPLAY_NAME+" COLLATE LOCALIZED ASC, "+Phone.IS_SUPER_PRIMARY+" DESC")){
            if(c==null)throw new IllegalStateException("Нет данных");
            while(c.moveToNext()){
                long id=c.getLong(0);String number=c.getString(3);
                if(TextUtils.isEmpty(number))continue;
                Entry e=grouped.get(id);
                if(e==null){
                    String name=c.getString(2);e=new Entry(TextUtils.isEmpty(name)?number:name);
                    e.starred=c.getInt(6)!=0;
                    e.contactLookup=c.getString(1);e.card=ContactsContract.Contacts.getLookupUri(id,e.contactLookup);
                    e.photo=uri(c.getString(7));e.largePhoto=uri(c.getString(8));grouped.put(id,e);
                }
                if(!hasSameContactNumber(e.numbers,number)){
                    e.numbers.add(number);
                    e.labels.add(Phone.getTypeLabel(getResources(),c.getInt(4),c.getString(5)).toString());
                }
            }
        }
        result.addAll(grouped.values());
    }
    private List<Entry> sipHistory(){
        List<Entry> result=new ArrayList<>();
        org.linphone.core.CallLog[] logs=phone.sipCore().getCallLogs();
        Set<String> stored=new HashSet<>();
        org.json.JSONArray saved=SipHistory.read(this);
        for(int i=0;i<saved.length();i++){
            org.json.JSONObject event=saved.optJSONObject(i);if(event==null)continue;
            String number=event.optString("number","");
            int type=event.optInt("type",CallLog.Calls.INCOMING_TYPE);
            long time=event.optLong("time");
            boolean outbound=type==CallLog.Calls.OUTGOING_TYPE;
            Entry savedCall=call(number,time,outbound,type==CallLog.Calls.MISSED_TYPE,"Городская");
            savedCall.durationSeconds=event.optLong("duration",-1);result.add(savedCall);
            stored.add(number+"|"+(time/1000)+"|"+outbound);
        }
        if(logs!=null)for(org.linphone.core.CallLog log:logs){
            Address remote=log.getRemoteAddress();String n=remote==null?null:remote.getUsername();
            if(stored.contains(n+"|"+log.getStartDate()+"|"+(log.getDir()==Call.Dir.Outgoing)))continue;
            Entry e=call(n,log.getStartDate()*1000L,log.getDir()==Call.Dir.Outgoing,log.getStatus()==Call.Status.Missed,"Городская");
            e.durationSeconds=log.getDuration();result.add(e);
        }
        return result;
    }
    private Entry call(String number,long date,boolean outbound,boolean missed,String route){
        Entry e=new Entry(TextUtils.isEmpty(number)?"Номер скрыт":number);
        if(!TextUtils.isEmpty(number))e.numbers.add(number);
        e.date=date;e.route=route;e.missed=missed;e.journalKey=historyKey(e.number());
        e.direction=missed?3:outbound?2:1;
        String type=missed?"Пропущенный":outbound?"Исходящий":"Входящий";
        e.detail=type+" · "+route;
        return e;
    }
    private void mobileHistory(List<Entry> result){
        if(!PhoneRole.canReadCallLog(this))return;
        List<Entry> pending=new ArrayList<>();
        String[] fields={CallLog.Calls.NUMBER,CallLog.Calls.DATE,CallLog.Calls.TYPE,CallLog.Calls.NUMBER_PRESENTATION,CallLog.Calls.DURATION,CallLog.Calls.CACHED_NAME};
        try(Cursor c=getContentResolver().query(CallLog.Calls.CONTENT_URI,fields,null,null,CallLog.Calls.DATE+" DESC")){
            if(c==null)throw new IllegalStateException("Нет данных");
            while(c.moveToNext()){
                if(!PhoneRole.canReadCallLog(this))return;
                int type=c.getInt(2);
                String n=c.getInt(3)==CallLog.Calls.PRESENTATION_ALLOWED?c.getString(0):null;
                Entry event=call(n,c.getLong(1),type==CallLog.Calls.OUTGOING_TYPE,type==CallLog.Calls.MISSED_TYPE,"SIM");
                if(n!=null&&!TextUtils.isEmpty(c.getString(5)))event.title=c.getString(5);
                if(!c.isNull(4))event.durationSeconds=c.getLong(4);pending.add(event);
            }
        }
        if(PhoneRole.canReadCallLog(this))result.addAll(pending);
    }
    private static String digits(String s){return PhoneNumberUtils.normalizeNumber(s);}
    private static String lookupKey(String number){
        String d=digits(number);
        if(d.startsWith("+"))d=d.substring(1);
        if(d.length()==11&&d.startsWith("8"))return "7"+d.substring(1);
        return d;
    }
    private static final class SearchTerm {
        final String text, digits;
        SearchTerm(String query){
            text=query.trim().toLowerCase(Locale.ROOT);
            digits=PHONE_FORMAT.matcher(text).matches()?lookupKey(text):"";
        }
        boolean matchesNumber(String number,String key){
            if(number.toLowerCase(Locale.ROOT).contains(text))return true;
            if(digits.isEmpty()||!key.startsWith("tel:"))return false;
            String value=key.substring(4);
            if(value.contains(digits))return true;
            if(value.length()==10&&digits.length()==11&&digits.startsWith("7"))return value.equals(digits.substring(1));
            // Partial Russian national prefix, e.g. "8 (916" vs "+7 916 …".
            return value.length()==11&&value.startsWith("7")&&digits.startsWith("8")&&digits.length()>1&&value.startsWith(digits.substring(1),1);
        }
        String contactNumber(Entry e){
            for(String number:e.numbers)if(matchesNumber(number,historyKey(number)))return number;
            return null;
        }
        boolean matches(Entry e){
            if(e.title.toLowerCase(Locale.ROOT).contains(text))return true;
            return e.isCall()?matchesNumber(e.number(),e.journalKey):contactNumber(e)!=null;
        }
    }
    private void filter(){
        int ticket=++filterGeneration;
        if(pendingFilter!=null)pendingFilter.cancel(true);
        SearchTerm term=new SearchTerm(search.getText().toString());
        int type=recent&&!searching?direction:0;
        if(term.text.isEmpty()&&type==0){
            showResults(recent?history:people,null);
            return;
        }
        // Keep the input responsive when a phone has tens of thousands of calls.
        List<Entry> contacts=people,events=history;
        pendingFilter=reader.submit(()->{
            List<Entry> matches=new ArrayList<>();
            if(type!=0){
                for(Entry event:events){
                    if(Thread.currentThread().isInterrupted())return;
                    if(event.direction==type)matches.add(event);
                }
            }else{
                for(List<Entry> source:Arrays.asList(contacts,events)){
                    for(Entry entry:source){
                        if(Thread.currentThread().isInterrupted())return;
                        if(term.matches(entry))matches.add(entry);
                    }
                }
            }
            runOnUiThread(()->{if(ticket==filterGeneration&&!isDestroyed())showResults(matches,type!=0?null:term);});
        });
    }
    private void showResults(List<Entry> result,SearchTerm term){
        if(!PhoneRole.canReadCallLog(this))result=withoutSimHistory(result);
        boolean scoped=term!=null;
        if(!searching&&term==null&&direction==0)renderFavorites();
        else if(favoritesPanel!=null)favoritesPanel.setVisibility(View.GONE);
        callGroupCounts.clear();
        if(recent&&!searching&&term==null){
            java.util.LinkedHashMap<String,Entry> grouped=new java.util.LinkedHashMap<>();
            for(Entry event:result){
                if(!event.isCall())continue;
                String key=event.card!=null?"contact:"+event.card.toString():event.journalKey;
                if(key==null||key.isEmpty())key="event:"+event.date+":"+event.number();
                if(!grouped.containsKey(key))grouped.put(key,event);
                callGroupCounts.put(key,callGroupCounts.containsKey(key)?callGroupCounts.get(key)+1:1);
            }
            visible=new ArrayList<>(grouped.values());
        }else visible=result;
        displayedTerm=term;
        boolean waiting=loading&&visible.isEmpty();
        count.setText(!error.isEmpty()?error:waiting?"Загрузка…":"Найдено: "+visible.size());
        count.setVisibility(!error.isEmpty()||scoped||waiting?View.VISIBLE:View.GONE);
        boolean filtered=recent&&!searching&&direction!=0;
        empty.setText(scoped?"Совпадений нет":filtered?"В этой группе пока нет вызовов":recent?(historyAvailable?"Пока нет вызовов":"Звонков по городской линии пока нет") :
                !allowed(Manifest.permission.READ_CONTACTS)?"Разрешите доступ к телефонной книге":"В телефонной книге пока нет номеров");
        emptyState.setVisibility(visible.isEmpty()&&!waiting&&error.isEmpty()?View.VISIBLE:View.GONE);
        emptyAction.setVisibility(scoped||recent?View.VISIBLE:View.GONE);
        emptyAction.setText(scoped?"Очистить поиск":filtered?"Все вызовы":recent?"Набрать номер":"Добавить контакт");
        emptyAction.setOnClickListener(v->{if(scoped)search.setText("");else if(filtered)setDirection(0);else if(recent)navigate("dial");else addContact();});
        adapter.notifyDataSetChanged();
        if(!loading&&(restorePosition!=0||restoreOffset!=0)){
            list.setSelectionFromTop(restorePosition,restoreOffset);restorePosition=0;restoreOffset=0;
        }
        if(!loading&&pendingCard!=null&&resumed){
            Entry selected=pendingCard;pendingCard=null;subscriber(selected);
        }
    }
    private void renderFavorites(){
        if(favoritesPanel==null)return;
        if(favoritesSource==people){
            favoritesPanel.setVisibility(favoritesCount>0?View.VISIBLE:View.GONE);
            return;
        }
        favoritesSource=people;
        favoritesPanel.removeAllViews();
        List<Entry> picks=new ArrayList<>();
        for(Entry entry:people){
            if(entry.starred&&!entry.numbers.isEmpty()){
                picks.add(entry);
                if(picks.size()==8)break;
            }
        }
        favoritesCount=picks.size();
        if(picks.isEmpty()){favoritesPanel.setVisibility(View.GONE);return;}
        TextView caption=text("Избранные · быстрый доступ",17,BLUE);
        caption.setPadding(dp(2),dp(2),dp(2),dp(8));
        favoritesPanel.addView(caption,-1,-2);
        HorizontalScrollView strip=new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout items=new LinearLayout(this);
        items.setGravity(Gravity.CENTER_VERTICAL);
        strip.addView(items,new FrameLayout.LayoutParams(-2,-2));
        favoritesPanel.addView(strip,new LinearLayout.LayoutParams(-1,-2));
        for(Entry entry:picks){
            LinearLayout tile=column();
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(6),dp(10),dp(6),dp(6));
            tile.setBackground(surface(CARD,18));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(106),dp(146));
            params.rightMargin=dp(8);
            items.addView(tile,params);
            ContactAvatar avatar=new ContactAvatar(this,46);
            avatar.bind(entry.title,entry.photo);
            LinearLayout.LayoutParams av=new LinearLayout.LayoutParams(dp(46),dp(46));
            av.gravity=Gravity.CENTER_HORIZONTAL;
            tile.addView(avatar,av);
            TextView name=text(entry.title,13,WHITE);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0,dp(4),0,dp(2));
            tile.addView(name,-1,dp(26));
            TextView call=chip("Позвонить",12,"Позвонить "+entry.title);
            call.setGravity(Gravity.CENTER);
            call.setMinimumHeight(dp(48));
            tile.addView(call,new LinearLayout.LayoutParams(-1,dp(48)));
            call.setOnClickListener(v->chooseNumber(entry,n->pick(entry,n,true)));
            tile.setContentDescription("Открыть контакт "+entry.title);
            tile.setOnClickListener(v->subscriber(entry));
        }
        favoritesPanel.setVisibility(View.VISIBLE);
    }
    private String displayedNumber(Entry e){
        String matched=!e.isCall()&&displayedTerm!=null?displayedTerm.contactNumber(e):null;
        return matched==null?e.number():matched;
    }
    private void callDisplayed(Entry e){
        // A call-history row always calls its own number, never another number of the contact.
        String number=displayedNumber(e);
        if(!number.isEmpty())pick(e,number,true);
    }
    private void chooseNumber(Entry e,java.util.function.Consumer<String> action){
        if(e.numbers.isEmpty())return;
        if(e.numbers.size()==1){action.accept(e.number());return;}
        String[] choices=new String[e.numbers.size()];
        for(int i=0;i<choices.length;i++)choices[i]=(i<e.labels.size()?e.labels.get(i):"Телефон")+"  ·  "+e.numbers.get(i);
        new StationSheet.Builder(this).setTitle(e.title).setItems(choices,(d,i)->action.accept(e.numbers.get(i)))
             .setNegativeButton("Отмена",null).show();
    }
    private void pick(Entry e,String number,boolean direct){
        if("SIM".equals(e.route)&&!PhoneRole.canReadCallLog(this)){reload();return;}
        if(TextUtils.isEmpty(number))return;
        setResult(RESULT_OK,new Intent().putExtra("number",number).putExtra("name",e.title)
                .putExtra("route",e.route).putExtra("directCall",direct));finish();
    }
    private static final java.util.regex.Pattern PHONE_FORMAT=java.util.regex.Pattern.compile("[+0-9() .-]+");
    private static String historyKey(String number){
        if(number==null)return "";
        String trimmed=number.trim();
        if(trimmed.isEmpty())return "";
        // SIP usernames and service codes are exact identifiers. Never merge them by digits.
        if(!PHONE_FORMAT.matcher(trimmed).matches())return "id:"+trimmed;
        return "tel:"+lookupKey(trimmed);
    }

    private List<Entry> historyFor(Entry selected){
        return historyFor(selected,history);
    }
    private List<Entry> historyFor(Entry selected,List<Entry> source){
        List<Entry> result=new ArrayList<>();
        Set<String> keys=new HashSet<>();
        for(String number:selected.numbers){String key=historyKey(number);if(!key.isEmpty())keys.add(key);}
        if(keys.isEmpty())return result;
        for(Entry event:source){
            if(keys.contains(event.journalKey))result.add(event);
        }
        result.sort((a,b)->Long.compare(b.date,a.date));
        return result;
    }

    private static Uri uri(String value){return TextUtils.isEmpty(value)?null:Uri.parse(value);}
    private static Uri contactUri(String value){
        if(TextUtils.isEmpty(value))return null;
        Uri candidate=Uri.parse(value);
        String path=candidate.getPath();
        return "content".equals(candidate.getScheme())&&ContactsContract.AUTHORITY.equals(candidate.getAuthority())
                &&path!=null&&path.startsWith("/contacts/")?candidate:null;
    }
    private void saveCard(Bundle state,Entry e){
        if(e==null)return;
        Bundle b=new Bundle();b.putString("title",e.title);b.putString("number",e.number());
        b.putString("uri",e.card==null?null:e.card.toString());b.putString("route",e.route);
        b.putString("detail",e.detail);b.putLong("date",e.date);b.putLong("duration",e.durationSeconds);
        b.putInt("direction",e.direction);b.putBoolean("missed",e.missed);state.putBundle("contactCard",b);
    }
    private Entry restoredCard(Bundle state){
        if(state==null)return null;Bundle b=state.getBundle("contactCard");if(b==null)return null;
        Entry e=new Entry(b.getString("title","Контакт"));String n=b.getString("number","");
        if(!n.isEmpty())e.numbers.add(n);e.card=contactUri(b.getString("uri"));
        e.route=b.getString("route");e.detail=b.getString("detail");e.date=b.getLong("date");
        e.durationSeconds=b.getLong("duration",-1);e.direction=b.getInt("direction");e.missed=b.getBoolean("missed");
        e.journalKey=historyKey(n);return e;
    }
    private Entry copyContact(Entry seed){
        Entry result=new Entry(seed.title);result.numbers.addAll(seed.numbers);result.labels.addAll(seed.labels);
        result.card=seed.card;result.contactLookup=seed.contactLookup;result.starred=seed.starred;
        result.photo=seed.photo;result.largePhoto=seed.largePhoto;result.emails.addAll(seed.emails);return result;
    }
    private Entry contactDetails(Entry seed){
        Entry fallback=copyContact(seed);
        if(!allowed(Manifest.permission.READ_CONTACTS))return fallback;
        try{
            Uri resolved=seed.card==null?null:ContactsContract.Contacts.lookupContact(getContentResolver(),seed.card);
            if(resolved==null&&!seed.number().isEmpty()){
                Uri lookup=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(seed.number()));
                try(Cursor c=getContentResolver().query(lookup,new String[]{ContactsContract.PhoneLookup._ID,ContactsContract.PhoneLookup.LOOKUP_KEY},null,null,null)){
                    if(c!=null&&c.moveToFirst())resolved=ContactsContract.Contacts.getLookupUri(c.getLong(0),c.getString(1));
                }
            }
            if(resolved==null){fallback.card=null;fallback.contactLookup=null;return fallback;}
            String[] fields={ContactsContract.Contacts._ID,ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME,ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                    ContactsContract.Contacts.PHOTO_URI,ContactsContract.Contacts.STARRED};
            Entry person;long id;
            try(Cursor c=getContentResolver().query(resolved,fields,null,null,null)){
                if(c==null||!c.moveToFirst()){fallback.card=null;return fallback;}
                id=c.getLong(0);person=new Entry(TextUtils.isEmpty(c.getString(2))?seed.title:c.getString(2));
                person.contactLookup=c.getString(1);person.card=ContactsContract.Contacts.getLookupUri(id,person.contactLookup);
                person.photo=uri(c.getString(3));person.largePhoto=uri(c.getString(4));person.starred=c.getInt(5)!=0;
            }
            try(Cursor c=getContentResolver().query(Phone.CONTENT_URI,new String[]{Phone.NUMBER,Phone.TYPE,Phone.LABEL},
                    Phone.CONTACT_ID+"=?",new String[]{String.valueOf(id)},Phone.IS_SUPER_PRIMARY+" DESC")){
                if(c!=null)while(c.moveToNext()){
                    String n=c.getString(0);if(TextUtils.isEmpty(n)||hasSameContactNumber(person.numbers,n))continue;
                    person.numbers.add(n);person.labels.add(Phone.getTypeLabel(getResources(),c.getInt(1),c.getString(2)).toString());
                }
            }
            try(Cursor c=getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Email.ADDRESS},
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID+"=?",new String[]{String.valueOf(id)},null)){
                if(c!=null)while(c.moveToNext()){
                    String email=c.getString(0);if(!TextUtils.isEmpty(email)&&!person.emails.contains(email))person.emails.add(email);
                }
            }
            return person;
        }catch(RuntimeException unavailable){return fallback;}
    }

    private void showHistory(Entry selected,List<Entry> source){
        boolean readCalls=PhoneRole.canReadCallLog(this);
        if("SIM".equals(selected.route)&&!readCalls){reload();return;}
        final List<Entry> events=readCalls?source:withoutSimHistory(source);
        if(historyDialog!=null){historyDialog.setOnCancelListener(null);historyDialog.dismiss();}
        android.app.Dialog dialog=new android.app.Dialog(this);
        historyDialog=dialog;historySelection=selected;
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel=new LinearLayout(this);
        panel.setBackgroundColor(0xff111a15);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16),dp(8),dp(16),dp(10));
        TextView back=text("‹   К карточке",15,MINT);
        back.setGravity(Gravity.CENTER_VERTICAL);back.setMinHeight(dp(48));panel.addView(back,-1,-2);
        back.setOnClickListener(v->{dialog.dismiss();subscriber(selected);});
        TextView heading=text("История",26,WHITE);
        heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);panel.addView(heading,-1,-2);
        String name=selected.title.equals(selected.number())?selected.number():selected.title+"  ·  "+selected.number();
        TextView owner=text(name,16,MUTED);owner.setPadding(0,dp(5),0,dp(12));panel.addView(owner,-1,-2);
        ListView calls=new ListView(this);
        panel.addView(calls,new LinearLayout.LayoutParams(-1,0,1));
        java.text.DateFormat days=android.text.format.DateFormat.getMediumDateFormat(this);
        java.text.DateFormat times=android.text.format.DateFormat.getTimeFormat(this);
        calls.setAdapter(new BaseAdapter(){
            public int getCount(){return events.size();}
            public Entry getItem(int p){return events.get(p);}
            public long getItemId(int p){return p;}
            public View getView(int p,View reuse,ViewGroup parent){
                LinearLayout row=(LinearLayout)reuse;
                if(row==null){
                    row=new LinearLayout(ContactsScreen.this);row.setOrientation(LinearLayout.VERTICAL);
                    row.setPadding(dp(6),dp(10),dp(6),dp(10));row.setMinimumHeight(dp(56));
                    row.addView(text("",17,WHITE));row.addView(text("",14,MUTED));
                }
                Entry event=getItem(p);Date when=new Date(event.date);
                TextView line=(TextView)row.getChildAt(0);line.setText(days.format(when)+"   ·   "+times.format(when));
                TextView info=(TextView)row.getChildAt(1);
                info.setText(event.detail+(event.durationSeconds>0?"   ·   "+android.text.format.DateUtils.formatElapsedTime(event.durationSeconds):""));
                info.setTextColor(event.missed?MISSED:MUTED);
                return row;
            }
        });
        calls.setOnItemClickListener((parent,row,position,id)->{dialog.dismiss();subscriber(events.get(position));});
        dialog.setOnCancelListener(d->{if(!isFinishing()&&!isDestroyed())subscriber(selected);});
        dialog.setOnDismissListener(d->{if(historyDialog==dialog){historyDialog=null;historySelection=null;}});
        dialog.setContentView(panel);
        android.view.Window window=dialog.getWindow();
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff111a15));
        window.setGravity(Gravity.BOTTOM);dialog.show();
        window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,dp(550)),
                Math.max(dp(240),(int)(body.getHeight()*.87f)));
    }

    private boolean hasNearbySimCall(Entry call) {
        return hasNearbySimCall(call,history);
    }
    private boolean hasNearbySimCall(Entry call,List<Entry> events) {
        String number = lookupKey(call.number());
        if (number.isEmpty()) return false;
        for (Entry other : events) {
            if (other == call || !"SIM".equals(other.route)) continue;
            if (other.date==call.date&&other.direction==call.direction&&other.number().equals(call.number())) continue;
            // The two ±15 second recording match windows overlap.
            if (Math.abs(other.date - call.date) <= 30_000L && number.equals(lookupKey(other.number()))) return true;
        }
        return false;
    }
    private void subscriber(Entry e){
        if(isFinishing()||isDestroyed())return;
        if("SIM".equals(e.route)&&!PhoneRole.canReadCallLog(this)){reload();return;}
        final int ticket=++cardGeneration;
        StationSheet opening=new StationSheet.Builder(this).setTitle(e.title)
                .setMessage("Загрузка карточки…").setNegativeButton("Закрыть",null).create();
        presentCard(opening,e);
        final boolean haveHistory=recent||searching;
        final boolean readCalls=PhoneRole.canReadCallLog(this);
        final boolean historyReadFailed=haveHistory&&error.contains("журнал SIM");
        final List<Entry> events=new ArrayList<>();
        if(haveHistory)events.addAll(history);
        else try{events.addAll(sipHistory());}catch(RuntimeException ignored){}
        reader.submit(()->{
            Entry person=contactDetails(e);
            boolean callReadFailed=false;
            if(!haveHistory&&readCalls)try{mobileHistory(events);}catch(RuntimeException unavailable){callReadFailed=true;}
            Entry historyOwner=copyContact(person);
            // Keep the original call visible even if its number has since been removed from the contact.
            if(e.isCall()&&!e.number().isEmpty()&&!historyOwner.numbers.contains(e.number()))historyOwner.numbers.add(e.number());
            List<Entry> related=historyFor(historyOwner,events);
            final boolean complete=readCalls&&!callReadFailed&&!historyReadFailed;
            runOnUiThread(()->{
                if(ticket!=cardGeneration||contactSheet!=opening||!opening.isShowing()||!resumed||isFinishing()||isDestroyed())return;
                boolean stillAllowed=PhoneRole.canReadCallLog(this);
                if("SIM".equals(e.route)&&!stillAllowed){opening.dismiss();reload();return;}
                renderSubscriber(e,person,stillAllowed?related:withoutSimHistory(related),complete&&stillAllowed);
            });
        });
    }
    private void presentCard(StationSheet sheet,Entry selected){
        if(contactSheet!=null){contactSheet.setOnDismissListener(null);contactSheet.dismiss();}
        contactSheet=sheet;cardSelection=selected;
        sheet.setOnDismissListener(dialog->{
            if(contactSheet==sheet){contactSheet=null;cardSelection=null;cardGeneration++;}
        });
        sheet.show();
    }
    private void renderSubscriber(Entry selected,Entry person,List<Entry> events,boolean completeHistory){
        LinearLayout panel=column();
        StationSheet sheet=new StationSheet.Builder(this).setView(panel).setNegativeButton("Закрыть",null).create();
        boolean known=person.card!=null;
        Entry target=selected.isCall()?selected:person;
        String number=target.number();
        LinearLayout top=line();top.setPadding(0,dp(3),0,dp(3));panel.addView(top,-1,-2);
        TextView identity=text(known?"КОНТАКТ":"НОМЕР ТЕЛЕФОНА",11,BLUE);
        identity.setLetterSpacing(.08f);top.addView(identity,weight());
        if(known){
            TextView favorite=chip(person.starred?"★":"☆",23,person.starred?"Убрать из избранного":"Добавить в избранное");
            favorite.setTextColor(person.starred?MINT:MUTED);top.addView(favorite,dp(42),dp(42));
            favorite.setOnClickListener(v->favorite(person));
            TextView edit=chip("✎",21,"Изменить контакт и фотографию");
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(dp(42),dp(42));ep.leftMargin=dp(6);top.addView(edit,ep);
            edit.setOnClickListener(v->editContact(person));
        }
        ContactAvatar avatar=new ContactAvatar(this,72);
        avatar.bind(person.title,person.largePhoto!=null?person.largePhoto:person.photo);
        avatar.setContentDescription(known?"Изменить фотографию контакта":"Фотография появится после сохранения контакта");
        LinearLayout.LayoutParams av=new LinearLayout.LayoutParams(dp(72),dp(72));av.gravity=Gravity.CENTER_HORIZONTAL;
        av.topMargin=dp(3);av.bottomMargin=dp(6);panel.addView(avatar,av);
        avatar.setOnClickListener(known?v->editContact(person):null);
        TextView name=text(person.title,23,WHITE);name.setGravity(Gravity.CENTER);
        name.setTypeface(Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));panel.addView(name,-1,-2);
        if(!number.isEmpty()&&!person.title.equals(number)){
            TextView address=text(number,16,MUTED);address.setGravity(Gravity.CENTER);address.setPadding(0,dp(2),0,dp(3));
            panel.addView(address,-1,-2);
        }
        if(!number.isEmpty()){
            LinearLayout shortcuts=line();
            shortcuts.setPadding(0,dp(12),0,dp(6));
            panel.addView(shortcuts,-1,-2);
            TextView call=contactQuickAction("Звонок",R.drawable.dir_callback,true,sheet,
                    ()->chooseNumber(target,n->pick(selected,n,true)));
            shortcuts.addView(call,weight());
            TextView sms=contactQuickAction("SMS",R.drawable.dir_sms,false,sheet,
                    ()->chooseNumber(target,n->QuickSms.open(this,n,person.title)));
            sms.setContentDescription("Написать SMS");
            LinearLayout.LayoutParams smsSize=weight();smsSize.leftMargin=dp(6);shortcuts.addView(sms,smsSize);
            TextView share=contactQuickAction("Отправить",android.R.drawable.ic_menu_share,false,sheet,
                    ()->shareContact(person,number));
            share.setContentDescription(known?"Поделиться карточкой контакта .vcf":"Поделиться номером");
            LinearLayout.LayoutParams shareSize=weight();shareSize.leftMargin=dp(6);shortcuts.addView(share,shareSize);
        }
        if(!person.numbers.isEmpty()){
            cardHeading(panel,person.numbers.size()>1?"Номера телефона":"Телефон");
            for(int i=0;i<person.numbers.size();i++){
                String n=person.numbers.get(i),label=i<person.labels.size()?person.labels.get(i):"Телефон";
                LinearLayout row=line();row.setPadding(dp(10),dp(4),dp(4),dp(4));row.setBackground(background(CARD,13));
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(4);panel.addView(row,rp);
                LinearLayout words=column();words.setGravity(Gravity.CENTER_VERTICAL);words.setMinimumHeight(dp(48));
                words.addView(text(n,16,WHITE));words.addView(text(label,12,MUTED));
                words.setContentDescription("Позвонить на "+n);words.setBackground(surface(0x001b2d28,10));
                words.setOnClickListener(v->{sheet.dismiss();pick(selected,n,true);});words.setOnLongClickListener(v->{android.content.ClipboardManager cm=getSystemService(android.content.ClipboardManager.class);cm.setPrimaryClip(android.content.ClipData.newPlainText("Номер телефона",n));Toast.makeText(this,"Номер скопирован",Toast.LENGTH_SHORT).show();return true;});row.addView(words,weight());
                ImageView message=icon(R.drawable.dir_sms,"Сообщение на "+n,v->{sheet.dismiss();QuickSms.open(this,n,person.title);});
                message.setColorFilter(BLUE);row.addView(message,dp(48),dp(48));
            }
        }
        if(!person.emails.isEmpty()){
            cardHeading(panel,"Электронная почта");
            for(String email:person.emails)subscriberRow(panel,email,false,sheet,
                    ()->open(new Intent(Intent.ACTION_SENDTO,Uri.fromParts("mailto",email,null)),"Нет приложения для электронной почты"));
        }
        if(!number.isEmpty()){
            cardHeading(panel,"Мессенджеры");
            TextView destination=text(number,14,MUTED);panel.addView(destination,-1,-2);
            LinearLayout messengerPanel=column();panel.addView(messengerPanel,-1,-2);
            MessengerShortcuts.add(this,messengerPanel,number,new MessengerShortcuts.Payload(){
                public String text(){return "";}public ArrayList<Uri> files(){return new ArrayList<>();}
            });
            // Direct chats need no draft. The generic share action does, so let the existing composer collect it.
            if(messengerPanel.getChildCount()>0&&messengerPanel.getChildAt(0) instanceof LinearLayout){
                LinearLayout apps=(LinearLayout)messengerPanel.getChildAt(0);
                if(apps.getChildCount()>0)apps.getChildAt(apps.getChildCount()-1).setOnClickListener(v->{
                    sheet.dismiss();QuickSms.open(this,number,person.title);
                });
            }
        }
        if(selected.isCall()){
            cardHeading(panel,"Этот звонок");
            TextView facts=text(callDescription(selected),16,selected.missed?MISSED:MUTED);
            facts.setPadding(dp(3),0,dp(3),dp(5));panel.addView(facts,-1,-2);
            if(!selected.number().isEmpty()){
                String label=RecordingLink.hasLink(this,selected.number(),selected.date)?"Прослушать запись":"Запись этого разговора";
                subscriberRow(panel,label,true,sheet,()->{
                    boolean simCall="SIM".equals(selected.route);
                    RecordingLink.open(this,selected.number(),selected.date,simCall&&hasNearbySimCall(selected,events),simCall);
                });
            }
        }
        cardHeading(panel,"Недавние звонки");
        if(events.isEmpty()){
            TextView none=text(completeHistory?"Звонков с этим номером пока нет":"Доступных звонков пока нет",16,MUTED);
            none.setPadding(dp(3),dp(3),dp(3),dp(8));panel.addView(none,-1,-2);
        }else{
            for(int i=0;i<Math.min(2,events.size());i++){
                Entry event=events.get(i);
                subscriberRow(panel,briefCallDescription(event),false,sheet,()->subscriber(event));
            }
            subscriberRow(panel,"Вся история · "+events.size(),true,sheet,()->showHistory(selected,events));
            if(!selected.isCall())subscriberRow(panel,"Выбрать звонок для прослушивания записи",false,sheet,()->showHistory(selected,events));
        }
        if(!completeHistory){
            TextView limited=text("Для истории SIM нужен доступ к журналу вызовов.",14,MUTED);
            limited.setPadding(dp(3),dp(5),dp(3),dp(8));panel.addView(limited,-1,-2);
            subscriberRow(panel,"Разрешить историю SIM",false,sheet,()->{
                if(!PhoneRole.held(this))PhoneRole.request(this,CHOOSE_PHONE,PHONE_SETTINGS);
                else ask(Manifest.permission.READ_CALL_LOG,CALLS_PERMISSION);
            });
        }
        cardHeading(panel,"Дополнительно");
        if(known){
            subscriberRow(panel,"Изменить контакт и фотографию",false,sheet,()->editContact(person))
                    .setOnClickListener(v->editContact(person));
        }else if(!number.isEmpty()){
            subscriberRow(panel,"Создать контакт",true,sheet,()->Dialer.saveNumber(this,number));
            subscriberRow(panel,"Добавить номер к контакту",false,sheet,()->Dialer.addToContact(this,number));
        }
        if(!allowed(Manifest.permission.READ_CONTACTS))subscriberRow(panel,"Разрешить имена и фото контактов",false,sheet,
                ()->ask(Manifest.permission.READ_CONTACTS,CONTACTS_PERMISSION));
        if(!number.isEmpty())subscriberRow(panel,"Скопировать номер",false,sheet,()->{android.content.ClipboardManager cm=getSystemService(android.content.ClipboardManager.class);cm.setPrimaryClip(android.content.ClipData.newPlainText("Номер телефона",number));Toast.makeText(this,"Номер скопирован",Toast.LENGTH_SHORT).show();});
        if(known||!number.isEmpty()){
            subscriberRow(panel,"Поделиться именем и номером",false,sheet,()->shareNumber(person,number))
                    .setOnClickListener(v->shareNumber(person,number));
            subscriberRow(panel,"Добавить на главный экран",false,sheet,()->pinContact(person,number))
                    .setOnClickListener(v->pinContact(person,number));
        }
        if(known){
            subscriberRow(panel,"Ещё настройки контакта",false,sheet,()->card(person))
                    .setOnClickListener(v->card(person));
            // System contact settings remain available via the row above.
        }
        presentCard(sheet,selected);
    }
    private void cardHeading(LinearLayout panel,String label){
        TextView heading=text(label,15,BLUE);
        heading.setTypeface(Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        heading.setPadding(dp(3),dp(13),dp(3),dp(6));
        panel.addView(heading,-1,-2);
    }
    private String briefCallDescription(Entry e){
        Date when=new Date(e.date);
        String day=android.text.format.DateFormat.getMediumDateFormat(this).format(when);
        String clock=android.text.format.DateFormat.getTimeFormat(this).format(when);
        String length=e.durationSeconds>0&&!e.missed?" · "+android.text.format.DateUtils.formatElapsedTime(e.durationSeconds):"";
        return (e.detail==null?"Звонок":e.detail)+"  ·  "+day+" "+clock+length;
    }
    private String callDescription(Entry e){
        Date when=new Date(e.date);
        String date=android.text.format.DateFormat.getMediumDateFormat(this).format(when);
        String clock=android.text.format.DateFormat.getTimeFormat(this).format(when);
        String result=(e.detail==null?"Звонок":e.detail)+"\n"+date+" · "+clock;
        if(e.durationSeconds>0&&!e.missed)result+=" · "+android.text.format.DateUtils.formatElapsedTime(e.durationSeconds);
        if(!e.number().isEmpty())result+="\n"+e.number();return result;
    }
    private void editContact(Entry person){
        if(person.card==null)return;
        open(new Intent(Intent.ACTION_EDIT).setDataAndType(person.card,ContactsContract.Contacts.CONTENT_ITEM_TYPE),
                "Не удалось открыть редактор контакта");
    }
    private void favorite(Entry person){
        if(person.card==null)return;
        if(!allowed(Manifest.permission.WRITE_CONTACTS)){
            if(getPreferences(0).getBoolean(Manifest.permission.WRITE_CONTACTS,false)&&!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CONTACTS)){
                Toast.makeText(this,"Измените избранное в контактах телефона или разрешите изменение контактов",Toast.LENGTH_LONG).show();
                card(person);return;
            }
            pendingFavorite=person;requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS},FAVORITES_PERMISSION);return;
        }
        changeFavorite(person);
    }
    private void changeFavorite(Entry person){
        reader.submit(()->{
            boolean updated=false;
            try{
                ContentValues change=new ContentValues();change.put(ContactsContract.Contacts.STARRED,person.starred?0:1);
                updated=getContentResolver().update(person.card,change,null,null)>0;
            }catch(RuntimeException unavailable){}
            final boolean success=updated;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(success){Directory.invalidate();reload();}
                else Toast.makeText(this,"Не удалось изменить избранное. Откройте контакт в телефоне.",Toast.LENGTH_LONG).show();
            });
        });
    }
    private void shareContact(Entry person,String selectedNumber){
        if(person.card==null||TextUtils.isEmpty(person.contactLookup)){
            shareNumber(person,selectedNumber);
            return;
        }
        Uri vcard=Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI,person.contactLookup);
        Intent send=new Intent(Intent.ACTION_SEND).setType("text/x-vcard").putExtra(Intent.EXTRA_STREAM,vcard);
        send.setClipData(new ClipData("Контакт",new String[]{"text/x-vcard"},new ClipData.Item(vcard)));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser=Intent.createChooser(send,"Поделиться контактом (.vcf)");
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        open(chooser,"Не удалось открыть отправку контакта");
    }
    private void shareNumber(Entry person,String selectedNumber){
        String numbers=person.numbers.isEmpty()?selectedNumber:TextUtils.join("\n",person.numbers);
        String value=person.title.equals(numbers)?numbers:person.title+"\n"+numbers;
        open(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,value),
                "Поделиться номером"),"Не удалось открыть отправку номера");
    }
    private void pinContact(Entry person,String selectedNumber){
        ShortcutManager manager=getSystemService(ShortcutManager.class);
        if(manager==null||!manager.isRequestPinShortcutSupported()){
            Toast.makeText(this,"Этот главный экран не поддерживает добавление ярлыков из приложения",Toast.LENGTH_LONG).show();return;
        }
        String key=person.contactLookup!=null?person.contactLookup:historyKey(selectedNumber);
        String id="contact-"+UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Intent target=new Intent(this,Dialer.class).setAction(ACTION_OPEN_CONTACT)
                .putExtra(EXTRA_CONTACT_NUMBER,selectedNumber)
                .putExtra(EXTRA_CONTACT_LOOKUP,person.card==null?null:person.card.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        String title=TextUtils.isEmpty(person.title)?selectedNumber:person.title;
        if(TextUtils.isEmpty(title))title="Контакт";
        ShortcutInfo shortcut=new ShortcutInfo.Builder(this,id).setShortLabel(title).setLongLabel(title)
                .setIcon(Icon.createWithResource(this,R.drawable.phone_icon)).setIntent(target).build();
        try{
            if(!manager.requestPinShortcut(shortcut,null))Toast.makeText(this,"Не удалось открыть добавление ярлыка",Toast.LENGTH_LONG).show();
        }catch(RuntimeException unavailable){
            Toast.makeText(this,"Не удалось добавить ярлык на главный экран",Toast.LENGTH_LONG).show();
        }
    }
    private TextView contactQuickAction(String label,int iconId,boolean primary,StationSheet sheet,Runnable click){
        String profile=UiProfiles.id(this);
        int ink=primary?(UiProfiles.LIGHT.equals(profile)||UiProfiles.PEARL.equals(profile)?0xffffffff:BG):UiProfiles.text(this);
        TextView action=text(label,12,ink);
        action.setGravity(Gravity.CENTER);
        action.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        action.setSingleLine(true);
        action.setEllipsize(TextUtils.TruncateAt.END);
        action.setMinHeight(dp(70));
        action.setPadding(dp(3),dp(7),dp(3),dp(7));
        action.setBackground(surface(primary?MINT:CARD,14));
        Drawable glyph=getDrawable(iconId);
        if(glyph!=null){
            glyph=glyph.mutate();
            glyph.setBounds(0,0,dp(19),dp(19));
            glyph.setTint(ink);
            action.setCompoundDrawables(null,glyph,null,null);
            action.setCompoundDrawablePadding(dp(5));
        }
        action.setFocusable(true);
        action.setOnClickListener(v->{sheet.dismiss();click.run();});
        return action;
    }
    private TextView subscriberAction(String label,int iconId,boolean primary,StationSheet sheet,Runnable click){
        int ink=primary?BG:WHITE;
        TextView action=text(label,17,ink);
        action.setGravity(Gravity.CENTER);
        action.setTypeface(Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        action.setMinHeight(dp(48));
        action.setPadding(dp(10),dp(8),dp(10),dp(8));
        action.setBackground(surface(primary?MINT:0xff293a30,16));
        if(iconId!=0){
            Drawable glyph=getDrawable(iconId).mutate();
            glyph.setBounds(0,0,dp(22),dp(22));
            glyph.setTint(ink);
            action.setCompoundDrawables(null,glyph,null,null);
            action.setCompoundDrawablePadding(dp(3));
        }
        action.setFocusable(true);
        action.setOnClickListener(v->{sheet.dismiss();click.run();});
        return action;
    }
    private TextView subscriberRow(LinearLayout panel,String label,boolean prominent,StationSheet sheet,Runnable click){
        TextView row=subscriberAction(label,0,false,sheet,click);
        row.setTextSize(UiProfiles.type(this,15));
        row.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        row.setMinHeight(dp(46));
        row.setPadding(dp(11),dp(8),dp(11),dp(8));
        row.setBackground(surface(CARD,12));
        if(prominent)row.setTextColor(MINT);
        LinearLayout.LayoutParams spacing=new LinearLayout.LayoutParams(-1,-2);
        spacing.topMargin=dp(prominent?6:3);
        panel.addView(row,spacing);
        return row;
    }
    private void card(Entry e){open(new Intent(Intent.ACTION_VIEW,e.card),"Не удалось открыть карточку");}
    private void open(Intent intent,String unavailable){
        try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException exception){Toast.makeText(this,unavailable,Toast.LENGTH_LONG).show();}
    }
    private final class Rows extends BaseAdapter{
        public int getCount(){return visible.size();}
        public Object getItem(int i){return visible.get(i);}
        public long getItemId(int i){return i;}
        public View getView(int i,View recycled,ViewGroup parent){
            Row row=recycled==null?new Row():(Row)recycled;
            Entry e=visible.get(i);
            row.title.setText((e.starred?"★  ":"")+e.title);
            boolean call=e.isCall();
            boolean callable=!e.numbers.isEmpty();
            String number=displayedNumber(e);
            row.avatar.bind(e.title,e.photo);
            row.avatar.setContentDescription("Открыть карточку: "+e.title);
            row.avatar.setOnClickListener(v->subscriber(e));
            row.words.setEnabled(callable);
            row.words.setContentDescription(callable?"Позвонить: "+e.title+", "+number:"Номер скрыт");
            row.words.setOnClickListener(v->{if(callable)callDisplayed(e);else subscriber(e);});
            row.words.setOnLongClickListener(v->{if(callable){android.content.ClipboardManager cm=getSystemService(android.content.ClipboardManager.class);cm.setPrimaryClip(android.content.ClipData.newPlainText("Номер телефона",number));Toast.makeText(ContactsScreen.this,"Номер скопирован",Toast.LENGTH_SHORT).show();}else subscriber(e);return true;});
            row.sms.setVisibility(callable?View.VISIBLE:View.GONE);
            row.sms.setContentDescription(callable?"Написать: "+e.title:null);
            row.sms.setOnClickListener(callable?v->QuickSms.open(ContactsScreen.this,number,e.title):null);
            row.detail.setTextColor(call&&e.missed?MISSED:MUTED);
            if(call){
                Date d=new Date(e.date);
                String clock=android.text.format.DateFormat.getTimeFormat(ContactsScreen.this).format(d);
                String direction=e.missed?"Пропущен":e.direction==2?"↗":"↙";
                String line="SIM".equals(e.route)?"SIM":"Город";
                String groupKey=e.card!=null?"contact:"+e.card.toString():e.journalKey;
                int calls=callGroupCounts.containsKey(groupKey)?callGroupCounts.get(groupKey):1;
                row.detail.setText(calls>1?calls+" вызовов  ·  "+direction+"  "+clock+"  ·  "+line:direction+"   "+clock+"  ·  "+line);
                row.detail.setContentDescription(clock+", "+e.detail);
                boolean firstCall=i==0||!visible.get(i-1).isCall();
                boolean newDay=firstCall||!sameDay(visible.get(i-1).date,e.date);
                row.group.setVisibility(newDay?View.VISIBLE:View.GONE);
                if(newDay)row.group.setText((firstCall&&displayedTerm!=null?"Звонки  ·  ":"")+dayLabel(e.date));
            }else{
                row.detail.setText(number+(e.numbers.size()>1?"  ·  ещё "+(e.numbers.size()-1):""));
                row.detail.setContentDescription(null);
                row.group.setText("Контакты");
                row.group.setVisibility(i==0&&displayedTerm!=null?View.VISIBLE:View.GONE);
            }
            return row;
        }
    }
    private boolean sameDay(long a,long b){
        Calendar ca=Calendar.getInstance(),cb=Calendar.getInstance();ca.setTimeInMillis(a);cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR)==cb.get(Calendar.YEAR)&&ca.get(Calendar.DAY_OF_YEAR)==cb.get(Calendar.DAY_OF_YEAR);
    }
    private String dayLabel(long time){
        Calendar now=Calendar.getInstance();if(sameDay(now.getTimeInMillis(),time))return "Сегодня";
        now.add(Calendar.DAY_OF_YEAR,-1);if(sameDay(now.getTimeInMillis(),time))return "Вчера";
        return new java.text.SimpleDateFormat("d MMMM yyyy",new Locale("ru")).format(new Date(time));
    }
    private final class Row extends LinearLayout{
        final TextView title,detail,group;
        final ImageView sms;
        final ContactAvatar avatar;
        final LinearLayout words;
        Row(){
            super(ContactsScreen.this);setOrientation(VERTICAL);
            group=text("",13,MINT);group.setTypeface(Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));group.setPadding(0,dp(12),0,dp(4));addView(group,-1,-2);
            LinearLayout fields=line();fields.setMinimumHeight(dp(72));addView(fields,-1,-2);
            fields.setPadding(0,dp(7),0,dp(7));
            setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            avatar=new ContactAvatar(ContactsScreen.this,48);avatar.setFocusable(true);
            fields.addView(avatar,dp(48),dp(48));
            words=new LinearLayout(ContactsScreen.this);words.setOrientation(VERTICAL);
            words.setGravity(Gravity.CENTER_VERTICAL);words.setMinimumHeight(dp(56));
            words.setPadding(dp(12),dp(4),dp(6),dp(4));words.setFocusable(true);
            words.setBackground(surface(0x001b2820,12));fields.addView(words,weight());
            title=text("",18,WHITE);title.setTypeface(Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
            if(android.os.Build.VERSION.SDK_INT>=29)title.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE);words.addView(title);
            detail=text("",14,MUTED);words.addView(detail);
            sms=icon(R.drawable.dir_sms,"Написать SMS",null);
            sms.setFocusable(true);sms.setPadding(dp(12),dp(12),dp(12),dp(12));
            sms.setColorFilter(BLUE);sms.setBackground(surface(0xff1e302a,20));
            fields.addView(sms,dp(48),dp(48));
            View rule=new View(ContactsScreen.this);rule.setBackgroundColor(0xff253138);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.leftMargin=dp(56);addView(rule,lp);
        }
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout line(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(UiProfiles.type(this,sp));
        t.setTextColor(color==WHITE?UiProfiles.text(this):color==MUTED?UiProfiles.muted(this):color==MINT?UiProfiles.accent(this):color==BLUE?UiProfiles.secondary(this):color);return t;}
    private ImageView icon(int resource,String description,View.OnClickListener click){
        ImageView v=new ImageView(this);v.setImageResource(resource);v.setContentDescription(description);v.setColorFilter(UiProfiles.text(this));
        v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setBackground(surface(CARD,18));v.setOnClickListener(click);return v;
    }
    private Drawable surface(int color,int radius){
        return new RippleDrawable(ColorStateList.valueOf(UiProfiles.opacity(UiProfiles.accent(this),24)),background(color,radius),null);
    }
    private TextView chip(String s,int sp,String desc){
        TextView t=text(s,sp,MINT);t.setGravity(Gravity.CENTER);t.setBackground(surface(CARD,14));
        if(!desc.isEmpty())t.setContentDescription(desc);return t;
    }
    private GradientDrawable background(int color,int radius){GradientDrawable b=new GradientDrawable();
        b.setColor(color==CARD||color==0xff1b2d28?UiProfiles.panel(this):color==MINT?UiProfiles.accent(this):color);
        b.setCornerRadius(dp(UiProfiles.corner(this,radius)));return b;}
}
