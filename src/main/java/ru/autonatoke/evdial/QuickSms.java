package ru.autonatoke.evdial;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.telephony.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class QuickSms extends DialogFragment {
    private static final String TAG="quick-sms", SENT="ru.autonatoke.evdial.SMS_SENT";
    private static final int ASK=73, PICK=74;
    private final ArrayList<Uri> files=new ArrayList<>();
    private LinearLayout fileRows, channels;
    private ImageButton attach;
    private String number, job="", note="";
    private boolean denied, asking;
    private EditText draft;
    private TextView status;
    private Button submit, thread;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable poll=new Runnable(){ public void run(){ if(refresh()) handler.postDelayed(this,800); } };

    public static void open(Activity a,String number){ open(a,number,null); }
    public static void open(Activity a,String number,String name){
        if(a.isFinishing() || a.getFragmentManager().isStateSaved()
                || a.getFragmentManager().findFragmentByTag(TAG)!=null) return;
        String value=number==null?"":number.trim();
        if(!value.matches("[+0-9(). -]+") || !PhoneNumberUtils.normalizeNumber(value).matches("[+]?[0-9]{1,25}")){
            Toast.makeText(a,"Для SMS нужен телефонный номер",Toast.LENGTH_SHORT).show();return;
        }
        QuickSms editor=new QuickSms(); Bundle args=new Bundle();
        args.putString("number",PhoneNumberUtils.normalizeNumber(value));args.putString("name",name);
        editor.setArguments(args);editor.show(a.getFragmentManager(),TAG);
    }

    @Override public Dialog onCreateDialog(Bundle state){
        Activity a=getActivity();
        number=getArguments().getString("number");
        String name=getArguments().getString("name");
        if(state!=null){
            ArrayList<Uri> restored=state.getParcelableArrayList("files");if(restored!=null){files.clear();files.addAll(restored);}
            job=state.getString("job","");note=state.getString("note","");
            asking=state.getBoolean("asking");denied=state.getBoolean("denied");
        }
        LinearLayout form=new LinearLayout(a);form.setOrientation(LinearLayout.VERTICAL);
        TextView who=new TextView(a);who.setText(name!=null&&!name.trim().isEmpty()&&!name.equals(number)?name+"  ·  "+number:number);
        who.setTextColor(0xffe6efe9);who.setTextSize(17);form.addView(who);
        draft=new EditText(a);draft.setTextColor(Color.WHITE);draft.setHintTextColor(0xffa7b7ad);
        draft.setHint("Текст сообщения");draft.setInputType(1|0x20000|0x4000);draft.setMinLines(2);draft.setMaxLines(5);
        draft.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});
        if(state!=null) draft.setText(state.getString("text",""));
        LinearLayout input=new LinearLayout(a);input.setGravity(Gravity.CENTER_VERTICAL);
        form.addView(input,-1,-2);input.addView(draft,new LinearLayout.LayoutParams(0,-2,1));
        attach=new ImageButton(a);attach.setImageResource(R.drawable.sms_clip);
        attach.setContentDescription("Прикрепить фото или файл");attach.setBackgroundColor(Color.TRANSPARENT);
        int touch=Station.dp(a,48);input.addView(attach,touch,touch);
        fileRows=new LinearLayout(a);fileRows.setOrientation(LinearLayout.VERTICAL);form.addView(fileRows);
        attach.setOnClickListener(v->pickFiles());updateFiles();
        status=new TextView(a);status.setTextColor(0xffacbdb2);status.setTextSize(14);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);form.addView(status);
        submit=button(a,form,"Отправить SMS",true);submit.setOnClickListener(v->attempt());
        thread=button(a,form,"В переписку",false);thread.setBackgroundColor(Color.TRANSPARENT);thread.setOnClickListener(v->compose());
        channels=new LinearLayout(a);channels.setOrientation(LinearLayout.VERTICAL);form.addView(channels);
        MessengerShortcuts.add(a,channels,number,new MessengerShortcuts.Payload(){
            public String text(){return draft.getText().toString();}
            public ArrayList<Uri> files(){return new ArrayList<>(QuickSms.this.files);}
        });
        draft.addTextChangedListener(new TextWatcher(){
            public void afterTextChanged(Editable s){refresh();}
            public void beforeTextChanged(CharSequence s,int st,int n,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){}
        });
        refresh();
        return new StationSheet.Builder(a).setTitle("Быстрое сообщение").setView(form).create();
    }

    private Button button(Activity a,LinearLayout form,String label,boolean primary){
        Button b=new Button(a);b.setText(label);b.setAllCaps(false);b.setTextSize(16);
        GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(Station.dp(a,14));
        bg.setColor(primary?0xff96ecae:0xff26362b);b.setBackground(bg);b.setTextColor(primary?0xff102017:0xffe7f2ea);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Station.dp(a,8);form.addView(b,lp);return b;
    }
    @Override public void onSaveInstanceState(Bundle b){
        super.onSaveInstanceState(b);b.putParcelableArrayList("files",new ArrayList<>(files));b.putString("job",job);b.putString("note",note);
        b.putString("text",draft.getText().toString());b.putBoolean("asking",asking);b.putBoolean("denied",denied);
    }
    @Override public void onResume(){super.onResume();handler.removeCallbacks(poll);poll.run();}
    @Override public void onPause(){handler.removeCallbacks(poll);super.onPause();}
    private void compose(){
        if(!files.isEmpty()){openMms();return;}
        if(!job.isEmpty() && !finished()) return;
        Intent i=new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",number,null));
        // After a submitted job only open the thread; never prefill a possible duplicate.
        if(job.isEmpty())i.putExtra("sms_body", draft.getText().toString());
        try{startActivity(i);dismiss();}catch(RuntimeException e){note="Не удалось открыть приложение сообщений";Toast.makeText(getActivity(),note,Toast.LENGTH_LONG).show();refresh();}
    }
    private void pickFiles(){
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        try{startActivityForResult(picker,PICK);}
        catch(ActivityNotFoundException unavailable){Toast.makeText(getActivity(),"Не удалось открыть выбор файлов",Toast.LENGTH_LONG).show();}
    }
    @Override public void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code!=PICK||result!=Activity.RESULT_OK||data==null)return;
        ClipData clip=data.getClipData();
        if(clip!=null){for(int i=0;i<clip.getItemCount();i++)addFile(clip.getItemAt(i).getUri());}
        else addFile(data.getData());
        updateFiles();refresh();
    }
    private void addFile(Uri uri){
        if(uri!=null&&"content".equals(uri.getScheme())&&!files.contains(uri))files.add(uri);
    }
    private void updateFiles(){
        if(fileRows==null)return;
        fileRows.removeAllViews();
        for(Uri uri:files){
            String name="Вложение";
            try(android.database.Cursor c=getActivity().getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
                if(c!=null&&c.moveToFirst()&&!c.isNull(0))name=c.getString(0);
            }catch(RuntimeException ignored){}
            LinearLayout row=new LinearLayout(getActivity());row.setGravity(Gravity.CENTER_VERTICAL);fileRows.addView(row);
            TextView title=new TextView(getActivity());title.setText(name);title.setTextColor(0xffceebd7);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            Button remove=new Button(getActivity());remove.setText("×");remove.setContentDescription("Убрать "+name);
            remove.setTextColor(0xffceebd7);remove.setBackgroundColor(Color.TRANSPARENT);row.addView(remove,Station.dp(getActivity(),48),Station.dp(getActivity(),48));
            remove.setOnClickListener(v->{files.remove(uri);updateFiles();refresh();});
        }
    }
    private void openMms(){
        Activity a=getActivity();if(a==null||files.isEmpty())return;
        try{
            String body=draft.getText().toString();
            Intent share=MessengerShortcuts.share(a,new ArrayList<>(files),body);
            share.putExtra("sms_body",body);share.putExtra("address",number);
            String pkg=android.provider.Telephony.Sms.getDefaultSmsPackage(a);
            if(pkg==null)throw new ActivityNotFoundException();
            share.setPackage(pkg);
            if("com.android.messaging".equals(pkg)){
                // The AOSP client treats address as SENDTO and drops EXTRA_STREAM.
                // Its attachment flow accepts the image but discards EXTRA_TEXT.
                share.removeExtra("address");
                String type=share.getType();
                boolean supported=type!=null&&(type.startsWith("image/")||(files.size()==1&&
                        (type.startsWith("video/")||type.startsWith("audio/")||type.endsWith("vcard"))));
                if(!supported){
                    Toast.makeText(a,"Для этого файла выберите другое приложение внизу.",Toast.LENGTH_LONG).show();
                    return;
                }
                if(!body.isEmpty()){
                    new StationSheet.Builder(a).setTitle("Фото или файл в Сообщениях")
                            .setMessage("Выберите диалог "+number+". Подпись можно вставить в поле сообщения.")
                            .setPositiveButton("Скопировать подпись и открыть",(d,w)->{
                                android.content.ClipboardManager clipboard=a.getSystemService(android.content.ClipboardManager.class);
                                clipboard.setPrimaryClip(ClipData.newPlainText("Подпись",body));
                                try{startActivity(share);}catch(RuntimeException e){Toast.makeText(a,"Не удалось открыть Сообщения",Toast.LENGTH_LONG).show();}
                            }).setNegativeButton("Назад",null).show();return;
                }
            }
            startActivity(share);
        }catch(RuntimeException unsupported){
            Toast.makeText(a,"Не удалось открыть вложение. Выберите другое приложение внизу.",Toast.LENGTH_LONG).show();
        }
    }
    private int sim(){
        try{return SubscriptionManager.getDefaultSmsSubscriptionId();}catch(RuntimeException e){return -1;}
    }
    private void attempt(){
        Activity a=getActivity();
        if(!files.isEmpty()){openMms();return;}
        if(a==null || !job.isEmpty() || asking || draft.getText().toString().trim().isEmpty())return;
        if(sim()<0){
            note="Выберите SIM в приложении сообщений или назначьте SIM для SMS в настройках телефона.";
            refresh();return;
        }
        if(a.checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED){
            asking=true;refresh();requestPermissions(new String[]{Manifest.permission.SEND_SMS},ASK);return;
        }
        send();
    }
    @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] results){
        super.onRequestPermissionsResult(req,permissions,results);
        if(req!=ASK || !asking)return;
        asking=false;
        if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED){send();return;}
        denied=true;note="Разрешите отправку SMS или откройте переписку с этим черновиком.";refresh();
    }
    private void send(){
        Activity a=getActivity();if(a==null || !job.isEmpty())return;
        int id=sim();if(id<0){note="Выберите SIM для SMS в приложении сообщений.";refresh();return;}
        try{
            SmsManager manager=SmsManager.getSmsManagerForSubscriptionId(id);
            ArrayList<String> parts=manager.divideMessage(draft.getText().toString());
            if(parts.isEmpty())return;
            job=UUID.randomUUID().toString();ArrayList<PendingIntent> callbacks=new ArrayList<>();
            for(int n=0;n<parts.size();n++){
                Intent callback=new Intent(a,Sent.class).setAction(SENT).setData(Uri.parse("evdial-sms://sent/"+job+"/"+n));
                callbacks.add(PendingIntent.getBroadcast(a,0,callback,PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_IMMUTABLE));
            }
            SharedPreferences p=store(a);SharedPreferences.Editor ed=p.edit();
            long now=System.currentTimeMillis();
            for(String key:p.getAll().keySet()){
                if(key.endsWith(".time") && now-p.getLong(key,now)>86400000L){
                    String stale=key.substring(0,key.length()-5);
                    for(String field:p.getAll().keySet())if(field.startsWith(stale+"."))ed.remove(field);
                }
            }
            ed.putLong(job+".time",now).putInt(job+".total",parts.size()).commit();
            refresh();
            if(parts.size()==1)manager.sendTextMessage(number,null,parts.get(0),callbacks.get(0),null);
            else manager.sendMultipartTextMessage(number,null,parts,callbacks,null);
            handler.removeCallbacks(poll);poll.run();
        }catch(RuntimeException e){
            if(!job.isEmpty())store(a).edit().putBoolean(job+".exception",true).apply();
            note="Не удалось передать SMS оператору. Проверьте SIM и разрешение на SMS.";refresh();
        }
    }
    private static SharedPreferences store(Context c){return c.getSharedPreferences("quick_sms_status",Context.MODE_PRIVATE);}
    private boolean finished(){
        SharedPreferences p=store(getActivity());int total=p.getInt(job+".total",0);
        return p.getBoolean(job+".exception",false) || total==0 || p.getInt(job+".seen",0)>=total ||
            System.currentTimeMillis()-p.getLong(job+".time",0)>120000;
    }
    private boolean refresh(){
        Activity a=getActivity();if(a==null||submit==null)return false;
        boolean pending=!job.isEmpty()&&!finished();
        attach.setVisibility(job.isEmpty()&&!asking?View.VISIBLE:View.GONE);
        channels.setVisibility(job.isEmpty()&&!asking?View.VISIBLE:View.GONE);
        thread.setVisibility(files.isEmpty()?View.VISIBLE:View.GONE);
        if(!files.isEmpty()){
            status.setText("Выберите приложение, затем проверьте получателя и вложения.");
            submit.setVisibility(View.VISIBLE);submit.setText("Открыть в Сообщениях");submit.setEnabled(true);submit.setAlpha(1f);
            return false;
        }
        submit.setText("Отправить SMS");thread.setText("В переписку");
        int sub=sim();
        String msg=note.isEmpty()?(sub<0?"Выберите SIM в приложении сообщений.":"SMS через SIM по тарифу оператора."):note;
        if(!job.isEmpty()){
            SharedPreferences p=store(a);
            int total=p.getInt(job+".total",0),seen=p.getInt(job+".seen",0),ok=p.getInt(job+".ok",0);
            if(total>0&&ok==total)msg="SMS отправлено оператором.";
            else if(p.getBoolean(job+".exception",false))msg="Не удалось передать SMS оператору.";
            else if(pending)msg="Отправка SMS…";
            else if(seen<total||total==0)msg="Статус отправки неизвестен. Проверьте переписку.";
            else if(ok>0)msg="Отправлена только часть SMS. Проверьте переписку.";
            else msg="SMS не отправлено. "+p.getString(job+".error","Проверьте связь и SIM.");
        }
        if(!msg.contentEquals(status.getText()))status.setText(msg);
        draft.setEnabled(job.isEmpty()&&!asking);
        boolean fallback=job.isEmpty()&&(denied || sub<0);
        submit.setVisibility(job.isEmpty()&&!fallback?View.VISIBLE:View.GONE);
        boolean active=!asking && draft.getText().toString().trim().length()>0;
        submit.setEnabled(active);submit.setAlpha(active?1f:0.52f);
        thread.setEnabled(!pending&&!asking);thread.setAlpha(thread.isEnabled()?1f:0.52f);
        return pending;
    }
    public static class Sent extends BroadcastReceiver{
        @Override public void onReceive(Context c,Intent i){
            if(!SENT.equals(i.getAction()) || i.getData()==null)return;
            List<String> path=i.getData().getPathSegments();if(path.size()!=2)return;
            String job=path.get(0);int part;
            try{UUID.fromString(job);part=Integer.parseInt(path.get(1));}catch(IllegalArgumentException e){return;}
            SharedPreferences p=store(c);int total=p.getInt(job+".total",0);
            if(part<0||part>=total||p.contains(job+".part"+part))return;
            int code=getResultCode();
            SharedPreferences.Editor ed=p.edit().putBoolean(job+".part"+part,true).putInt(job+".seen",p.getInt(job+".seen",0)+1);
            if(code==Activity.RESULT_OK)ed.putInt(job+".ok",p.getInt(job+".ok",0)+1);
            else{
                String error="Ошибка оператора ("+code+").";
                if(code==SmsManager.RESULT_ERROR_NO_SERVICE)error="Нет сети.";
                else if(code==SmsManager.RESULT_ERROR_RADIO_OFF)error="Радиомодуль выключен.";
                else if(code==SmsManager.RESULT_ERROR_LIMIT_EXCEEDED)error="Достигнут лимит отправки.";
                ed.putString(job+".error",error);
            }
            ed.apply();
        }
    }
}
