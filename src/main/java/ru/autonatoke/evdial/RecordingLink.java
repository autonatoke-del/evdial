package ru.autonatoke.evdial;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioFocusRequest;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.*;
import java.io.IOException;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RecordingLink extends DialogFragment {
    private static final String TAG="recording-player", TREE="tree";
    private static final int FOLDER=1, FILE=2, READ=Intent.FLAG_GRANT_READ_URI_PERMISSION;
    private static final String[] COLUMNS={Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private CancellationSignal scanning;
    private int generation;
    private String number, key;
    private long time;
    private Uri selected;
    private MediaPlayer player;
    private boolean preparing, prepared, autoplay;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private static final AudioAttributes SPEECH=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private TextView status;
    private Button play, detach;
    private SeekBar seek;
    private final Runnable tick=new Runnable(){public void run(){
        if(player!=null && prepared){
            try{seek.setProgress(player.getCurrentPosition());if(player.isPlaying())ui.postDelayed(this,400);}
            catch(IllegalStateException ignored){}
        }
    }};

    public static void open(Activity a,String number,long time){
        open(a,number,time,false,false);
    }
    public static void open(Activity a,String number,long time,boolean ambiguousNeighbor,boolean simCall){
        if(a.isFinishing()||a.getFragmentManager().isStateSaved()||a.getFragmentManager().findFragmentByTag(TAG)!=null)return;
        RecordingLink panel=new RecordingLink();Bundle b=new Bundle();
        b.putBoolean("neighbor",ambiguousNeighbor);b.putBoolean("sim",simCall);b.putString("number",number);b.putLong("time",time);panel.setArguments(b);panel.show(a.getFragmentManager(),TAG);
    }
    public static boolean hasLink(Activity a,String number,long time){return prefs(a).contains(callKey(number,time));}
    private static String callKey(String number,long time){return "call/"+RecordingNames.keyNumber(number)+"/"+time;}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("local_recording_links",Context.MODE_PRIVATE);}

    @Override public Dialog onCreateDialog(Bundle state){
        Activity a=getActivity();number=getArguments().getString("number","");time=getArguments().getLong("time");key=callKey(number,time);
        audio=a.getSystemService(AudioManager.class);
        autoplay=state==null;
        LinearLayout form=new LinearLayout(a);form.setOrientation(LinearLayout.VERTICAL);
        status=new TextView(a);status.setTextColor(0xffdbe8df);status.setTextSize(15);form.addView(status);
        seek=new SeekBar(a);seek.setEnabled(false);form.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){if(prepared && player!=null)player.seekTo(s.getProgress());}
            public void onProgressChanged(SeekBar s,int value,boolean user){}
        });
        play=button(form,"Слушать запись",()->toggle());play.setEnabled(false);
        button(form,"Выбрать аудиофайл",()->pick(FILE));
        button(form,"Папка записей Cube ACR",()->pick(FOLDER));
        detach=button(form,"Отвязать файл",()->{
            String old=prefs(a).getString(key,null);prefs(a).edit().remove(key).apply();releaseUnusedGrant(a,old);load();
        });
        return new StationSheet.Builder(a).setTitle("Запись разговора").setView(form).setNegativeButton("Закрыть",null).create();
    }
    private Button button(LinearLayout form,String label,Runnable action){
        Button b=new Button(getActivity());b.setText(label);b.setAllCaps(false);b.setGravity(Gravity.CENTER);
        boolean primary=label.equals("Слушать запись");
        int pad=Station.dp(getActivity(),12);
        b.setMinHeight(Station.dp(getActivity(),48));b.setPadding(pad,0,pad,0);
        b.setTextSize(15);b.setTextColor(primary?0xff0e2117:0xffd9f3df);
        android.graphics.drawable.GradientDrawable face=new android.graphics.drawable.GradientDrawable();
        face.setCornerRadius(Station.dp(getActivity(),16));face.setColor(primary?0xff96ecae:0xff203027);
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x5583da9c),face,null));
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Station.dp(getActivity(),5);
        form.addView(b,lp);return b;
    }
    private void pick(int code){
        autoplay=false;
        Intent i=new Intent(code==FOLDER?Intent.ACTION_OPEN_DOCUMENT_TREE:Intent.ACTION_OPEN_DOCUMENT)
            .addFlags(READ|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION).putExtra(Intent.EXTRA_LOCAL_ONLY,true);
        if(code==FILE)i.addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try{startActivityForResult(i,code);}catch(ActivityNotFoundException e){status.setText("Выбор файлов на устройстве недоступен.");}
    }
    @Override public void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();Activity a=getActivity();if(a==null)return;
        if(!"content".equals(uri.getScheme())){status.setText("Выберите файл в памяти телефона.");return;}
        try{
            if(code==FILE && !RecordingNames.audio(displayName(a,uri),a.getContentResolver().getType(uri))){
                status.setText("Выберите аудиозапись разговора.");return;
            }
            a.getContentResolver().takePersistableUriPermission(uri,READ);
            String pref=code==FOLDER?TREE:key;
            String old=prefs(a).getString(pref,null);
            prefs(a).edit().putString(pref,uri.toString()).apply();releaseUnusedGrant(a,old);load();
        }catch(RuntimeException e){status.setText("Доступ не сохранён. Выберите файл или папку в памяти телефона.");}
    }
    private static void releaseUnusedGrant(Context c,String old){
        if(old==null||prefs(c).getAll().containsValue(old))return;
        try{c.getContentResolver().releasePersistableUriPermission(Uri.parse(old),READ);}catch(RuntimeException ignored){}
    }
    private static String displayName(Context c,Uri uri){
        try(Cursor cur=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            return cur!=null&&cur.moveToFirst()?cur.getString(0):null;
        }
    }
    @Override public void onStart(){super.onStart();load();}
    @Override public void onStop(){cancel();super.onStop();}
    @Override public void onDestroy(){cancel();worker.shutdownNow();super.onDestroy();}
    private void abandonFocus(){
        if(focus!=null && audio!=null){audio.abandonAudioFocusRequest(focus);focus=null;}
    }
    private boolean acquireFocus(){
        Activity a=getActivity();if(a==null || audio==null)return false;
        boolean inCall=((Phone)a.getApplication()).active!=null || (MobileCalls.live!=null && MobileCalls.live.current()!=null);
        // Use InCallService/SIP state and audio mode, without an unnecessary READ_PHONE_STATE request.
        int mode=audio.getMode();
        if(inCall || mode==AudioManager.MODE_IN_CALL || mode==AudioManager.MODE_IN_COMMUNICATION || mode==AudioManager.MODE_RINGTONE){
            status.setText("Запись можно прослушать после звонка.");return false;
        }
        if(focus!=null)return true;
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(SPEECH).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(change->{
                if(change>=0 || focus==null)return;
                if(preparing){cancel();play.setEnabled(true);play.setText("Слушать запись");}
                else if(prepared && player!=null){
                    if(player.isPlaying())player.pause();
                    play.setText("Продолжить");abandonFocus();
                }
            },ui).build();
        if(audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return true;
        focus=null;status.setText("Звук сейчас занят. Попробуйте ещё раз после звонка.");return false;
    }
    private void cancel(){
        generation++;if(scanning!=null){scanning.cancel();scanning=null;}
        ui.removeCallbacks(tick);
        if(player!=null){player.release();player=null;}
        prepared=false;preparing=false;abandonFocus();
    }
    private void load(){
        cancel();selected=null;play.setEnabled(false);play.setText("Слушать запись");seek.setEnabled(false);seek.setProgress(0);
        Context c=getActivity().getApplicationContext();String manual=prefs(c).getString(key,null),tree=prefs(c).getString(TREE,null);
        detach.setVisibility(manual==null?android.view.View.GONE:android.view.View.VISIBLE);
        if(manual!=null){
            found(Uri.parse(manual),"Файл привязан к этому звонку.");
            return;
        }
        if(!getArguments().getBoolean("sim")||getArguments().getBoolean("neighbor")){
            status.setText("Для этого звонка выберите файл вручную.");return;
        }
        if(tree==null){status.setText("Выберите папку Cube ACR для поиска записи или прикрепите файл вручную.");return;}
        int token=generation;CancellationSignal signal=new CancellationSignal();scanning=signal;
        status.setText("Ищем запись по номеру и времени…");
        Runnable timeout=()->{
            if(generation==token && scanning==signal && isAdded()){
                generation++;scanning=null;signal.cancel();
                status.setText("Папка слишком велика или не отвечает. Выберите папку поменьше или отдельный файл.");
            }
        };
        ui.postDelayed(timeout,15500);
        worker.execute(()->{
            Uri answer=null;String message;
            try{
                answer=find(c,Uri.parse(tree),number,time,signal);
                message=answer==null?"Однозначного совпадения нет. Выберите файл вручную.":"Найдена запись с этим номером и временем.";
            }catch(android.os.OperationCanceledException stopped){return;}
            catch(Exception e){message="Поиск не завершён. Выберите папку с записями ещё раз или прикрепите файл.";}
            Uri matched=answer;String text=message;
            ui.post(()->{ui.removeCallbacks(timeout);if(generation==token && isAdded()){scanning=null;found(matched,text);}});
        });
    }
    private void found(Uri uri,String message){
        selected=uri;status.setText(message);play.setEnabled(uri!=null);
        if(autoplay && uri!=null){autoplay=false;toggle();}
    }

    private static Uri find(Context c,Uri tree,String number,long time,CancellationSignal signal)throws IOException{
        ArrayDeque<String> queue=new ArrayDeque<>();Set<String> visited=new HashSet<>();
        queue.add(DocumentsContract.getTreeDocumentId(tree));int entries=0;Uri match=null;
        ZoneId zone=ZoneId.systemDefault();long deadline=SystemClock.elapsedRealtime()+15000;
        while(!queue.isEmpty()){
            signal.throwIfCanceled();String dir=queue.removeFirst();if(!visited.add(dir))continue;
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,dir);
            try(Cursor cur=c.getContentResolver().query(children,COLUMNS,null,null,null,signal)){
                if(cur==null)throw new IOException();
                while(cur.moveToNext()){
                    signal.throwIfCanceled();
                    if(++entries>100000||SystemClock.elapsedRealtime()>deadline)throw new IOException();
                    String id=cur.getString(0),name=cur.getString(1),type=cur.getString(2);
                    if(Document.MIME_TYPE_DIR.equals(type)){queue.add(id);continue;}
                    if(RecordingNames.audio(name,type)&&RecordingNames.matches(name,number,time,zone)){
                        Uri next=DocumentsContract.buildDocumentUriUsingTree(tree,id);
                        if(match!=null&&!match.equals(next))return null;
                        match=next;
                    }
                }
            }
        }
        return match;
    }
    private void toggle(){
        if(selected==null||preparing)return;
        if(prepared&&player!=null){
            if(player.isPlaying()){player.pause();abandonFocus();play.setText("Продолжить");}
            else if(acquireFocus()){player.start();play.setText("Пауза");tick.run();}
            return;
        }
        if(!acquireFocus())return;
        play.setEnabled(false);preparing=true;int token=generation;
        Context c=getActivity().getApplicationContext();Uri uri=selected;
        worker.execute(()->{
            ParcelFileDescriptor fd=null;
            try{fd=c.getContentResolver().openFileDescriptor(uri,"r");}catch(Exception ignored){}
            ParcelFileDescriptor opened=fd;
            ui.post(()->{
                try(ParcelFileDescriptor source=opened){
                    if(generation!=token || !isAdded())return;
                    if(source==null)throw new IOException();
                    player=new MediaPlayer();
                    player.setAudioAttributes(SPEECH);
                    player.setDataSource(source.getFileDescriptor());
                    player.setOnPreparedListener(p->{
                        if(p!=player)return;
                        preparing=false;prepared=true;
                        seek.setMax(Math.max(1,p.getDuration()));seek.setEnabled(true);play.setEnabled(true);
                        p.start();play.setText("Пауза");tick.run();
                    });
                    player.setOnCompletionListener(p->{if(p==player){p.seekTo(0);abandonFocus();seek.setProgress(0);play.setText("Слушать снова");}});
                    player.setOnErrorListener((p,w,e)->{if(p==player)failed();return true;});
                    player.prepareAsync();
                }catch(Exception e){if(generation==token)failed();}
            });
        });
    }
    private void failed(){cancel();seek.setEnabled(false);play.setEnabled(false);status.setText("Не удалось воспроизвести запись. Выберите доступный аудиофайл ещё раз.");}
}
