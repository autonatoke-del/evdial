package ru.autonatoke.evdial;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native wallpaper picker. Selection changes EVDial only, never the phone's system wallpaper. */
public class ThemeStudio extends Activity {
 private static final int PICK=6201;
 private TextView selected;private View preview;private TextView own;private boolean importing;
 private TextView sampleButton,samplePanel; private View sampleKey, sampleNav;
 private final ArrayList<View> cards=new ArrayList<>();private final ArrayList<TextView> modes=new ArrayList<>();
 private final ArrayList<View> profiles=new ArrayList<>();
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().setStatusBarColor(UiProfiles.active(this).base);getWindow().setNavigationBarColor(UiProfiles.active(this).base);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(UiProfiles.active(this).base);
  LinearLayout root=ElectricUi.column(this);root.setPadding(dp(16),dp(10),dp(16),dp(24));scroll.addView(root);setContentView(scroll);
  add(root,ElectricUi.button(this,tr("‹  Вернуться к телефону","‹  Back to phone"),this::finish),0);
  TextView title=ElectricUi.text(this,tr("Темы и оформление","Themes and appearance"),28,ElectricUi.WHITE,true);title.setPadding(0,dp(19),0,dp(9));root.addView(title,-1,-2);
  TextView scope=ElectricUi.text(this,tr("Темы меняют только оформление звонилки. SIM, SIP, контакты и звонки остаются прежними.","Themes change only the appearance. SIM, SIP, contacts and calls remain unchanged."),14,ElectricUi.MUTED,false);root.addView(scope,-1,-2);
  section(root,tr("ВЫБЕРИТЕ ОФОРМЛЕНИЕ", "CHOOSE APPEARANCE"));
  LinearLayout profileRow=new LinearLayout(this);profileRow.setBaselineAligned(false);
  for(UiProfiles.Preset preset:UiProfiles.values()){
    LinearLayout tile=ElectricUi.column(this);tile.setGravity(Gravity.CENTER_HORIZONTAL);
    tile.setPadding(dp(8),dp(14),dp(8),dp(12));
    View swatch=new View(this);GradientDrawable mark=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
       new int[]{preset.accent,preset.panel,preset.base});mark.setCornerRadius(dp(12));
    mark.setStroke(dp(1),preset.secondary);swatch.setBackground(mark);
    tile.addView(swatch,dp(44),dp(44));
    TextView heading=ElectricUi.text(this,preset.label,15,ElectricUi.WHITE,true);
    heading.setGravity(Gravity.CENTER);heading.setMaxLines(2);heading.setPadding(0,dp(8),0,0);
    tile.addView(heading,-1,-2);tile.setTag(preset.id);profiles.add(tile);
    tile.setOnClickListener(v->{if(!preset.id.equals(UiProfiles.id(this))){UiProfiles.select(this,preset.id);recreate();}});
    tile.setContentDescription(tr("Выбрать тему ","Select theme ")+preset.label);
    LinearLayout.LayoutParams slot=new LinearLayout.LayoutParams(0,-2,1);
    slot.setMargins(dp(3),0,dp(3),0);profileRow.addView(tile,slot);
    if(profileRow.getChildCount()==3){add(root,profileRow,6);profileRow=new LinearLayout(this);profileRow.setBaselineAligned(false);}
  }
  if(profileRow.getChildCount()>0)add(root,profileRow,12);
  TextView themeDescription=ElectricUi.text(this,UiProfiles.active(this).description,14,ElectricUi.MUTED,false);
  add(root,themeDescription,10);
  LinearLayout advanced=ElectricUi.column(this);
  TextView advancedToggle=ElectricUi.button(this,tr("⚙  Тонкая настройка  ▾","⚙  Fine tuning  ▾"),()->{
    advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
  });
  add(root,advancedToggle,8);
  advanced.setVisibility(View.GONE);
  section(advanced,tr("ДОПОЛНИТЕЛЬНЫЕ НАСТРОЙКИ", "ADVANCED SETTINGS"));
  TextView hint=ElectricUi.text(this,tr("Параметры сохраняются отдельно для каждой темы. Прозрачность меняет фон кнопок, но не читаемость цифр.",
      "Controls are saved separately per theme. Opacity changes the key background, not the digits."),13,ElectricUi.MUTED,false);
  add(advanced,hint,6);
  sample(advanced);
  slider(advanced,tr("Прозрачность кнопок", "Button opacity"),UiProfiles.BUTTON,0,100,"%",10);
  slider(advanced,tr("Прозрачность цифровой клавиатуры", "Keypad opacity"),UiProfiles.KEYPAD,0,100,"%",4);
  slider(advanced,tr("Прозрачность панелей", "Panel opacity"),UiProfiles.PANEL,0,100,"%",4);
  slider(advanced,tr("Нижняя панель", "Bottom navigation opacity"),UiProfiles.NAV,0,100,"%",4);
  slider(advanced,tr("Насыщенность акцентов", "Accent saturation"),UiProfiles.ACCENT,45,100,"%",4);
  slider(advanced,tr("Скругление углов", "Corner radius"),UiProfiles.CORNERS,8,32,"",4);
  slider(advanced,tr("Размер шрифта", "Font size"),UiProfiles.SCALE,95,125,"%",4);
  add(advanced,ElectricUi.button(this,tr("Сбросить настройки текущей темы", "Reset current theme controls"),
      ()->{UiProfiles.reset(this);recreate();}),12);
  add(root,advanced,10);
  section(root,tr("ФОН ИЗОБРАЖЕНИЯ", "BACKGROUND IMAGE"));
  LinearLayout chosen=new LinearLayout(this);chosen.setGravity(Gravity.CENTER_VERTICAL);chosen.setPadding(dp(12),dp(12),dp(12),dp(12));chosen.setBackground(ElectricUi.surface(this,20,false));
  preview=new View(this);chosen.addView(preview,dp(94),dp(150));LinearLayout words=ElectricUi.column(this);words.setPadding(dp(14),0,0,0);chosen.addView(words,new LinearLayout.LayoutParams(0,-2,1));
  words.addView(ElectricUi.text(this,tr("ВЫБРАННЫЙ ФОН","SELECTED WALLPAPER"),11,ElectricUi.CYAN,true));
  selected=ElectricUi.text(this,"",22,ElectricUi.WHITE,true);selected.setPadding(0,dp(8),0,dp(10));words.addView(selected);
  words.addView(ElectricUi.text(this,tr("Набор · контакты\nэкран звонка","Dialer · contacts\ncall screen"),14,ElectricUi.MUTED,false));add(root,chosen,14);
  own=ElectricUi.button(this,tr("＋  Выбрать своё фото","＋  Use my own photo"),this::pick);add(root,own,12);
  TextView privacy=ElectricUi.text(this,tr("Фото сохраняется только на устройстве. Местоположение и другие EXIF-данные не копируются.","The image stays on your device. Location and other EXIF metadata are not copied."),12,ElectricUi.MUTED,false);add(root,privacy,8);
  LinearLayout row=new LinearLayout(this);String[] labels={tr("Без фона","Plain"),tr("Спокойно","Calm"),tr("Ярко","Vivid")};
  for(int i=0;i<3;i++){final int mode=i;TextView button=ElectricUi.button(this,labels[i],()->{Appearance.backgroundMode(this,mode);updateSelection();});button.setTextSize(13);button.setPadding(dp(5),dp(10),dp(5),dp(10));modes.add(button);
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(i==0?0:dp(3),0,i==2?0:dp(3),0);row.addView(button,p);}
  add(root,row,16);
  TextView gallery=ElectricUi.text(this,tr("КОЛЛЕКЦИЯ EVDIAL  /  12","EVDIAL COLLECTION  /  12"),13,ElectricUi.CYAN,true);gallery.setPadding(0,dp(24),0,dp(12));root.addView(gallery);
  PhoneThemes.Theme[] themes=PhoneThemes.values();
  for(int r=0;r<themes.length;r+=2){LinearLayout line=new LinearLayout(this);line.setBaselineAligned(false);
   for(int c=0;c<2&&r+c<themes.length;c++){
    final PhoneThemes.Theme theme=themes[r+c];LinearLayout card=ElectricUi.column(this);card.setPadding(dp(6),dp(6),dp(6),dp(12));
    View picture=new View(this);card.addView(picture,-1,dp(190));Appearance.applyPreview(this,picture,PhoneThemes.resource(this,theme),2);
    TextView name=ElectricUi.text(this,theme.name,15,ElectricUi.WHITE,true);name.setPadding(dp(4),dp(10),dp(4),0);card.addView(name,-1,-2);
    card.setTag(theme.id);cards.add(card);card.setOnClickListener(v->{PhoneThemes.select(this,theme.id);if(Appearance.backgroundMode(this)==0)Appearance.backgroundMode(this,2);updateSelection();});
    card.setContentDescription(tr("Выбрать фон: ","Select wallpaper: ")+theme.name);
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(c==0?0:dp(5),0,c==0?dp(5):0,0);line.addView(card,p);
   }add(root,line,10);
  }
  updateSelection();
 }
 private void section(LinearLayout root,String heading){
  TextView title=ElectricUi.text(this,heading,13,ElectricUi.CYAN,true);
  title.setLetterSpacing(.1f);title.setPadding(0,dp(24),0,dp(3));root.addView(title,-1,-2);
 }
 private GradientDrawable previewSwatch(int fill,int border){
  GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(UiProfiles.corner(this,14)));
  g.setStroke(dp(1),border);return g;
 }
 private void sample(LinearLayout root){
  LinearLayout demo=ElectricUi.column(this);
  demo.setPadding(dp(12),dp(12),dp(12),dp(12));
  GradientDrawable surface=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
      new int[]{UiProfiles.active(this).base,UiProfiles.active(this).panel});
  surface.setCornerRadius(dp(18));demo.setBackground(surface);
  LinearLayout tops=new LinearLayout(this);tops.setGravity(Gravity.CENTER_VERTICAL);
  sampleButton=ElectricUi.text(this,tr("Позвонить","Call"),17,ElectricUi.WHITE,true);
  sampleButton.setGravity(Gravity.CENTER);sampleButton.setMinHeight(dp(46));
  tops.addView(sampleButton,new LinearLayout.LayoutParams(0,-2,1));
  samplePanel=ElectricUi.text(this,"SIM / SIP",15,ElectricUi.WHITE,true);
  samplePanel.setGravity(Gravity.CENTER);samplePanel.setMinHeight(dp(46));
  LinearLayout.LayoutParams param=new LinearLayout.LayoutParams(0,-2,1);param.leftMargin=dp(8);tops.addView(samplePanel,param);
  demo.addView(tops,-1,-2);
  LinearLayout bottom=new LinearLayout(this);bottom.setPadding(0,dp(10),0,0);
  sampleKey=new View(this);sampleNav=new View(this);
  LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(28),1);half.rightMargin=dp(8);
  bottom.addView(sampleKey,half);bottom.addView(sampleNav,new LinearLayout.LayoutParams(0,dp(28),1));
  demo.addView(bottom,-1,-2);add(root,demo,12);
  updatePreviewOnly();
 }
 private void updatePreviewOnly(){
  if(sampleButton==null)return;
  sampleButton.setBackground(ElectricUi.buttonSurface(this,18));
  samplePanel.setBackground(ElectricUi.surface(this,18,false));
  sampleKey.setBackground(previewSwatch(UiProfiles.keypad(this),UiProfiles.secondary(this)));
  sampleNav.setBackground(previewSwatch(UiProfiles.navigation(this),UiProfiles.accent(this)));
 }
 private void slider(LinearLayout root,String name,String key,int min,int max,String suffix,int top){
  LinearLayout block=ElectricUi.column(this);
  LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);
  TextView label=ElectricUi.text(this,name,15,ElectricUi.WHITE,true);
  TextView reading=ElectricUi.text(this,"",14,ElectricUi.CYAN,true);reading.setGravity(Gravity.END);
  line.addView(label,new LinearLayout.LayoutParams(0,-2,1));line.addView(reading,dp(55),-2);
  block.addView(line,-1,-2);
  SeekBar bar=new SeekBar(this);bar.setMax(max);bar.setMin(min);bar.setProgress(UiProfiles.value(this,key));
  reading.setText(bar.getProgress()+suffix);
  bar.setProgressTintList(android.content.res.ColorStateList.valueOf(UiProfiles.accent(this)));
  bar.setThumbTintList(android.content.res.ColorStateList.valueOf(UiProfiles.accent(this)));
  bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
    @Override public void onProgressChanged(SeekBar seek,int progress,boolean fromUser){
      reading.setText(progress+suffix);
      if(fromUser){UiProfiles.set(ThemeStudio.this,key,progress);updatePreviewOnly();}
    }
    @Override public void onStartTrackingTouch(SeekBar seek){}
    @Override public void onStopTrackingTouch(SeekBar seek){updateSelection();}
  });
  block.addView(bar,-1,-2);add(root,block,top);
 }
 private void pick(){
  if(importing)return;
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  try{startActivityForResult(i,PICK);}catch(ActivityNotFoundException e){Toast.makeText(this,tr("На устройстве нет выбора фотографий","No image picker is installed"),Toast.LENGTH_LONG).show();}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);if(request!=PICK||result!=RESULT_OK||data==null||data.getData()==null)return;
  importing=true;own.setEnabled(false);own.setText(tr("Подготавливаю фото…","Preparing photo…"));
  WallpaperStore.importPhoto(this,data.getData(),(success,message)->{importing=false;own.setEnabled(true);own.setText(tr("＋  Выбрать своё фото","＋  Use my own photo"));
   if(success){updateSelection();Toast.makeText(this,tr("Ваш фон установлен","Your wallpaper is ready"),Toast.LENGTH_SHORT).show();}
   else Toast.makeText(this,message,Toast.LENGTH_LONG).show();
  });
 }
 private void updateSelection(){
  selected.setText(PhoneThemes.selectedName(this));
  if(WallpaperStore.enabled(this))WallpaperStore.apply(this,preview,Appearance.backgroundMode(this));
  else{WallpaperStore.cancel(preview);Appearance.applyPreview(this,preview,PhoneThemes.selectedResource(this),Appearance.backgroundMode(this));}
  for(View card:cards)card.setBackground(ElectricUi.surface(this,18,!WallpaperStore.enabled(this)&&card.getTag().equals(PhoneThemes.selectedId(this))));
  for(int i=0;i<modes.size();i++)modes.get(i).setBackground(ElectricUi.surface(this,16,i==Appearance.backgroundMode(this)));
  for(View item:profiles)item.setBackground(ElectricUi.surface(this,18,item.getTag().equals(UiProfiles.id(this))));
  if(sampleButton!=null){
    sampleButton.setBackground(ElectricUi.buttonSurface(this,18));
    sampleButton.setTextColor(UiProfiles.text(this));
    samplePanel.setBackground(ElectricUi.surface(this,18,false));
    samplePanel.setTextColor(UiProfiles.text(this));
    sampleKey.setBackground(previewSwatch(UiProfiles.keypad(this),UiProfiles.secondary(this)));
    sampleNav.setBackground(previewSwatch(UiProfiles.navigation(this),UiProfiles.accent(this)));
  }
 }
 private String tr(String ru,String en){return ElectricUi.tr(this,ru,en);} private int dp(int n){return ElectricUi.dp(this,n);}
 private void add(LinearLayout root,View view,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);root.addView(view,p);}
}
