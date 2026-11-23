package ru.autonatoke.evdial;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.net.Uri;
import android.telecom.Call;
import android.telecom.TelecomManager;
import android.text.InputFilter;
import android.text.InputType;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Objects;
import org.linphone.core.Address;
import org.linphone.core.Reason;

/** Reply only to the specific ringing call and recipient the user reviewed. */
final class IncomingReply {
    private static final String[][] REPLIES={
            {"Перезвоню", "Сейчас не могу говорить. Перезвоню."},
            {"За рулём", "Я за рулём. Перезвоню позже."},
            {"Напишите", "Напишите, пожалуйста, в сообщении."}
    };

    private interface Target {
        boolean ringing();
        String recipient();
        boolean canRejectWithText();
        void reject(String text);
    }

    private final Activity activity;
    private Target target;
    private String recipient;
    private StationSheet sheet;
    private boolean watchingScreen;
    private final BroadcastReceiver screenOff=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){close();}
    };

    IncomingReply(Activity activity){this.activity=activity;}

    static String mobileRecipient(Call call){
        if(call==null||call.getDetails()==null)return null;
        Call.Details details=call.getDetails();
        Uri handle=details.getHandle();
        if(details.getHandlePresentation()!=TelecomManager.PRESENTATION_ALLOWED||
                handle==null||!"tel".equalsIgnoreCase(handle.getScheme()))return null;
        return phoneNumber(handle.getSchemeSpecificPart(),false);
    }
    static String sipRecipient(org.linphone.core.Call call){
        if(call==null)return null;
        Address remote=call.getRemoteAddress();
        return remote==null?null:phoneNumber(remote.getUsername(),true);
    }
    private static String phoneNumber(String raw,boolean sip){
        if(raw==null)return null;
        String n=raw.replaceAll("[()\\s.-]","");
        if(sip){
            if(n.matches("[78][0-9]{10}"))return "+7"+n.substring(1);
            return n.matches("\\+[1-9][0-9]{6,14}")?n:null;
        }
        return n.matches("\\+?[0-9]{2,15}")?n:null;
    }
    void openMobile(MobileCalls service, Call call){
        open(new Target(){
            public boolean ringing(){return MobileCalls.live==service&&service.current()==call&&call.getState()==Call.STATE_RINGING;}
            public String recipient(){return mobileRecipient(call);}
            public boolean canRejectWithText(){
                return call.getDetails()!=null&&call.getDetails().can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT);
            }
            public void reject(String text){call.reject(text!=null,text);}
        });
    }
    void openSip(Phone phone,org.linphone.core.Call call){
        open(new Target(){
            public boolean ringing(){return phone.active==call&&phone.ringing();}
            public String recipient(){return sipRecipient(call);}
            public boolean canRejectWithText(){return false;}
            public void reject(String text){
                if(call.decline(Reason.Declined)!=0)throw new IllegalStateException("SIP decline failed");
            }
        });
    }

    private void open(Target choice){
        if(target!=null||!choice.ringing())return;
        String number=choice.recipient();
        if(number==null)return;
        target=choice;
        recipient=number;
        activity.registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF));
        watchingScreen=true;
        KeyguardManager keyguard=activity.getSystemService(KeyguardManager.class);
        if(locked()){
            keyguard.requestDismissKeyguard(activity,new KeyguardManager.KeyguardDismissCallback(){
                @Override public void onDismissSucceeded(){
                    if(target!=choice)return;
                    if(valid()&&!locked())editor(choice, "");else close();
                }
                @Override public void onDismissCancelled(){if(target==choice)close();}
                @Override public void onDismissError(){if(target==choice)close();}
            });
        }else editor(choice, "");
    }

    private boolean locked(){
        KeyguardManager manager=activity.getSystemService(KeyguardManager.class);
        return manager!=null&&(manager.isDeviceLocked()||manager.isKeyguardLocked());
    }
    private boolean valid(){
        return !activity.isFinishing()&&!activity.isDestroyed()&&target!=null&&target.ringing()
                &&Objects.equals(recipient,target.recipient());
    }
    void reconcile(){if(target!=null&&!valid())close();}
    void close(){
        target=null;recipient=null;
        if(watchingScreen){watchingScreen=false;activity.unregisterReceiver(screenOff);}
        StationSheet current=sheet;sheet=null;
        if(current!=null&&current.isShowing())current.dismiss();
    }
    private void editor(Target choice,String initialText){
        if(target!=choice||!valid()||locked()){close();return;}
        boolean direct=choice.canRejectWithText();
        String number=recipient;
        LinearLayout form=new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText field=new EditText(activity);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|
                InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setGravity(Gravity.START|Gravity.TOP);
        field.setHint("Свой текст или быстрый ответ");
        field.setTextColor(0xffedf4ef);
        field.setHintTextColor(0xffaabbaf);
        field.setMinLines(2);field.setMaxLines(5);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        field.setText(initialText);field.setSelection(field.length());
        form.addView(field,-1,-2);

        HorizontalScrollView choices=new HorizontalScrollView(activity);
        choices.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(activity);
        for(String[] preset:REPLIES){
            TextView chip=new TextView(activity);
            chip.setText(preset[0]);chip.setTextSize(14);chip.setTextColor(0xffb7e9c7);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12),dp(4),dp(12),dp(4));
            chip.setMinHeight(dp(48));
            chip.setSingleLine(true);
            chip.setBackground(new RippleDrawable(ColorStateList.valueOf(0x4094edac),
                    Station.plate(activity,14,0xff233a2b,0xff1a2a20),null));
            chip.setFocusable(true);
            chip.setContentDescription("Подставить ответ. "+preset[1]);
            chip.setOnClickListener(v->{field.setText(preset[1]);field.setSelection(field.length());});
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,-2);
            size.setMarginEnd(dp(6));
            row.addView(chip,size);
        }
        choices.addView(row);
        form.addView(choices,-1,-2);
        String action=direct?"Отклонить и отправить SMS":"Отклонить и открыть SMS";
        String hint=direct?"Ответ через SIM":"Черновик откроется в приложении SMS. Нажмите «Отправить» там.";
        StationSheet dialog=new StationSheet.Builder(activity).setTitle("Ответить сообщением")
                .setMessage(number+"  ·  "+hint).setView(form).setPositiveButton(action,null)
                .setNegativeButton("Отмена",(d,w)->{if(target==choice)close();}).create();
        sheet=dialog;
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnCancelListener(d->{if(sheet==dialog)close();});
        dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{
            if(sheet!=dialog||target!=choice)return;
            if(!valid()||locked()){close();return;}
            String message=field.getText().toString().trim();
            if(message.isEmpty()){field.setError("Введите текст или выберите быстрый ответ");return;}
            if(direct!=choice.canRejectWithText()){
                dialog.dismiss();editor(choice,message);return;
            }
            // Disarm the editor before any system or SIP call can trigger a callback.
            close();
            submit(choice,number,message,direct);
        });
    }

    private int dp(int value){return Station.dp(activity,value);}

    private void submit(Target call,String number,String text,boolean direct){
        if(!call.ringing()||!Objects.equals(number,call.recipient())||locked())return;
        if(direct){
            try{call.reject(text);}
            catch(RuntimeException unavailable){
                Toast.makeText(activity,"Android не принял команду отправки SMS",Toast.LENGTH_LONG).show();
            }
            return;
        }
        Intent draft=new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",number,null))
                .putExtra("sms_body",text);
        try{activity.startActivity(draft);}
        catch(ActivityNotFoundException|SecurityException unavailable){
            Toast.makeText(activity,"Не удалось открыть приложение SMS. Звонок не отклонён.",Toast.LENGTH_LONG).show();
            return;
        }
        try{if(call.ringing()&&Objects.equals(number,call.recipient()))call.reject(null);}
        catch(RuntimeException failure){Toast.makeText(activity,"Черновик открыт. Не удалось отклонить звонок.",Toast.LENGTH_LONG).show();}
    }
}
