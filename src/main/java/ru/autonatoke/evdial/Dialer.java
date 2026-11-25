package ru.autonatoke.evdial;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.linphone.core.*;

public class Dialer extends Activity {
 static final int BG=0xff0b100e,CARD=0xff14232c,FG=0xfff4fcff,MUTED=0xffc0d7de,GREEN=0xff79f4a6;
 Phone phone;
 LinearLayout body,tools,keypad,navigation,header,idleCard,idleActions,callInfo,batterySlot,dialPane,routeSelector;
 ScrollView contentScroll;
 StationSheet servicesDialog,routeDialog;
 TextView callPhase,callIdentity,callAddress,micControl,speakerControl,keysControl;
 BatteryCallControl callBattery;
 Call shownCall,awaitingMic;
 boolean sipKeysOpen, batteryRinging;
 EditText number;
 TextView action,erase,numberMenu,replyButton,routeLabel;
 LinearLayout smartPanel,smartPrimary;
 TextView smartTitle,smartNumber,smartMore,smartInfo;
 private volatile java.util.List<SmartDialSearch.Entry> smartEntries=java.util.Collections.emptyList();
 private SmartDialSearch.Matches smartMatches;
 private android.app.Dialog smartDialog;
 private final java.util.concurrent.ExecutorService smartWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
 private final android.os.Handler smartUi=new android.os.Handler(android.os.Looper.getMainLooper());
 private Runnable smartPending;
 private int smartQueryGeneration,smartLoadGeneration;
 private boolean smartLoaded,smartResumed;
 IncomingReply incomingReply;
 SpeedDial speedDial;
 String pending,lookupNumber="",knownName="";
 boolean sim, directoryAtHome, directoryVisible;
 String shownProfile;
 String shownStyle;
 Intent pendingContactShortcut;

 // Keep SIP opt-in separate from inspecting display settings for either line.
 private static final int DISPLAY_SETTINGS=16,SIP_DISPLAY_SETTINGS=14,DISPLAY_PERMISSION=9;

 static final String[] START_TABS={"recent","dial","contacts"};
 static final String[] START_NAMES={"Недавние","Набор","Контакты"};

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  shownProfile=UiProfiles.id(this);
  shownStyle=UiProfiles.signature(this);
  phone=(Phone)getApplication();
  incomingReply=new IncomingReply(this);
  speedDial=new SpeedDial(this);
  if(state!=null)speedDial.picking=state.getInt("pickingSpeedKey",0);
  getWindow().setStatusBarColor(UiProfiles.active(this).base);
  getWindow().setNavigationBarColor(UiProfiles.active(this).base);
  setVolumeControlStream(android.media.AudioManager.STREAM_VOICE_CALL);
  DialerViewport root=new DialerViewport(this);
  root.setId(android.R.id.custom);DriveTools.background(this,root);
  setContentView(root);

  body=new LinearLayout(this);
  body.setOrientation(LinearLayout.VERTICAL);
  body.setGravity(Gravity.BOTTOM);
  boolean largeType=getResources().getConfiguration().fontScale>=1.3f;
  boolean compact=largeType||getResources().getConfiguration().screenHeightDp<660;
  body.setPadding(dp(12),0,dp(12),0);
  header=sectionHeader(this,root);
  sim="sim".equals(getPreferences(0).getString("route","sim"));
  buildRouteSelector();
  headerAction(R.drawable.driver_car,"Сервисы EV",this::showServices);
  headerAction(R.drawable.dir_search,"Найти",()->openDirectory(true,false,number.getText().toString()));
  headerAction(R.drawable.ev_settings,"Настройки",this::configure);
  header.setMinimumHeight(dp(48));
  header.setPadding(dp(8),0,dp(8),0);
  LinearLayout.LayoutParams headerSize=new LinearLayout.LayoutParams(-1,dp(48));
  headerSize.setMargins(dp(12),dp(4),dp(12),dp(4));header.setLayoutParams(headerSize);
  header.setBackground(new ElectricUi.Banner(this));
  contentScroll=new ScrollView(this);
  contentScroll.setFillViewport(true);contentScroll.setVerticalScrollBarEnabled(false);
  root.addView(contentScroll,new LinearLayout.LayoutParams(-1,0,1));
  contentScroll.addView(body);
  dialPane=new LinearLayout(this);dialPane.setOrientation(LinearLayout.VERTICAL);
  dialPane.setPadding(dp(12),0,dp(12),0);
  root.addView(dialPane,new LinearLayout.LayoutParams(-1,-2));

  idleCard=new LinearLayout(this);
  idleCard.setOrientation(LinearLayout.VERTICAL);
  idleCard.setPadding(dp(4),dp(2),dp(4),dp(2));
  idleCard.setBackground(glass(UiProfiles.panel(this),UiProfiles.opacity(UiProfiles.active(this).base,UiProfiles.value(this,UiProfiles.PANEL)),UiProfiles.secondary(this),22));
  dialPane.addView(idleCard,box(-1,52));
  number=new EditText(this){
   @Override public boolean requestRectangleOnScreen(android.graphics.Rect rectangle,boolean immediate){
    // The caret may scroll inside the field, but must not pull the dialpad offscreen.
    return isAccessibilityFocused()&&super.requestRectangleOnScreen(rectangle,immediate);
   }
   @Override public boolean performAccessibilityAction(int action,Bundle arguments){
    if(action==android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId()){
     android.graphics.Rect rectangle=new android.graphics.Rect();
     getDrawingRect(rectangle);
     return super.requestRectangleOnScreen(rectangle,true);
    }
    return super.performAccessibilityAction(action,arguments);
   }
  };
  number.setInputType(InputType.TYPE_CLASS_PHONE);
  number.setSingleLine();
  number.setGravity(Gravity.CENTER);
  number.setTextSize(UiProfiles.type(this,compact?27:32));
  number.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  number.setTextColor(UiProfiles.text(this));
  number.setHintTextColor(UiProfiles.muted(this));
  number.setBackgroundColor(android.graphics.Color.TRANSPARENT);
  number.setHint("Номер телефона");
  number.setShowSoftInputOnFocus(false);
  number.setPadding(0,dp(5),0,dp(3));
  number.setMinHeight(dp(48));
  LinearLayout numberField=new LinearLayout(this);
  numberField.setBaselineAligned(false);
  numberField.setGravity(Gravity.CENTER_VERTICAL);
  // Width fitting changes the glyph size, never the height of the idle screen.
  int numberHeight=dp(48);
  numberField.addView(number,new LinearLayout.LayoutParams(0,-1,1));
   TextView inlineErase=label("⌫",26,FG);
   inlineErase.setGravity(Gravity.CENTER);
   fitSingleLine(inlineErase,16);
   inlineErase.setContentDescription("Удалить цифру");
   inlineErase.setOnClickListener(v->{
     int a=Math.max(0,number.getSelectionStart()),b=Math.max(0,number.getSelectionEnd());
     number.getText().delete(a==b?Math.max(0,a-1):Math.min(a,b),Math.max(a,b));
   });
   inlineErase.setOnLongClickListener(v->{number.setText("");return true;});
   numberField.addView(inlineErase,box(48,48));

  numberMenu=label("⋮",26,FG);
  numberMenu.setGravity(Gravity.CENTER);
  fitSingleLine(numberMenu,16);
  numberMenu.setContentDescription("Действия с номером");
  numberMenu.setBackground(ElectricUi.buttonSurface(this,18));
  numberMenu.setVisibility(View.INVISIBLE);
  numberMenu.setOnClickListener(v->showNumberMenu());
  numberField.addView(numberMenu,box(48,48));
  idleCard.addView(numberField,new LinearLayout.LayoutParams(-1,numberHeight));
  if(state!=null)number.setText(state.getString("number",""));
  else if(phone.active!=null)number.setText(phone.active.getRemoteAddress().getUsername());
  if(state==null&&phone.active==null)importNumber(getIntent());
  number.setSelection(number.length());
  buildSmartSuggestions();

  callInfo=new LinearLayout(this);
  callInfo.setOrientation(LinearLayout.VERTICAL);
  callInfo.setGravity(Gravity.CENTER);
  body.addView(callInfo,box(-1,-2));
  TextView lineName=label(UiProfiles.active(this).label+"   /   SIP",14,UiProfiles.accent(this));
  lineName.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  lineName.setLetterSpacing(.11f);
  callInfo.addView(lineName);
  callPhase=label("",17,MUTED);
  callPhase.setPadding(0,dp(8),0,dp(12));callInfo.addView(callPhase);
  callIdentity=label("",compact?26:30,FG);
  callIdentity.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  callIdentity.setGravity(Gravity.CENTER);callInfo.addView(callIdentity,box(-1,-2));
  callAddress=label("",17,MUTED);callAddress.setGravity(Gravity.CENTER);
  callAddress.setPadding(0,dp(4),0,0);callInfo.addView(callAddress,box(-1,-2));

  tools=row();
  tools.setPadding(0,dp(12),0,dp(4));
  micControl=callTool("Микрофон",R.drawable.sim_mic);
  speakerControl=callTool("Динамик",R.drawable.sim_speaker);
  keysControl=callTool("Клавиши",R.drawable.ic_keypad);
  micControl.setOnClickListener(v->{
   if(shownCall==null||phone.active!=shownCall||phone.ringing())return;
   phone.sipCore().setMicEnabled(!phone.sipCore().isMicEnabled());refresh();
  });
  speakerControl.setOnClickListener(v->switchSipSpeaker());
  keysControl.setOnClickListener(v->{
   if(shownCall==null||phone.active!=shownCall||phone.ringing())return;
   sipKeysOpen=!sipKeysOpen;refresh();
  });
  keypad=new LinearLayout(this);
  keypad.setOrientation(LinearLayout.VERTICAL);
  keypad.setPadding(0,dp(2),0,dp(2));
  dialPane.addView(keypad,box(-1,-2));
  String chars="123456789*0#";
  for(int r=0;r<4;r++){
   LinearLayout line=new LinearLayout(this);
   line.setGravity(Gravity.CENTER);
   keypad.addView(line,box(-1,-2));
   for(int col=0;col<3;col++){
    int i=r*3+col;
    char ch=chars.charAt(i);
    LinearLayout slot=new LinearLayout(this);
    slot.setGravity(Gravity.CENTER);
    DialpadKey key=new DialpadKey(this,ch,UiProfiles.type(this,compact?28:32),UiProfiles.text(this));
    int circle=compact?72:80;
    GradientDrawable round=new GradientDrawable();
    round.setShape(GradientDrawable.OVAL);
    round.setColor(UiProfiles.keypad(this));
    round.setStroke(dp(1),UiProfiles.opacity(UiProfiles.secondary(this),45));
    key.setBackground(new RippleDrawable(ColorStateList.valueOf(UiProfiles.opacity(UiProfiles.accent(this),25)),round,null));
    key.setContentDescription(ch=='0'?"0. Удерживайте для плюса":String.valueOf(ch));
    key.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN)v.setAlpha(.76f);else if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)v.setAlpha(1f);return false;});
    key.setOnClickListener(v->{
     if(phone.active==null)insert(""+ch);
     else if(sipKeysOpen&&phone.active==shownCall&&!phone.ringing())shownCall.sendDtmf(ch);
    });
    if(ch=='0')key.setOnLongClickListener(v->{if(phone.active==null)insert("+");return true;});
    if(ch>='2'&&ch<='9')key.setOnLongClickListener(v->{
     if(callBlocksNumberActions())return false;
     speedDial.press(ch-'0');return true;
    });
    slot.addView(key,box(circle,circle));
    LinearLayout.LayoutParams keySlot=flex(-2);
    keySlot.topMargin=dp(2);keySlot.bottomMargin=dp(2);
    line.addView(slot,keySlot);
   }
  }
  idleActions=new LinearLayout(this);
  idleActions.setBaselineAligned(false);
  idleActions.setGravity(Gravity.CENTER_VERTICAL);
  idleActions.setPadding(dp(3),dp(2),dp(3),dp(2));
  dialPane.addView(idleActions,box(-1,52));
  action=button("Позвонить",22,UiProfiles.accent(this),UiProfiles.active(this).base);
  action.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
  action.setMinHeight(dp(48));action.setPadding(dp(6),dp(4),dp(6),dp(4));
  action.setSingleLine(true);
  action.setHorizontallyScrolling(false);
  int actionHeight=dp(48);
  action.setAutoSizeTextTypeUniformWithConfiguration(dp(12),Math.max(dp(12),Math.round(action.getTextSize())),1,
          android.util.TypedValue.COMPLEX_UNIT_PX);
  idleActions.addView(action,new LinearLayout.LayoutParams(0,actionHeight,1));
  erase=button("",20,CARD,FG);
  icon(erase,R.drawable.ev_erase,FG,23);
  erase.setPadding(dp(17),0,0,0);
  erase.setBackground(glass(UiProfiles.button(this),UiProfiles.navigation(this),UiProfiles.secondary(this),17));
  erase.setContentDescription("Удалить цифру");
  idleActions.addView(erase,box(52,48));
  erase.setOnClickListener(v->{
   int a=Math.max(0,number.getSelectionStart()),b=Math.max(0,number.getSelectionEnd());
   number.getText().delete(a==b?Math.max(0,a-1):Math.min(a,b),Math.max(a,b));
  });
  erase.setOnLongClickListener(v->{number.setText("");return true;});
  action.setOnClickListener(v->startOutgoing());
  batterySlot=new LinearLayout(this);
  batterySlot.setPadding(0,dp(14),0,0);
  body.addView(batterySlot,box(-1,-2));
  replyButton=button("Ответить SMS",15,0xb81c3026,GREEN);
  replyButton.setMinHeight(dp(48));replyButton.setPadding(dp(14),dp(6),dp(14),dp(6));
  body.addView(replyButton,box(-2,-2));
  ((LinearLayout.LayoutParams)replyButton.getLayoutParams()).gravity=Gravity.CENTER_HORIZONTAL;
  replyButton.setOnClickListener(v->{if(phone.active==shownCall&&phone.ringing())incomingReply.openSip(phone,shownCall);});

  final int numberSp=UiProfiles.type(this,compact?27:32);
  number.addOnLayoutChangeListener((view,left,topEdge,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
   if(right-left!=oldRight-oldLeft||bottom-topEdge!=oldBottom-oldTop)fitNumber(numberSp);
  });
  number.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){
    fitNumber(numberSp);
    if(phone.active==null)updateCallLabel(sim,phone.ready());
    updateNumberMenu();
    if(phone.active==null&&!s.toString().equals(lookupNumber)){knownName="";lookupNumber="";}
    scheduleSmartSearch();
   }
   public void afterTextChanged(android.text.Editable e){}
  });
  navigation=tabs(this,root,"dial",tab->{
   if("dial".equals(tab)){number.requestFocus();}
   else openDirectory("recent".equals(tab));
  });
  fitDialerNavigation();
  root.bind(header,contentScroll,dialPane,keypad,idleCard,idleActions,navigation);
  refresh();
  directoryAtHome=state!=null&&state.getBoolean("directoryHome");
  directoryVisible=state!=null&&state.getBoolean("directoryOpen");
  if(state==null&&ContactsScreen.ACTION_OPEN_CONTACT.equals(getIntent().getAction())){
   openContactShortcut(getIntent());return;
  }
  if(state==null && Intent.ACTION_MAIN.equals(getIntent().getAction()) && getIntent().getData()==null){
   getWindow().getDecorView().post(()->{
    if(isFinishing()||isDestroyed()||isDefaultPhone()||getPreferences(0).getBoolean("role_explained",false))return;
    getPreferences(0).edit().putBoolean("role_explained",true).apply();
    new StationSheet.Builder(this).setTitle("EVDial · Телефон").setMessage("Для SIM-вызовов нужно назначить EVDial телефоном по умолчанию через системное подтверждение Android.")
     .setPositiveButton("Выбрать",(dialog,which)->defaultPhone()).setNegativeButton("Позже",null).show();
   });
  }
  if(state==null && Intent.ACTION_MAIN.equals(getIntent().getAction()) && getIntent().getData()==null
     && phone.active==null && (MobileCalls.live==null||MobileCalls.live.current()==null)){
   String first=startTab();
   if(!"dial".equals(first))openDirectory("recent".equals(first),true);
  }
 }
 private void buildRouteSelector(){
  int index=header.getChildCount()-1;
  TextView brand=(TextView)header.getChildAt(index);
  header.removeView(brand);
  routeSelector=new LinearLayout(this);routeSelector.setOrientation(LinearLayout.VERTICAL);
  routeSelector.setGravity(Gravity.CENTER_VERTICAL);
  routeSelector.setMinimumWidth(dp(48));routeSelector.setMinimumHeight(dp(48));
  routeSelector.setBackground(pressable(android.graphics.Color.TRANSPARENT,14));
  routeSelector.setFocusable(true);
  brand.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  routeSelector.addView(brand,box(-1,24));
  routeLabel=label("",12,UiProfiles.accent(this));
  routeLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  routeLabel.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
  fitSingleLine(routeLabel,10);
  routeSelector.addView(routeLabel,box(-1,24));
  routeSelector.setOnClickListener(v->showRoutePicker());
  header.addView(routeSelector,index,new LinearLayout.LayoutParams(0,dp(48),1));
 }
 private void showRoutePicker(){
  if(callBlocksNumberActions())return;
  dismissHeaderPanels();
  routeDialog=new StationSheet.Builder(this).setTitle("Линия для звонков")
    .setMessage(phone.ready()?"SIP подключён":"SIP не подключён. SIM работает через сотового оператора.")
    .setSingleChoiceItems(new String[]{"SIM · Сотовая связь","SIP · Интернет-телефония"},sim?0:1,
            (dialog,which)->{if(!callBlocksNumberActions())route(which==0);})
    .setPositiveButton("Настроить SIP",(dialog,which)->{if(!callBlocksNumberActions())configureSip();})
    .setNegativeButton("Закрыть",null).create();
  routeDialog.setOnDismissListener(dialog->{if(routeDialog==dialog)routeDialog=null;});
  routeDialog.show();
 }
 private void showServices(){
  if(callBlocksNumberActions())return;
  dismissHeaderPanels();
  // Services live in their own scrolling sheet, never in the dialer's layout.
  LinearLayout links=ElectricUi.shortcuts(this);
  links.setOrientation(LinearLayout.VERTICAL);
  for(int i=0;i<links.getChildCount();i++){
   LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);
   size.topMargin=dp(4);size.bottomMargin=dp(4);
   links.getChildAt(i).setLayoutParams(size);
  }
  servicesDialog=new StationSheet.Builder(this).setTitle("Сервисы EV")
          .setView(links).setNegativeButton("Закрыть",null).create();
  servicesDialog.setOnDismissListener(dialog->{if(servicesDialog==dialog)servicesDialog=null;});
  servicesDialog.show();
 }
 private void dismissHeaderPanels(){
  StationSheet services=servicesDialog,lines=routeDialog;
  servicesDialog=null;routeDialog=null;
  if(services!=null)services.dismiss();
  if(lines!=null)lines.dismiss();
 }
 private void fitSingleLine(TextView view,int minimumDp){
  int preferred=Math.max(dp(minimumDp)+1,Math.round(view.getTextSize()));
  view.setSingleLine(true);view.setHorizontallyScrolling(false);
  view.setEllipsize(android.text.TextUtils.TruncateAt.END);
  view.setAutoSizeTextTypeUniformWithConfiguration(dp(minimumDp),preferred,1,
          android.util.TypedValue.COMPLEX_UNIT_PX);
 }
 private void fitDialerNavigation(){
  navigation.setPadding(dp(4),dp(2),dp(4),dp(2));
  navigation.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(52)));
  for(int i=0;i<navigation.getChildCount();i++){
   LinearLayout tab=(LinearLayout)navigation.getChildAt(i);
   tab.setMinimumHeight(dp(48));tab.setPadding(dp(2),dp(2),dp(2),dp(2));
   tab.getLayoutParams().height=dp(48);
   View icon=tab.getChildAt(0);icon.getLayoutParams().width=dp(18);icon.getLayoutParams().height=dp(18);
   TextView caption=(TextView)tab.getChildAt(1);caption.getLayoutParams().height=dp(22);
   fitSingleLine(caption,10);
  }
 }
 private void buildSmartSuggestions(){
  smartPanel=new LinearLayout(this);
  smartPanel.setOrientation(LinearLayout.HORIZONTAL);
  smartPanel.setBaselineAligned(false);
  smartPanel.setGravity(Gravity.CENTER_VERTICAL);
  smartPanel.setPadding(dp(8),dp(4),dp(8),dp(4));
  smartPanel.setBackground(shape(UiProfiles.panel(this),16));
  smartPanel.setVisibility(View.INVISIBLE);
  LinearLayout.LayoutParams panelSize=box(-1,64);
  panelSize.topMargin=dp(2);panelSize.bottomMargin=dp(2);
  body.addView(smartPanel,panelSize);
  smartPrimary=new LinearLayout(this);
  smartPrimary.setBaselineAligned(false);
  smartPrimary.setGravity(Gravity.CENTER_VERTICAL);
  smartPrimary.setMinimumHeight(dp(48));
  smartPrimary.setPadding(dp(4),0,0,0);
  smartPanel.addView(smartPrimary,new LinearLayout.LayoutParams(0,-1,1));
  LinearLayout infoText=new LinearLayout(this);
  infoText.setOrientation(LinearLayout.VERTICAL);
  infoText.setGravity(Gravity.CENTER_VERTICAL);
  smartTitle=label("",15,FG);
  smartTitle.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  fitSingleLine(smartTitle,12);
  smartNumber=label("",12,MUTED);
  fitSingleLine(smartNumber,10);
  infoText.addView(smartTitle,box(-1,28));
  infoText.addView(smartNumber,box(-1,22));
  smartPrimary.addView(infoText,new LinearLayout.LayoutParams(0,-2,1));
  smartInfo=label("ⓘ",23,UiProfiles.secondary(this));
  smartInfo.setGravity(Gravity.CENTER);
  fitSingleLine(smartInfo,14);
  smartInfo.setContentDescription("Открыть карточку контакта");
  smartPrimary.addView(smartInfo,box(48,48));
  smartMore=label("",13,UiProfiles.secondary(this));
  smartMore.setGravity(Gravity.CENTER);
  smartMore.setPadding(dp(3),0,dp(3),0);
  smartMore.setMinHeight(dp(48));
  smartMore.setLines(2);smartMore.setHorizontallyScrolling(false);
  smartMore.setAutoSizeTextTypeUniformWithConfiguration(dp(10),Math.max(dp(10)+1,Math.round(smartMore.getTextSize())),1,
          android.util.TypedValue.COMPLEX_UNIT_PX);
  // A fixed adjacent action replaces the extra line that used to push keys down.
  smartPanel.addView(smartMore,box(64,-1));
  clearSmartSuggestions();
 }
 private void clearSmartSuggestions(){
  smartMatches=null;
  if(smartPanel==null)return;
  // Invisible reserves the slot; removing listeners also prevents stale programmatic clicks.
  smartPanel.setVisibility(phone.active!=null?View.GONE:View.INVISIBLE);
  smartPrimary.setOnClickListener(null);smartPrimary.setClickable(false);smartPrimary.setEnabled(false);
  smartInfo.setOnClickListener(null);smartInfo.setClickable(false);smartInfo.setEnabled(false);
  smartMore.setOnClickListener(null);smartMore.setClickable(false);smartMore.setEnabled(false);
 }
 private void loadSmartIndex(){
  if(smartWorker.isShutdown())return;
  final int ticket=++smartLoadGeneration;
  smartWorker.execute(()->{
   java.util.List<SmartDialSearch.Entry> fresh=SmartDialIndex.load(getApplicationContext());
   runOnUiThread(()->{
    if(ticket!=smartLoadGeneration||isFinishing()||isDestroyed())return;
    smartEntries=fresh;smartLoaded=true;
    scheduleSmartSearch();
   });
  });
 }
 private void scheduleSmartSearch(){
  if(smartPanel==null||number==null)return;
  // Keep the keypad stationary without letting an old result dial during filtering.
  clearSmartSuggestions();
  if(smartPending!=null)smartUi.removeCallbacks(smartPending);
  final int ticket=++smartQueryGeneration;
  if(!smartResumed)return;
  final String query=number.getText().toString();
  smartPending=()->{
   if(!smartLoaded||smartWorker.isShutdown())return;
   final java.util.List<SmartDialSearch.Entry> snapshot=smartEntries;
   smartWorker.execute(()->{
    SmartDialSearch.Matches results=SmartDialSearch.search(query,snapshot);
    runOnUiThread(()->{
     if(!smartResumed||ticket!=smartQueryGeneration||isFinishing()||isDestroyed()||!number.getText().toString().equals(query))return;
     smartMatches=results;
     renderSmartSuggestions(results);
    });
   });
  };
  smartUi.postDelayed(smartPending,95);
 }
 private static String smartCountLabel(int count){
  int n=Math.abs(count);
  int tail=n%100;
  String word=tail>=11&&tail<=14?"результатов":n%10==1?"результат":n%10>=2&&n%10<=4?"результата":"результатов";
  return count+" "+word;
 }
 private void renderSmartSuggestions(SmartDialSearch.Matches results){
  if(smartPanel==null)return;
  // Bind even during a call so returning to idle never reveals disabled, stale actions.
  smartPanel.setVisibility(phone.active!=null?View.GONE:View.VISIBLE);
  smartPrimary.setEnabled(true);
  smartInfo.setEnabled(results.total>0);
  smartMore.setEnabled(results.isRecent&&results.total>0||results.total>1);
  if(results.total==0){
   smartTitle.setText(results.isRecent?"Недавние звонки":"Совпадений не найдено");
   smartNumber.setText(results.isRecent?"История SIM и SIP":"Попробуйте другой номер или имя");
   smartPrimary.setContentDescription(results.isRecent?"Открыть последние звонки":"Поиск в адресной книге");
   smartInfo.setVisibility(View.INVISIBLE);
   smartPrimary.setOnClickListener(v->openDirectory(results.isRecent,false,results.isRecent?null:number.getText().toString()));
   smartMore.setVisibility(View.INVISIBLE);
   return;
  }
  SmartDialSearch.Entry first=results.items.get(0).entry;
  smartTitle.setText(results.isRecent?"Последний · "+first.name:first.name);
  smartNumber.setText(first.number);
  smartPrimary.setContentDescription(first.name+", "+first.number+". Позвонить");
  smartPrimary.setOnClickListener(v->applySmartSelection(first,true));
  smartInfo.setVisibility(View.VISIBLE);
  smartInfo.setContentDescription("Открыть карточку "+first.name);
  smartInfo.setOnClickListener(v->openSmartContact(first));
  if(results.isRecent){
   smartMore.setText("Все\nвызовы");
   smartMore.setContentDescription("Все недавние вызовы");
   smartMore.setVisibility(View.VISIBLE);
   smartMore.setOnClickListener(v->openDirectory(true));
  }else if(results.total>1){
   smartMore.setText("Ещё\n"+(results.total-1));
   smartMore.setContentDescription("Ещё "+smartCountLabel(results.total-1));
   smartMore.setVisibility(View.VISIBLE);
   smartMore.setOnClickListener(v->showSmartMatches(results));
  }else{
   smartMore.setVisibility(View.INVISIBLE);
  }
 }
 private void applySmartSelection(SmartDialSearch.Entry candidate,boolean call){
  if(candidate==null||callBlocksNumberActions())return;
  number.setText(candidate.number);
  number.setSelection(number.length());
  lookupName(candidate.number);
  if(call)startOutgoing();
 }
 private void openSmartContact(SmartDialSearch.Entry candidate){
  if(candidate==null||callBlocksNumberActions())return;
  if(candidate.contactUri.isEmpty()){
   openDirectory(true,false,candidate.number);
   return;
  }
  Intent i=new Intent(this,ContactsScreen.class);
  i.putExtra(ContactsScreen.EXTRA_CONTACT_NUMBER,candidate.number);
  i.putExtra(ContactsScreen.EXTRA_CONTACT_LOOKUP,candidate.contactUri);
  directoryAtHome=false;directoryVisible=true;
  startActivityForResult(i,21);
 }
 private void dismissSmartMatches(){
  android.app.Dialog dialog=smartDialog;
  smartDialog=null;
  if(dialog!=null)dialog.dismiss();
 }
 private void showSmartMatches(SmartDialSearch.Matches results){
  if(results==null||results.items.isEmpty()||callBlocksNumberActions())return;
  dismissSmartMatches();
  android.app.Dialog dialog=new android.app.Dialog(this);
  smartDialog=dialog;
  dialog.setOnDismissListener(ignored->{if(smartDialog==dialog)smartDialog=null;});
  dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
  LinearLayout panel=new LinearLayout(this);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(dp(14),dp(10),dp(14),dp(10));
  panel.setBackground(shape(UiProfiles.panel(this),20));
  TextView title=label("Совпадения · "+results.total,19,FG);
  title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  title.setPadding(dp(5),dp(8),dp(5),dp(12));
  panel.addView(title,box(-1,-2));
  ListView list=new ListView(this);
  list.setDividerHeight(0);
  panel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  list.setAdapter(new BaseAdapter(){
   public int getCount(){return results.items.size();}
   public Object getItem(int position){return results.items.get(position);}
   public long getItemId(int position){return position;}
   public View getView(int position,View recycled,android.view.ViewGroup parent){
    LinearLayout row;
    if(recycled instanceof LinearLayout && ((LinearLayout)recycled).getChildCount()==2){
     row=(LinearLayout)recycled;
    }else{
     row=new LinearLayout(Dialer.this);
     row.setGravity(Gravity.CENTER_VERTICAL);
     row.setMinimumHeight(dp(62));
     row.setPadding(dp(8),dp(5),dp(4),dp(5));
     LinearLayout texts=new LinearLayout(Dialer.this);
     texts.setOrientation(LinearLayout.VERTICAL);
     texts.addView(label("",15,FG),box(-1,-2));
     texts.addView(label("",12,MUTED),box(-1,-2));
     row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
     TextView open=label("ⓘ",21,UiProfiles.secondary(Dialer.this));
     open.setGravity(Gravity.CENTER);
     row.addView(open,box(48,48));
    }
    SmartDialSearch.Entry item=results.items.get(position).entry;
    LinearLayout texts=(LinearLayout)row.getChildAt(0);
    ((TextView)texts.getChildAt(0)).setText(item.name);
    ((TextView)texts.getChildAt(1)).setText(item.number);
    TextView open=(TextView)row.getChildAt(1);
    open.setContentDescription("Карточка "+item.name);
    open.setOnClickListener(v->{dialog.dismiss();openSmartContact(item);});
    row.setContentDescription(item.name+", "+item.number+". Позвонить");
    return row;
   }
  });
  list.setOnItemClickListener((parent,view,position,id)->{
   SmartDialSearch.Entry entry=results.items.get(position).entry;
   dialog.dismiss();
   applySmartSelection(entry,true);
  });
  TextView close=label("Закрыть",15,UiProfiles.secondary(this));
  close.setGravity(Gravity.CENTER);
  close.setMinHeight(dp(44));
  close.setOnClickListener(v->dialog.dismiss());
  panel.addView(close,box(-1,-2));
  dialog.setContentView(panel);
  dialog.show();
  android.view.Window window=dialog.getWindow();
  if(window!=null){
   window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
   window.setGravity(Gravity.BOTTOM);
   window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,dp(550)),
          (int)(getResources().getDisplayMetrics().heightPixels*.72f));
  }
 }
 private void fitNumber(int preferredSp){
  int width=number.getWidth()-number.getCompoundPaddingLeft()-number.getCompoundPaddingRight();
  if(width<=0)return;
  android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
  float preferred=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,preferredSp,metrics);
  android.text.TextPaint measure=new android.text.TextPaint(number.getPaint());measure.setTextSize(preferred);
  String displayed=number.length()==0?number.getHint().toString():number.getText().toString();
  float needed=measure.measureText(displayed);float available=Math.max(1,width-dp(5));
  float size=needed>available?Math.max(14*metrics.density,preferred*available/needed):preferred;
  android.graphics.Paint.FontMetrics font=measure.getFontMetrics();
  int height=number.getHeight()-number.getCompoundPaddingTop()-number.getCompoundPaddingBottom();
  if(height>0)size=Math.min(size,preferred*height/Math.max(1,font.bottom-font.top));
  if(Math.abs(number.getTextSize()-size)>.25f)number.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);
 }
 private String startTab(){return getPreferences(0).getString("startTab","dial");}
 static LinearLayout sectionHeader(Activity activity,LinearLayout parent){
  int unit=Math.round(activity.getResources().getDisplayMetrics().density*48);
  LinearLayout top=new LinearLayout(activity);
  top.setGravity(Gravity.CENTER_VERTICAL);
  top.setMinimumHeight(unit);
  parent.addView(top,-1,-2);
  DriverBadge.add(activity,top);
  return top;
 }
 interface TabClick{void select(String tab);}
 static LinearLayout tabs(Activity activity,LinearLayout parent,String current,TabClick listener){
  final float d=activity.getResources().getDisplayMetrics().density;
  LinearLayout nav=new LinearLayout(activity);
  nav.setGravity(Gravity.CENTER_VERTICAL);
  nav.setBackgroundColor(UiProfiles.navigation(activity));
  nav.setPadding(Math.round(d*4),Math.round(d*3),Math.round(d*4),Math.round(d*3));
  parent.addView(nav,-1,-2);
  String[] titles={"Недавние","Набор","Контакты"};String[] pages={"recent","dial","contacts"};
  int[] symbols={R.drawable.ic_recents,R.drawable.ic_keypad,R.drawable.ic_people};
  for(int i=0;i<pages.length;i++){
   final String target=pages[i];boolean active=target.equals(current);
   LinearLayout tab=new LinearLayout(activity);tab.setOrientation(LinearLayout.VERTICAL);tab.setGravity(Gravity.CENTER);
   tab.setSelected(active);tab.setContentDescription(titles[i]);tab.setMinimumHeight(Math.round(56*d));
   tab.setPadding(Math.round(d*2),Math.round(d*4),Math.round(d*2),Math.round(d*4));
   android.util.TypedValue feedback=new android.util.TypedValue();
   if(activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,feedback,true))tab.setBackgroundResource(feedback.resourceId);
   ImageView icon=new ImageView(activity);icon.setImageResource(symbols[i]);icon.setColorFilter(active?UiProfiles.accent(activity):UiProfiles.muted(activity));
   int iconSize=Math.round(22*d);tab.addView(icon,iconSize,iconSize);
   TextView caption=new TextView(activity);caption.setText(titles[i]);caption.setTextSize(12);
   caption.setTextColor(active?UiProfiles.accent(activity):UiProfiles.muted(activity));caption.setGravity(Gravity.CENTER);
   if(active)caption.setTypeface(Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
   tab.addView(caption,-1,-2);tab.setOnClickListener(v->listener.select(target));
   nav.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
  }
  return nav;
 }
 private float swipeStartX,swipeStartY;
 private boolean swipeTracking;
 @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){
  if(e.getPointerCount()>1){swipeTracking=false;return super.dispatchTouchEvent(e);}
  if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
   swipeStartX=e.getX();swipeStartY=e.getY();swipeTracking=true;
  }else if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP&&swipeTracking){
   swipeTracking=false;
   float dx=e.getX()-swipeStartX,dy=e.getY()-swipeStartY;
   if(swipeStartY<getResources().getDisplayMetrics().heightPixels*.43f&&Math.abs(dx)>dp(95)&&Math.abs(dx)>Math.abs(dy)*1.8f&&phone.active==null&&!UiProfiles.MINIMAL.equals(UiProfiles.id(this))){
    if(dx<0){openDirectory(false);return true;}
    if(dx>0){openDirectory(true);return true;}
   }
  }else if(e.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)swipeTracking=false;
  return super.dispatchTouchEvent(e);
 }
 private void openDirectory(boolean recent){
  openDirectory(recent,false);
 }
 private void openDirectory(boolean recent,boolean home){
  openDirectory(recent,home,null);
 }
 private void openDirectory(boolean recent,boolean home,String searchQuery){
  if(phone.active!=null)return;
  directoryAtHome=home;
  directoryVisible=true;
  Intent next=new Intent(this,ContactsScreen.class);
  next.putExtra("recent",recent);
  next.putExtra("home",home);
  if(searchQuery!=null){
   next.putExtra("openSearch",true);
   next.putExtra("query",searchQuery);
  }
  if(home)next.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
  startActivityForResult(next,21);
 }
 private void headerAction(int drawable,String description,Runnable action){
  ImageButton key=new ImageButton(this);
  key.setImageResource(drawable);key.setColorFilter(UiProfiles.text(this));
  key.setContentDescription(description);
  key.setBackground(ElectricUi.buttonSurface(this,18));
  key.setPadding(dp(12),dp(12),dp(12),dp(12));
  header.addView(key,box(48,48));
  key.setOnClickListener(v->action.run());
 }
 protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==37){
   if(result==RESULT_OK&&data!=null&&data.getBooleanExtra("configureSip",false))configureSip();
   return;
  }
  if(request==SpeedDial.PICK_PHONE){speedDial.picked(result,data);return;}
  if(request==12||request==15){
   if(isDefaultPhone())checkIncomingSetup(false);
   else if(request==12)PhoneRole.offerSettings(this,15);
   else note("Приложение телефона не изменено");
   return;
  }
  if(request==DISPLAY_SETTINGS||request==SIP_DISPLAY_SETTINGS){
   showCallDisplay(request==SIP_DISPLAY_SETTINGS);
   return;
  }
  if(request!=21)return;
  directoryVisible=false;
  boolean wasHome=directoryAtHome;
  directoryAtHome=false;
  if(pendingContactShortcut!=null){
   Intent contact=pendingContactShortcut;pendingContactShortcut=null;
   openContactShortcut(contact);return;
  }
  if(phone.active!=null)return;
  if(result!=RESULT_OK||data==null){
   if(wasHome)finish();
   return;
  }
  String tab=data.getStringExtra("tab");
  if("settings".equals(tab)){configure();return;}
  if("incomingSetup".equals(tab)){checkIncomingSetup(false);return;}
  if("recent".equals(tab)||"contacts".equals(tab)){openDirectory("recent".equals(tab),wasHome);return;}
  String selected=data.getStringExtra("number");
  if(selected!=null&&!selected.isEmpty()){
   number.setText(selected);
   number.setSelection(number.length());
   lookupName(selected);
   if(data.getBooleanExtra("directCall",false)) {
    if(!sim&&!phone.ready()&&!emergency(selected)){
     new StationSheet.Builder(this).setTitle("Городская линия недоступна")
       .setMessage(selected)
       .setItems(new String[]{"Позвонить с SIM","Настроить городскую линию"},(dialog,i)->{
        if(i==0){route(true);startOutgoing();}else configureSip();
       }).setNegativeButton("Отмена",null).show();
    }else startOutgoing();
   }
  }
 }
 private void lookupName(String dialed){
  if(dialed==null||dialed.equals(lookupNumber))return;
  lookupNumber=dialed;
  knownName="";
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return;
  new Thread(()->{
   String result=Directory.name(this,dialed);
   runOnUiThread(()->{
    if(isDestroyed()||!dialed.equals(lookupNumber))return;
    knownName=result==null||result.equals(dialed)?"":result;
    refresh();
   });
  },"ContactName").start();
 }
 void route(boolean value){sim=value;getPreferences(0).edit().putString("route",value?"sim":"sip").apply();refresh();}
 private static boolean isNumberIntent(Intent intent){
  if(intent==null)return false;
  boolean supported=Intent.ACTION_DIAL.equals(intent.getAction())||Intent.ACTION_VIEW.equals(intent.getAction());
  return supported&&intent.getData()!=null&&"tel".equalsIgnoreCase(intent.getData().getScheme());
 }
 void importNumber(Intent intent){
  if(!isNumberIntent(intent))return;
  String imported=intent.getData().getSchemeSpecificPart();
  if(imported==null||imported.length()>128)return;
  number.setText(imported.trim());number.setSelection(number.length());
  // A link only fills the dial pad. The user always confirms the outgoing call.
 }
 private void openContactShortcut(Intent source){
  if(callBlocksNumberActions())return;
  Intent next=new Intent(this,ContactsScreen.class).putExtra("recent",false).putExtra("home",false);
  String lookup=source.getStringExtra(ContactsScreen.EXTRA_CONTACT_LOOKUP);
  if(lookup!=null&&lookup.length()<=2048){
   Uri uri=Uri.parse(lookup);
   if("content".equals(uri.getScheme())&&ContactsContract.AUTHORITY.equals(uri.getAuthority()))
    next.putExtra(ContactsScreen.EXTRA_CONTACT_LOOKUP,lookup);
  }
  String selected=source.getStringExtra(ContactsScreen.EXTRA_CONTACT_NUMBER);
  if(selected!=null&&selected.length()<=128)next.putExtra(ContactsScreen.EXTRA_CONTACT_NUMBER,selected);
  directoryAtHome=false;directoryVisible=true;
  startActivityForResult(next,21);
 }
 public void onNewIntent(Intent i){
  super.onNewIntent(i);setIntent(i);
  boolean contactShortcut=ContactsScreen.ACTION_OPEN_CONTACT.equals(i.getAction());
  if(phone.active!=null||isNumberIntent(i)||contactShortcut)dismissSmartMatches();
  if(directoryVisible&&(phone.active!=null||isNumberIntent(i)||contactShortcut)){
   if(contactShortcut)pendingContactShortcut=new Intent(i);
   directoryAtHome=false;finishActivity(21);
   if(contactShortcut)return;
  }
  if(contactShortcut){openContactShortcut(i);return;}
  if(!callBlocksNumberActions()){
   importNumber(i);
   if(isNumberIntent(i))lookupName(number.getText().toString());
  }
 }
 boolean emergency(String raw){
  String n=phoneDigits(raw);
  if(n==null||!n.matches("[0-9]+"))return false;
  if(Build.VERSION.SDK_INT>=29){
   try{
    android.telephony.TelephonyManager manager=getSystemService(android.telephony.TelephonyManager.class);
    if(manager!=null && manager.isEmergencyNumber(n))return true;
   }catch(RuntimeException ignored){}
  }
  try{return android.telephony.PhoneNumberUtils.isLocalEmergencyNumber(this,n);}
  catch(RuntimeException ignored){return "112".equals(n)||"911".equals(n);}
 }
 private void emergencyCall(String raw){
  String n=android.telephony.PhoneNumberUtils.stripSeparators(raw);
  Uri tel=Uri.fromParts("tel",n,null);TelecomManager telecom=getSystemService(TelecomManager.class);
  if(telecom!=null && isDefaultPhone() && checkSelfPermission(Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED){
   try{telecom.placeCall(tel,new Bundle());return;}catch(RuntimeException ignored){}
  }
  Intent dial=new Intent(Intent.ACTION_DIAL,tel);
  if(Build.VERSION.SDK_INT>=29 && telecom!=null){
   String system=telecom.getSystemDialerPackage();
   if(system!=null&&!system.equals(getPackageName())){
    try{startActivity(new Intent(dial).setPackage(system));return;}catch(ActivityNotFoundException|SecurityException ignored){}
   }
  }
  // API 26–28 has no public getSystemDialerPackage(). Select a preinstalled handler.
  for(android.content.pm.ResolveInfo match:getPackageManager().queryIntentActivities(dial,PackageManager.MATCH_DEFAULT_ONLY|PackageManager.MATCH_SYSTEM_ONLY)){
   if(match.activityInfo==null||!match.activityInfo.exported||getPackageName().equals(match.activityInfo.packageName))continue;
   try{startActivity(new Intent(dial).setClassName(match.activityInfo.packageName,match.activityInfo.name));return;}
   catch(ActivityNotFoundException|SecurityException ignored){}
  }
  note("Системный экран экстренного вызова недоступен");
 }
 // Reject URI parameters, pauses and letters rather than silently changing the destination.
 private static String dialChars(String raw){
  return raw==null?"":raw.replaceAll("[()\\s\\p{Zs}\\p{Pd}]","");
 }
 static String phoneDigits(String raw){
  String n=dialChars(raw);
  return n.matches("\\+?[0-9]{1,15}")?n:"";
 }
 private static boolean serviceCode(String n){
  return (n.indexOf('*')>=0||n.indexOf('#')>=0)&&n.matches("[0-9*#+]{1,80}");
 }
 void callSpeedNumber(String raw){
  if(callBlocksNumberActions())return;
  number.setText(raw);number.setSelection(number.length());lookupName(raw);startOutgoing();
 }
 private void startOutgoing(){
  String n=dialChars(number.getText().toString());
  if(emergency(n)){emergencyCall(n);return;}
  if(callBlocksNumberActions()){note("Сначала завершите текущий разговор");return;}
  if(serviceCode(n)){
   new StationSheet.Builder(this).setTitle("Код оператора").setMessage("Этот код будет передан сотовому оператору через SIM.")
     .setPositiveButton("Отправить с SIM",(d,i)->{if(!callBlocksNumberActions())mobile(n);})
     .setNegativeButton("Отмена",null).show();return;
  }
  if(sim)mobile(n);else if(phone.ready())begin();else configureSip();
 }
 void mobile(String n){
  if(!serviceCode(n)&&!n.matches("\\+?[0-9]{1,25}")){note("Введите номер телефона");return;}
  if(!isDefaultPhone()){defaultPhone();return;}
  if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CALL_PHONE},3);return;}
  try{getSystemService(TelecomManager.class).placeCall(Uri.fromParts("tel",n,null),new Bundle());}catch(RuntimeException e){note("Сотовый вызов недоступен. Проверьте SIM и разрешения.");}
 }
 void answerSip(Call target){
  if(target==null||target!=phone.active||!phone.ringing())return;
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   awaitingMic=target;
   requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},5);
   return;
  }
  phone.sipCore().setMicEnabled(true);
  if(target.accept()!=0)note("Не удалось принять звонок");
 }
 private void switchSipSpeaker(){
  Call target=shownCall;
  if(target==null||target!=phone.active||phone.ringing())return;
  AudioDevice output=target.getOutputAudioDevice();
  boolean speaker=output!=null&&output.getType()==AudioDevice.Type.Speaker;
  AudioDevice.Type desired=speaker?AudioDevice.Type.Earpiece:AudioDevice.Type.Speaker;
  for(AudioDevice device:phone.sipCore().getExtendedAudioDevices()){
   if(device.getType()==desired){
    target.setOutputAudioDevice(device);phone.updateSipProximity();refresh();return;
   }
  }
 }
 private TextView callTool(String title,int symbol){
  TextView control=button(title,13,0xc4202b26,FG);
  control.setMinHeight(dp(56));control.setPadding(dp(2),dp(5),dp(2),dp(5));
  android.graphics.drawable.Drawable image=getDrawable(symbol).mutate();
  image.setTint(GREEN);image.setBounds(0,0,dp(19),dp(19));
  control.setCompoundDrawables(null,image,null,null);control.setCompoundDrawablePadding(dp(4));
  control.setFocusable(true);
  tools.addView(control,flex(-2));return control;
 }

 void listen(){
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},6);return;
  }
  checkIncomingSetup(true);
 }
 private boolean isDefaultPhone(){return PhoneRole.held(this);}
 void defaultPhone(){
  if(isDefaultPhone()){checkIncomingSetup(false);return;}
  PhoneRole.request(this,12,15);
 }
 private boolean needsNotificationPermission(){
  return Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED;
 }
 private boolean canAskNotificationPermission(){
  return needsNotificationPermission()&&(!getPreferences(0).getBoolean("askedNotify",false)||
          shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS));
 }
 private void askNotificationPermission(int request){
  getPreferences(0).edit().putBoolean("askedNotify",true).apply();
  requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},request);
 }
 private void checkIncomingSetup(boolean sip){
  showCallDisplay(sip);
 }
 private void showCallDisplay(boolean enableSip){
  if(isFinishing()||isDestroyed()||phone.active!=null)return;
  startActivityForResult(new Intent(this,IncomingSetup.class).putExtra("enableSip",enableSip),37);
 }
 private void openCallSettings(Intent intent,int request){
  try{startActivityForResult(intent,request);}
  catch(ActivityNotFoundException|SecurityException unavailable){
   try{startActivityForResult(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
           Uri.fromParts("package",getPackageName(),null)),request);}
   catch(ActivityNotFoundException|SecurityException ignored){note("Откройте настройки приложения в Android");}
  }
 }
 void begin(){
  String n=phoneDigits(number.getText().toString());
  if(n.matches("8[0-9]{10}"))n="7"+n.substring(1);
  if(n.isEmpty()){note("Проверьте номер телефона");return;}
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pending=n;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}
  place(n);
 }
 void place(String n){try{phone.dial(n);}catch(Exception e){note(e.getMessage());}}
 public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
  super.onRequestPermissionsResult(request,permissions,grants);
  boolean granted=grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED;
  if(request==7||request==8||request==DISPLAY_PERMISSION){
   if(grants.length>0)showCallDisplay(request==7);
   return;
  }
  if(request==1){
   String n=pending;pending=null;
   if(n!=null){if(granted)place(n);else note("Для разговора нужен доступ к микрофону");}
   return;
  }
  if(request==5){
   Call target=awaitingMic;awaitingMic=null;
   if(granted)answerSip(target);else if(target!=null&&target==phone.active)note("Для разговора нужен доступ к микрофону");
   return;
  }
  if(!granted){note("Разрешение не выдано");return;}
  if(request==3)note("Доступ разрешён. Нажмите кнопку вызова ещё раз.");
  else if(request==6)listen();
 }

 protected void onResume(){
  super.onResume();
  smartResumed=true;
  if(shownStyle!=null&&!shownStyle.equals(UiProfiles.signature(this))){recreate();return;}
  DriveTools.background(this,findViewById(android.R.id.custom));
  if(phone.listening())phone.updateService();phone.observer=this::refresh;refresh();
  loadSmartIndex();
 }
 protected void onPause(){
  phone.observer=null;
  smartResumed=false;
  smartQueryGeneration++;smartLoadGeneration++;
  if(smartPending!=null)smartUi.removeCallbacks(smartPending);
  dismissSmartMatches();
  dismissHeaderPanels();
  clearSmartSuggestions();
  super.onPause();
 }
 @Override protected void onDestroy(){
  smartResumed=false;
  smartQueryGeneration++;smartLoadGeneration++;
  if(smartPending!=null)smartUi.removeCallbacks(smartPending);
  dismissSmartMatches();
  dismissHeaderPanels();
  smartWorker.shutdownNow();
  super.onDestroy();
 }
 protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("number",number.getText().toString());b.putInt("pickingSpeedKey",speedDial.picking);b.putBoolean("directoryHome",directoryAtHome);b.putBoolean("directoryOpen",directoryVisible);}
 @Override protected void onStop(){incomingReply.close();super.onStop();}
 void refresh(){
  incomingReply.reconcile();
  boolean busy=phone.active!=null, incoming=phone.ringing(), cell=sim&&!busy;
  if(busy){dismissSmartMatches();dismissHeaderPanels();}
  boolean sipReady=phone.ready();

  String sipState=sipReady?"SIP подключён":"SIP не подключён";
  routeLabel.setText((sim?"SIM":sipReady?"SIP":"SIP !")+" ▾");
  routeSelector.setContentDescription("Выбрать линию вызова. "+(sim?"Выбрана SIM. ":"Выбрана SIP. ")+sipState);
  routeSelector.setTooltipText((sim?"Сотовая связь SIM. ":"Интернет-телефония SIP. ")+sipState);
  routeSelector.setEnabled(!callBlocksNumberActions());
  body.setGravity(busy?Gravity.TOP:Gravity.BOTTOM);
   header.setVisibility(busy?View.GONE:View.VISIBLE);idleCard.setVisibility(busy?View.GONE:View.VISIBLE);
   if(smartPanel!=null)smartPanel.setVisibility(busy?View.GONE:(smartMatches!=null?View.VISIBLE:View.INVISIBLE));
  idleActions.setVisibility(busy?View.GONE:View.VISIBLE);
  navigation.setVisibility(busy?View.GONE:View.VISIBLE);
  callInfo.setVisibility(busy?View.VISIBLE:View.GONE);batterySlot.setVisibility(busy?View.VISIBLE:View.GONE);
  tools.setVisibility(busy&&!incoming?View.VISIBLE:View.GONE);
  if(shownCall!=phone.active){
   shownCall=phone.active;
   awaitingMic=null;sipKeysOpen=false;
   batterySlot.removeAllViews();callBattery=null;
   if(busy){
    final Call target=shownCall;
    callBattery=new BatteryCallControl(this,incoming,()->answerSip(target),()->{
     if(target!=phone.active||batteryRinging!=phone.ringing())return;
     int result=batteryRinging?target.decline(Reason.Declined):target.terminate();
     if(result!=0)note("Не удалось завершить звонок");
    });
    batterySlot.addView(callBattery,box(-1,-2));
   }
  }
  keypad.setVisibility(!busy||(!incoming&&sipKeysOpen)?View.VISIBLE:View.GONE);
  dialPane.setVisibility(!busy||(!incoming&&sipKeysOpen)?View.VISIBLE:View.GONE);
  replyButton.setVisibility(incoming&&IncomingReply.sipRecipient(shownCall)!=null?View.VISIBLE:View.GONE);
  updateNumberMenu();
  if(busy){
   batteryRinging=incoming;
   callBattery.setRinging(incoming);
   String caller=phone.caller();
   lookupName(caller);
   callPhase.setText(incoming?"Входящий звонок":phone.phase);
   callIdentity.setText(knownName.isEmpty()?caller:knownName);
   callAddress.setText(caller);callAddress.setVisibility(knownName.isEmpty()?View.GONE:View.VISIBLE);
   boolean liveMic=phone.sipCore().isMicEnabled();
   boolean speaker=shownCall.getOutputAudioDevice()!=null&&shownCall.getOutputAudioDevice().getType()==AudioDevice.Type.Speaker;
   toolState(micControl,!liveMic,liveMic?"Выключить микрофон":"Включить микрофон");
   micControl.setText(liveMic?"Микрофон":"Без звука");
   toolState(speakerControl,speaker,speaker?"Выключить громкую связь":"Включить громкую связь");
   toolState(keysControl,sipKeysOpen,sipKeysOpen?"Скрыть клавиши":"Открыть клавиши");
  }else{
   updateCallLabel(cell,sipReady);
   int buttonOpacity=UiProfiles.value(this,UiProfiles.BUTTON);
   action.setBackground(glass(UiProfiles.opacity(UiProfiles.accent(this),buttonOpacity),
       UiProfiles.opacity(UiProfiles.accent(this),Math.max(0,buttonOpacity-14)),UiProfiles.accent(this),24));
   action.setTextColor(buttonOpacity>=65?UiProfiles.active(this).base:UiProfiles.text(this));
  }
  int lockFlags=WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
  if(incoming)getWindow().addFlags(lockFlags);
  else if(busy)getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
  else getWindow().clearFlags(lockFlags);
 }
 private void toolState(TextView control,boolean selected,String description){
  control.setSelected(selected);control.setContentDescription(description);
  control.setBackground(pressable(selected?0xd32c4836:0xc4202b26,18));
 }
 private void updateCallLabel(boolean cell,boolean sipReady){
  String n=dialChars(number.getText().toString());
  action.setText(emergency(n)?"Экстренный вызов":serviceCode(n)?"Код через SIM":
          cell||sipReady?"Позвонить":phone.account==null?"Подключить линию":"Настроить линию");
 }
 private String actionNumber(){return phoneDigits(number.getText().toString());}
 private boolean callBlocksNumberActions(){
  android.telecom.Call mobile=MobileCalls.live==null?null:MobileCalls.live.current();
  return phone.active!=null||(mobile!=null&&mobile.getState()!=android.telecom.Call.STATE_DISCONNECTED);
 }
 private void updateNumberMenu(){
  boolean available=!callBlocksNumberActions()&&(number.length()==0||!actionNumber().isEmpty());
  numberMenu.setContentDescription(number.length()==0?"Быстрый набор":"Действия с номером");
  numberMenu.setVisibility(available?View.VISIBLE:View.INVISIBLE);
 }
 private void showNumberMenu(){
  String dialed=actionNumber();
  if(callBlocksNumberActions())return;
  if(dialed.isEmpty()){speedDial.keys("");return;}
  new StationSheet.Builder(this).setTitle(dialed)
    .setItems(new String[]{"Написать сообщение","Сохранить номер","Добавить к контакту","Добавить в быстрый набор"},(dialog,index)->{
     if(callBlocksNumberActions())return;
     if(index==0)smsNumber(this,dialed);
     else if(index==1)saveNumber(this,dialed);
     else if(index==2)addToContact(this,dialed);
     else speedDial.keys(dialed);
    }).setNegativeButton("Закрыть",null).show();
 }
 static void smsNumber(Activity activity,String dialed){QuickSms.open(activity,dialed);}
 static void saveNumber(Activity activity,String dialed){
  Intent create=new Intent(Intent.ACTION_INSERT,ContactsContract.Contacts.CONTENT_URI)
      .putExtra(ContactsContract.Intents.Insert.PHONE,dialed);
  openNumberAction(activity,create,"На устройстве нет редактора контактов");
 }
 static void addToContact(Activity activity,String dialed){
  Intent edit=new Intent(Intent.ACTION_INSERT_OR_EDIT)
      .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
      .putExtra(ContactsContract.Intents.Insert.PHONE,dialed);
  openNumberAction(activity,edit,"На устройстве нет приложения для выбора контакта");
 }
 private static void openNumberAction(Activity activity,Intent intent,String message){
  try{activity.startActivity(intent);}
  catch(ActivityNotFoundException|SecurityException e){Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
 }
 void configure(){
  final String[] titles={"ОФОРМЛЕНИЕ","ЗВОНКИ И ЛИНИИ","ПОВЕДЕНИЕ","ПРИЛОЖЕНИЕ"};
  final String[][] labels={
   {"Темы и фон"},
   {"SIP-линия","Звонки через SIM","Входящие на экране блокировки"},
   {"Стартовый экран","Поворот экрана"},
   {"Проверить обновления"}
  };
  final Runnable[][] actions={
   {()->PhoneAppearance.show(this)},
   {()->configureSip(),()->defaultPhone(),()->showCallDisplay(false)},
   {()->chooseStart(),()->ScreenOrientation.choose(this)},
   {()->AppUpdates.open(this)}
  };
  LinearLayout content=new LinearLayout(this);
  content.setOrientation(LinearLayout.VERTICAL);
  content.setPadding(dp(2),dp(3),dp(2),dp(6));
  for(int i=0;i<titles.length;i++){
   TextView heading=label(titles[i],12,UiProfiles.secondary(this));
   heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
   heading.setLetterSpacing(.06f);
   heading.setPadding(dp(8),dp(i==0?8:15),dp(8),dp(6));
   content.addView(heading,box(-1,-2));
   LinearLayout section=new LinearLayout(this);
   section.setOrientation(LinearLayout.VERTICAL);
   section.setPadding(dp(5),dp(2),dp(5),dp(2));
   section.setBackground(ElectricUi.surface(this,16,false));
   content.addView(section,box(-1,-2));
   for(int k=0;k<labels[i].length;k++){
    if(k>0){
     View divider=new View(this);
     divider.setBackgroundColor(UiProfiles.opacity(UiProfiles.muted(this),20));
     LinearLayout.LayoutParams div=new LinearLayout.LayoutParams(-1,dp(1));
     div.leftMargin=dp(11);div.rightMargin=dp(11);
     section.addView(divider,div);
    }
    final Runnable action=actions[i][k];
    LinearLayout row=new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(dp(50));
    row.setPadding(dp(11),dp(7),dp(9),dp(7));
    android.util.TypedValue feedback=new android.util.TypedValue();
    if(getTheme().resolveAttribute(android.R.attr.selectableItemBackground,feedback,true)&&feedback.resourceId!=0)
     row.setBackgroundResource(feedback.resourceId);
    TextView item=label(labels[i][k],15,UiProfiles.text(this));
    item.setGravity(Gravity.CENTER_VERTICAL);
    row.addView(item,new LinearLayout.LayoutParams(0,-2,1));
    TextView next=label("›",22,UiProfiles.muted(this));
    next.setGravity(Gravity.CENTER);
    next.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(next,box(26,40));
    row.setContentDescription(labels[i][k]);
    row.setOnClickListener(v->action.run());
    section.addView(row,box(-1,-2));
   }
  }
  new StationSheet.Builder(this).setTitle("Настройки")
   .setView(content).setNegativeButton("Готово",null).show();
 }
 void chooseStart(){
  int current=java.util.Arrays.asList(START_TABS).indexOf(startTab());
  new StationSheet.Builder(this).setTitle("Что открывать при запуске").setSingleChoiceItems(START_NAMES,current,(d,which)->{
   getPreferences(0).edit().putString("startTab",START_TABS[which]).apply();d.dismiss();
  }).setNegativeButton("Отмена",null).show();
 }
 void configureSip(){
  LinearLayout form=new LinearLayout(this);form.setPadding(dp(22),dp(8),dp(22),dp(8));form.setOrientation(android.widget.LinearLayout.VERTICAL);
  form.addView(label("Учётные данные вашего SIP-провайдера",14,MUTED));
  EditText login=field(form,"SIP-логин",phone.value("user"),1),password=field(form,phone.value("secret").isEmpty()?"SIP-пароль":"Пароль сохранён", "",129),server=field(form,"SIP-сервер",phone.value("host").isEmpty()?"":phone.value("host"),17);
  String[] options={"UDP","TCP","TLS"};StationChoice transport=new StationChoice(this,"Транспорт SIP",options,java.util.Arrays.asList(options).indexOf(phone.value("transport")));form.addView(transport);
  TextView incoming=button("",16,CARD,FG);incoming.setMinimumHeight(dp(48));incoming.setText(phone.listening()?"Выключить входящие SIP":"Включить входящие SIP");form.addView(incoming);
  ScrollView sc=new ScrollView(this);sc.addView(form);
  StationSheet dialog=new StationSheet.Builder(this).setTitle("Городская линия").setView(sc).setNegativeButton("Закрыть",null).setPositiveButton("Подключить",null).create();dialog.show();
  incoming.setOnClickListener(v->{dialog.dismiss();if(phone.listening())phone.setListening(false);else if(phone.account==null)note("Сначала подключите SIP-линию");else listen();});
  dialog.getButton(-1).setOnClickListener(v->{try{phone.save(login.getText().toString().trim(),server.getText().toString().trim(),password.getText().toString(),(String)transport.getSelectedItem());dialog.dismiss();}catch(Exception e){note(e.getMessage());}});
 }
 EditText field(LinearLayout form,String hint,String val,int type){EditText e=new EditText(this);e.setInputType(type);e.setSingleLine();e.setHint(hint);e.setText(val);form.addView(e);return e;}
 void insert(String s){int a=Math.max(0,number.getSelectionStart()),b=Math.max(0,number.getSelectionEnd());number.getText().replace(Math.min(a,b),Math.max(a,b),s);}
 void note(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setGravity(Gravity.CENTER_VERTICAL);body.addView(x,box(-1,-2));return x;}
 LinearLayout.LayoutParams box(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
 LinearLayout.LayoutParams flex(int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,h<0?h:dp(h),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));return p;}
 TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(UiProfiles.type(this,size));
  t.setTextColor(color==FG?UiProfiles.text(this):color==MUTED?UiProfiles.muted(this):color==GREEN?UiProfiles.accent(this):color==CARD?UiProfiles.panel(this):color);
  t.setGravity(Gravity.CENTER_VERTICAL);return t;}
 TextView button(String s,int size,int bg,int fg){TextView t=label(s,size,fg);t.setGravity(Gravity.CENTER);t.setBackground(pressable(bg,18));return t;}
 private void icon(TextView view,int resource,int tint,int size){
  android.graphics.drawable.Drawable drawable=getDrawable(resource).mutate();
  drawable.setTint(tint==FG?UiProfiles.text(this):tint==GREEN?UiProfiles.accent(this):tint);drawable.setBounds(0,0,dp(size),dp(size));
  view.setCompoundDrawablesRelative(drawable,null,null,null);
 }
 private RippleDrawable glass(int top,int bottom,int edge,int radius){
  GradientDrawable fill=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});
  fill.setCornerRadius(dp(UiProfiles.corner(this,radius)));fill.setStroke(dp(1),edge);
  return new RippleDrawable(ColorStateList.valueOf(UiProfiles.opacity(UiProfiles.accent(this),26)),fill,null);
 }
 android.graphics.drawable.Drawable pressable(int color,int radius){
  return new RippleDrawable(ColorStateList.valueOf(UiProfiles.opacity(UiProfiles.accent(this),24)),shape(color==CARD?UiProfiles.button(this):color,radius),null);
 }
 GradientDrawable shape(int c,int radius){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(UiProfiles.corner(this,radius)));return d;}
}
