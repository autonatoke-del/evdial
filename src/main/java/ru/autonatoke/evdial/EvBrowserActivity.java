package ru.autonatoke.evdial;
import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

/** A small EV link hub; web rendering stays in the selected browser's Custom Tab. */
public class EvBrowserActivity extends Activity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().setStatusBarColor(UiProfiles.active(this).base);getWindow().setNavigationBarColor(UiProfiles.active(this).base);
  ScrollView scroll=new ScrollView(this);LinearLayout root=ElectricUi.column(this);root.setId(android.R.id.custom);
  root.setPadding(dp(18),dp(12),dp(18),dp(24));scroll.addView(root);setContentView(scroll);Appearance.applyBackground(this,root);
  root.addView(ElectricUi.button(this,ElectricUi.tr(this,"‹  Вернуться в EVDial","‹  Back to EVDial"),this::finish),-1,-2);
  TextView title=ElectricUi.text(this,ElectricUi.tr(this,"Интернет для EV","EV browser"),28,ElectricUi.WHITE,true);title.setPadding(0,dp(24),0,dp(12));root.addView(title);
  EditText input=new EditText(this);input.setSingleLine();input.setTextColor(ElectricUi.WHITE);input.setHintTextColor(ElectricUi.MUTED);input.setTextSize(17);
  input.setPadding(dp(14),dp(15),dp(14),dp(15));input.setBackground(ElectricUi.surface(this,18,false));input.setHint(ElectricUi.tr(this,"Сайт или поисковый запрос","Website or search query"));
  input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);input.setImeOptions(EditorInfo.IME_ACTION_GO);root.addView(input,-1,-2);
  Runnable go=()->{String q=input.getText().toString().trim();if(q.isEmpty())return;
   String url=q;
   if(!q.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))url=q.contains(".")&&!q.contains(" ")?"https://"+q:"https://www.google.com/search?q="+Uri.encode(q);
   EvLinks.open(this,url);
  };
  input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_GO){go.run();return true;}return false;});
  add(root,ElectricUi.button(this,ElectricUi.tr(this,"Открыть сайт / поиск","Open website / search"),go));
  add(root,ElectricUi.button(this,"DIAGNOST AI  ↗",()->EvLinks.diagnost(this)));
  add(root,ElectricUi.button(this,"Open Charge Map  ↗",()->EvLinks.open(this,"https://openchargemap.org/site/poi")));
  add(root,ElectricUi.button(this,ElectricUi.tr(this,"Офлайн-каталог зарядок России","Russian offline charging directory"),()->Charging.open(this)));
  TextView note=ElectricUi.text(this,ElectricUi.tr(this,"Веб-страницы открываются в защищённой вкладке установленного браузера. Адрес сайта виден сверху; кнопка закрытия возвращает в EVDial. Приложение не читает пароли и не хранит историю посещений.","Web pages open in your browser's secure tab with the site address visible. Close it to return to EVDial. The app does not read passwords or store browsing history."),14,ElectricUi.MUTED,false);
  note.setPadding(0,dp(20),0,0);root.addView(note,-1,-2);
  root.setFocusableInTouchMode(true);root.requestFocus();getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
 }
 private int dp(int x){return ElectricUi.dp(this,x);} private void add(LinearLayout r,android.view.View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);r.addView(v,p);}
}
