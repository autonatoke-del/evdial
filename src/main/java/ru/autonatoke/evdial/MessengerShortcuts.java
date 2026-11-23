package ru.autonatoke.evdial;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

/** Transfer a user-selected draft to a specific locally installed messenger. */
public final class MessengerShortcuts {
    private MessengerShortcuts(){}
    public interface Payload { String text(); ArrayList<Uri> files(); }

    private enum App {
        TELEGRAM("Telegram","org.telegram.messenger","org.telegram.messenger.web","org.thunderdog.challegram"),
        WHATSAPP("WhatsApp","com.whatsapp","com.whatsapp.w4b"),
        MAX("MAX","ru.oneme.app");
        final String label;
        final String[] packages;
        App(String label,String... packages){this.label=label;this.packages=packages;}
    }

    public static void add(Activity a,LinearLayout form,String number,Payload payload){
        LinearLayout row=new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        form.addView(row,-1,-2);
        for(App app:App.values()){
            if(installed(a,app).isEmpty())continue;
            int icon=app==App.TELEGRAM?R.drawable.msg_telegram:app==App.WHATSAPP?R.drawable.msg_whatsapp:R.drawable.msg_max;
            channel(a,row,app.label,icon,()->choose(a,app,number,payload));
        }
        channel(a,row,row.getChildCount()==0?"Выбрать приложение":"Ещё",R.drawable.msg_other,()->{
            String text=payload.text();
            ArrayList<Uri> files=payload.files();
            if(text.trim().isEmpty()&&files.isEmpty()){
                hint(a,"Введите текст или прикрепите файл, чтобы выбрать приложение для отправки.");return;
            }
            Intent picker=Intent.createChooser(share(a,files,text),"Выберите приложение и получателя");
            if(!start(a,picker))hint(a,"Выбор приложения недоступен. Черновик сохранён.");
        });
    }

    private static void channel(Activity a,LinearLayout row,String label,int icon,Runnable open){
        TextView button=new TextView(a);
        button.setText(label);button.setCompoundDrawablesWithIntrinsicBounds(0,icon,0,0);
        button.setCompoundDrawablePadding(Station.dp(a,2));
        button.setPadding(0,Station.dp(a,5),0,Station.dp(a,4));
        button.setTextSize(13);button.setGravity(Gravity.CENTER);
        button.setMinHeight(Station.dp(a,48));button.setTextColor(0xffe2f7e8);button.setFocusable(true);
        GradientDrawable chip=new GradientDrawable();
        chip.setCornerRadius(Station.dp(a,12));chip.setColor(0xff213d2c);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x4076b990),chip,null));
        button.setOnClickListener(v->open.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        if(row.getChildCount()>0)lp.leftMargin=Station.dp(a,5);
        lp.topMargin=Station.dp(a,6);row.addView(button,lp);
    }

    private static ArrayList<ApplicationInfo> installed(Activity a,App app){
        ArrayList<ApplicationInfo> found=new ArrayList<>();
        for(String pkg:app.packages){
            try{
                ApplicationInfo info=a.getPackageManager().getApplicationInfo(pkg,0);
                if(info.enabled)found.add(info);
            }catch(PackageManager.NameNotFoundException ignored){}
        }
        return found;
    }

    private static void choose(Activity a,App app,String number,Payload payload){
        ArrayList<ApplicationInfo> versions=installed(a,app);
        if(versions.isEmpty()){hint(a,app.label+" не установлен");return;}
        if(versions.size()==1){open(a,app,versions.get(0).packageName,number,payload);return;}
        String[] names=new String[versions.size()];
        for(int i=0;i<names.length;i++){
            ApplicationInfo info=versions.get(i);names[i]=info.loadLabel(a.getPackageManager()).toString();
            if(info.packageName.equals("org.telegram.messenger.web"))names[i]+=" (telegram.org)";
        }
        new StationSheet.Builder(a).setTitle("Открыть в приложении")
            .setItems(names,(dialog,i)->open(a,app,versions.get(i).packageName,number,payload)).show();
    }

    private static String international(Activity a,String raw){
        String country=null;
        try{
            TelephonyManager tel=a.getSystemService(TelephonyManager.class);
            if(tel!=null)country=tel.getSimCountryIso();
        }catch(RuntimeException ignored){}
        return international(raw,country);
    }

    private static String international(String raw,String country){
        if(raw==null || !raw.trim().matches("[+0-9 ()\\-.]+"))return null;
        String n=PhoneNumberUtils.normalizeNumber(raw);
        if(n.matches("[+][1-9][0-9]{5,14}"))return n.substring(1);
        if(n.startsWith("+")||n.length()<6)return null;
        // Russian trunk numbers stay attached to their original recipient while roaming.
        if(n.matches("8[3489][0-9]{9}")){
            String ru=PhoneNumberUtils.formatNumberToE164(n,"RU");
            if(ru!=null&&ru.equals("+7"+n.substring(1)))return ru.substring(1);
        }
        if(country==null||country.isEmpty())return null;
        String formatted=PhoneNumberUtils.formatNumberToE164(n,country.toUpperCase(Locale.ROOT));
        return formatted!=null&&formatted.matches("[+][1-9][0-9]{5,14}")?formatted.substring(1):null;
    }
    private static void open(Activity a,App app,String pkg,String number,Payload payload){
        String text=payload.text();if(text==null)text="";
        ArrayList<Uri> files=payload.files();
        files=files==null?new ArrayList<>():new ArrayList<>(files);
        for(Uri uri:files){
            if(uri==null || !"content".equals(uri.getScheme())){hint(a,"Не удалось открыть вложение");return;}
        }
        String digits=international(a,number);
        if(files.isEmpty() && app!=App.MAX && digits!=null){
            Uri.Builder link=app==App.TELEGRAM?new Uri.Builder().scheme("tg").authority("resolve").appendQueryParameter("phone",digits)
                    :new Uri.Builder().scheme("https").authority("wa.me").appendPath(digits);
            if(!text.isEmpty())link.appendQueryParameter("text",text);
            if(start(a,new Intent(Intent.ACTION_VIEW,link.build()).setPackage(pkg)))return;
        }
        boolean opened;
        if(!text.isEmpty()||!files.isEmpty()){
            opened=start(a,share(a,files,text).setPackage(pkg));
        }else{
            Intent launch=a.getPackageManager().getLaunchIntentForPackage(pkg);
            opened=launch!=null && start(a,launch);
        }
        hint(a,opened?"Выберите получателя в "+app.label:"Не удалось открыть "+app.label+". Черновик сохранён.");
    }

    static Intent share(Activity a,ArrayList<Uri> files,String text){
        Intent out=new Intent(files.size()>1?Intent.ACTION_SEND_MULTIPLE:Intent.ACTION_SEND);
        if(!text.isEmpty())out.putExtra(Intent.EXTRA_TEXT,text);
        if(files.isEmpty())return out.setType("text/plain");
        String common=null;
        for(Uri uri:files){
            String type=null;
            try{type=a.getContentResolver().getType(uri);}catch(RuntimeException ignored){}
            if(type==null || !type.contains("/"))type="application/octet-stream";
            if(common==null)common=type;
            else if(!common.equals(type)){
                String group=common.substring(0,common.indexOf('/'));
                common=type.startsWith(group+"/")?group+"/*":"*/*";
            }
        }
        out.setType(common);
        if(files.size()==1)out.putExtra(Intent.EXTRA_STREAM,files.get(0));
        else out.putParcelableArrayListExtra(Intent.EXTRA_STREAM,files);
        ClipData clip=new ClipData("Вложения",new String[]{common},new ClipData.Item(files.get(0)));
        for(int i=1;i<files.size();i++)clip.addItem(new ClipData.Item(files.get(i)));
        out.setClipData(clip);out.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return out;
    }
    private static boolean start(Activity a,Intent intent){
        try{a.startActivity(intent);return true;}catch(RuntimeException e){return false;}
    }
    private static void hint(Activity a,String message){Toast.makeText(a,message,Toast.LENGTH_LONG).show();}
}
