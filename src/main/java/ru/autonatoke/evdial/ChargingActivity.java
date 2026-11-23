package ru.autonatoke.evdial;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class ChargingActivity extends Activity {
    private static final int BG=0xff0a100e, FG=0xffeff7f2, MINT=0xff91f5bd, MUTED=0xffb2c3b9;
    private final ArrayList<String[]> stations=new ArrayList<>(), matches=new ArrayList<>();
    private LinearLayout root;
    private EditText search;
    private String query="", detailId="";
    private Rows adapter;
    private TextView count;
    private int first, offset;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if(state!=null) {query=state.getString("q",""); detailId=state.getString("id",""); first=state.getInt("first"); offset=state.getInt("offset");}
        try(BufferedReader in=new BufferedReader(new InputStreamReader(getAssets().open("charging.tsv"),StandardCharsets.UTF_8))){
            for(String line;(line=in.readLine())!=null;) {
                String[] row=line.split("\t",-1);
                if(row.length==10) stations.add(row);
            }
        } catch(IOException ignored) {}
        for(String[] s:stations) if(s[0].equals(detailId)){detail(s);return;}
        directory();
    }
    private int dp(int x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private GradientDrawable surface(int color,int radius){
        GradientDrawable s=new GradientDrawable();s.setColor(color);s.setCornerRadius(dp(radius));return s;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView label(String text,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(true);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView action(String title,Runnable r){
        TextView t=label(title,16,MINT,true);t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(48));t.setPadding(dp(12),dp(6),dp(12),dp(6));t.setBackground(surface(0xff16271e,14));t.setOnClickListener(v->r.run()); return t;
    }
    private void begin(){
        root=column();root.setFocusableInTouchMode(true);root.setBackgroundColor(BG);
        root.setPadding(dp(16),dp(6),dp(16),dp(4));setContentView(root);

    }
    private void add(LinearLayout parent,View item,int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(item,p);
    }
    private void open(String url){
        if(url.startsWith("https://")||url.startsWith("http://")){EvLinks.open(this,url);return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(ActivityNotFoundException e){Toast.makeText(this,"Для ссылки нужен браузер или карты",Toast.LENGTH_LONG).show();}
    }
    private void mapSearch(){open("https://yandex.ru/maps/?text="+Uri.encode("зарядные станции для электромобилей "+query.trim()));}
    private ListView list;
    private void directory(){
        detailId="";begin();
        list=new ListView(this); list.setDivider(null); list.setCacheColorHint(0); list.setVerticalScrollBarEnabled(false);
        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=action("‹",this::finish);
        back.setTextSize(26);back.setGravity(Gravity.CENTER);back.setContentDescription("Вернуться к телефону");bar.addView(back,dp(48),-2);
        root.addView(bar,-1,-2);
        LinearLayout header=column();
        add(header,label("Зарядные станции",28,FG,true),10);
        add(header,label("Россия · офлайн-выборка 22.04.2026",14,MUTED,false),2);
        search=new EditText(this);search.setTextColor(FG);search.setHintTextColor(MUTED);search.setTextSize(16);search.setSingleLine(true);
        search.setHint("Город или адрес");search.setContentDescription("Поиск станции по городу, адресу или названию");
        search.setPadding(dp(14),dp(10),dp(14),dp(10));search.setMinHeight(dp(48));search.setBackground(surface(0xff1b2922,16));
        search.setText(query);search.setSelection(search.length());
        LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(0,-2,1);searchParams.leftMargin=dp(8);bar.addView(search,searchParams);
        add(header,action("Мировая карта Open Charge Map ↗",()->open("https://openchargemap.org/site/poi")),6);add(header,action("Яндекс Карты  ↗",this::mapSearch),6);
        add(header,action("Энергия Москвы  ↗",()->open("https://transport.mos.ru/electro/map")),4);
        count=label("",14,MUTED,false);count.setPadding(0,dp(10),0,dp(8));add(header,count,0);
        list.addHeaderView(header,null,false);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));adapter=new Rows();list.setAdapter(adapter);filter();
        list.setSelectionFromTop(first,offset);
        list.setOnItemClickListener((parent,view,pos,id)->{
            saveScroll();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);
            int index=pos-list.getHeaderViewsCount();
            if(index>=0 && index<matches.size())detail(matches.get(index));
        });
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int before,int n){query=s.toString();filter();list.setSelection(0);}
            public void afterTextChanged(Editable s){}
        });
    }
    private void saveScroll(){if(list!=null){first=list.getFirstVisiblePosition();View v=list.getChildAt(0);offset=v==null?0:v.getTop();}}
    private static String normalized(String s){return s.toLowerCase(Locale.ROOT).replace('ё','е').replace("moscow","москва").replace("saint petersburg","санкт-петербург");}
    private void filter(){
        matches.clear();String[] words=normalized(query.trim()).split("\\s+");
        for(String[] r:stations){String hay=normalized(r[1]+" "+r[2]+" "+r[3]+" "+r[4]);boolean ok=true;for(String word:words)if(!hay.contains(word)){ok=false;break;}if(ok)matches.add(r);}
        count.setText(matches.isEmpty()?"Совпадений нет. Попробуйте карты выше.":"Найдено: "+matches.size()+"  ·  Занятость уточняйте у оператора");
        adapter.notifyDataSetChanged();
    }
    private class Rows extends BaseAdapter{
        public int getCount(){return matches.size();}
        public Object getItem(int i){return matches.get(i);}
        public long getItemId(int i){return Long.parseLong(matches.get(i)[0]);}
        public View getView(int i,View v,ViewGroup p){
            LinearLayout row=(LinearLayout)v;
            if(row==null){row=column();row.setPadding(dp(4),dp(12),dp(4),dp(8)); row.addView(label("",17,FG,true));row.addView(label("",14,MUTED,false));View rule=new View(ChargingActivity.this);rule.setBackgroundColor(0xff27372e);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(1));rp.topMargin=dp(8);row.addView(rule,rp);}
            String[] s=matches.get(i);((TextView)row.getChildAt(0)).setText(s[1]);((TextView)row.getChildAt(1)).setText(address(s));return row;
        }
    }
    private String address(String[] s){ArrayList<String> parts=new ArrayList<>();for(String part:new String[]{s[2],s[3]}) if(!part.isEmpty()&&!parts.contains(part))parts.add(part);return android.text.TextUtils.join(" · ",parts);}
    private void detail(String[] s){
        detailId=s[0];list=null;begin();ScrollView scroll=new ScrollView(this);LinearLayout body=column();scroll.addView(body);root.addView(scroll);
        add(body,action("‹   К станциям",this::directory),0);add(body,label("EVDial   /   ЭЗС",13,MINT,true),22);
        add(body,label(s[1],27,FG,true),8);add(body,label(address(s),17,MUTED,false),6);
        add(body,label(s[7],17,FG,false),18);
        if(!s[8].isEmpty())add(body,label("Разъёмы в справочнике: "+s[8],15,MUTED,false),6);
        if(!s[9].isEmpty())add(body,label("Дата проверки в OCM: "+s[9],14,MUTED,false),5);
        add(body,label("Условия доступа и занятость проверьте у оператора перед поездкой.",14,MUTED,false),5);
        add(body,action("Построить маршрут  ↗",()->open("https://yandex.ru/maps/?rtext=~"+s[5]+","+s[6]+"&rtt=auto")),18);
        add(body,action("Показать на карте  ↗",()->open("https://yandex.ru/maps/?pt="+s[6]+","+s[5]+",pm2gnm&z=17")),6);
        add(body,action("Запись Open Charge Map  ↗",()->open("https://www.openchargemap.org/site/poi/details/"+s[0])),14);
        add(body,label("Координаты и сведения © Open Charge Map Contributors. Российская выборка, отфильтрована и переведена. Снимок 22.04.2026.",13,MUTED,false),12);
        add(body,action("Лицензия CC BY 4.0  ↗",()->open("https://creativecommons.org/licenses/by/4.0/")),5);
    }
    @Override public void onBackPressed(){if(!detailId.isEmpty())directory();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);saveScroll();b.putString("q",query);b.putString("id",detailId);b.putInt("first",first);b.putInt("offset",offset);}
}
