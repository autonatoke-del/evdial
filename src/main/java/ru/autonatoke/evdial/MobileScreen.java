package ru.autonatoke.evdial;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Chronometer;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

public class MobileScreen extends Activity {
    private static final int GRAPHITE = 0xff101419;
    private static final int ICE = 0xffe3efed;
    private static final int MINT = 0xff94f0b8;
    private static final int STEEL = 0xff93aaa9;
    private LinearLayout body;
    private MobileCalls service;
    private StationSheet digitsSheet;
    private IncomingReply reply;
    private Call keypadCall, displayed;
    private int displayedState = -1;
    private TextView caller, address, message, micLabel, speakerLabel;
    private Chronometer elapsed;
    private LinearLayout audioRow, accounts;
    private long connectedAt;
    private boolean narrowControls;
    private final Runnable refresh = this::show;
    private final android.os.Handler tones=new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable stopTone;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        reply=new IncomingReply(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().setStatusBarColor(UiProfiles.active(this).base);
        getWindow().setNavigationBarColor(UiProfiles.active(this).base);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(dp(18), dp(18), dp(18), dp(18));
        narrowControls = (getResources().getConfiguration().screenWidthDp - 55) / 3f
                < 65 * getResources().getConfiguration().fontScale;
        body.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff162b2e, GRAPHITE, 0xff162126}));
        DriveTools.background(this,body);
        scroll.addView(body);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        Appearance.applyBackground(this,body);
        service = MobileCalls.live;
        if (service != null) service.changed = refresh;
        Intent request=getIntent();
        if(CallNotice.ANSWER.equals(request.getAction())){
            setIntent(new Intent(request).setAction(null));
            Call target=service==null?null:service.current();
            if(service!=null && service.ringNotice.matches(target,request) && target.getState()==Call.STATE_RINGING)
                target.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY);
        }
        show();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override protected void onPause() {
        if (service != null && service.changed == refresh) service.changed = null;
        super.onPause();
    }

    private void show() {
        reply.reconcile();
        Call call = service == null ? null : service.current();
        if (keypadCall != null && (keypadCall != call || call.getState() != Call.STATE_ACTIVE)) closeDigits();
        if (call == null) { finish(); return; }
        int state = call.getState();
        if (state != Call.STATE_RINGING) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        if (displayed != call || displayedState != state) {
            displayed = call;
            displayedState = state;
            renderControls(call, state);
        }

        String number = MobileCalls.number(call);
        String name = Directory.name(this, number);
        caller.setText(name);
        address.setText(number);
        address.setVisibility(name.equals(number) ? View.GONE : View.VISIBLE);
        if (message != null) message.setVisibility(IncomingReply.mobileRecipient(call) == null ? View.GONE : View.VISIBLE);
        if (accounts != null) updateAccounts(call);
        if (state == Call.STATE_ACTIVE) updateElapsed(call);
        if (MobileCalls.audioControlsAllowed(state)) updateAudio();
    }

    private void renderControls(Call call, int state) {
        if (elapsed != null) elapsed.stop();
        connectedAt = 0;
        elapsed = null;
        message = null;
        accounts = null;
        micLabel = null;
        speakerLabel = null;
        body.removeAllViews();

        TextView brand = label(UiProfiles.active(this).label+"  /  SIM", 12, UiProfiles.accent(this));
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brand.setLetterSpacing(.12f);
        label(stateText(state), 15, STEEL);
        caller = label("", 31, UiProfiles.text(this));
        caller.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        address = label("", 17, UiProfiles.muted(this));

        if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) return;
        if (state == Call.STATE_SELECT_PHONE_ACCOUNT) {
            accounts = new LinearLayout(this);
            accounts.setOrientation(LinearLayout.VERTICAL);
            body.addView(accounts, -1, -2);
        }
        if (state == Call.STATE_ACTIVE) {
            elapsed = new Chronometer(this);
            elapsed.setGravity(Gravity.CENTER);
            elapsed.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            elapsed.setTextSize(16);
            elapsed.setTextColor(UiProfiles.accent(this));
            body.addView(elapsed, -1, -2);
        }
        if (MobileCalls.audioControlsAllowed(state)) addAudioControls(call);
        if (state == Call.STATE_HOLDING) {
            action(body, "Продолжить разговор", android.R.drawable.sym_action_call, () -> {
                if (isCurrent(call, Call.STATE_HOLDING)) call.unhold();
            });
        }
        boolean ringing = state == Call.STATE_RINGING;
        BatteryCallControl battery = new BatteryCallControl(this, ringing,
                () -> { if (isCurrent(call, Call.STATE_RINGING)) call.answer(0); },
                () -> {
                    if (!isCurrent(call, state)) return;
                    if (ringing) call.reject(false, null);
                    else call.disconnect();
                });
        LinearLayout.LayoutParams batterySlot = new LinearLayout.LayoutParams(-1, -2);
        batterySlot.topMargin = dp(state == Call.STATE_ACTIVE ? 17 : 28);
        body.addView(battery, batterySlot);
        if (ringing) message = action(body, "Ответить SMS", R.drawable.dir_sms,
                () -> { if (isCurrent(call, Call.STATE_RINGING)) reply.openMobile(service, call); });
    }

    private String stateText(int state) {
        switch (state) {
            case Call.STATE_RINGING: return "Входящий звонок";
            case Call.STATE_ACTIVE: return "Идёт разговор";
            case Call.STATE_HOLDING: return "На удержании";
            case Call.STATE_DISCONNECTED: return "Звонок завершён";
            case Call.STATE_DISCONNECTING: return "Завершение звонка";
            case Call.STATE_SELECT_PHONE_ACCOUNT: return "Выберите SIM";
            default: return "Исходящий звонок";
        }
    }

    private boolean isCurrent(Call call, int state) {
        return MobileCalls.live == service && service != null && service.current() == call
                && call.getState() == state;
    }

    private void updateElapsed(Call call) {
        long connected = call.getDetails() == null ? 0 : call.getDetails().getConnectTimeMillis();
        if (connected <= 0) { elapsed.setVisibility(View.GONE); return; }
        if (connected != connectedAt) {
            connectedAt = connected;
            elapsed.setBase(SystemClock.elapsedRealtime() - Math.max(0L, System.currentTimeMillis() - connected));
            elapsed.start();
        }
        elapsed.setVisibility(View.VISIBLE);
    }

    private void updateAccounts(Call call) {
        accounts.removeAllViews();
        Bundle extras = call.getDetails().getIntentExtras();
        List<PhoneAccountHandle> choices = extras == null ? null :
                extras.getParcelableArrayList(Call.AVAILABLE_PHONE_ACCOUNTS);
        if (choices == null) return;
        for (PhoneAccountHandle account : choices) {
            PhoneAccount sim = getSystemService(TelecomManager.class).getPhoneAccount(account);
            action(accounts, sim == null ? "SIM" : sim.getLabel().toString(), 0, () -> {
                if (isCurrent(call, Call.STATE_SELECT_PHONE_ACCOUNT)) call.phoneAccountSelected(account, false);
            });
        }
    }

    private void addAudioControls(Call call) {
        audioRow = new LinearLayout(this);
        audioRow.setOrientation(narrowControls ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        audioRow.setGravity(Gravity.CENTER);
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(UiProfiles.panel(this));
        glass.setCornerRadius(dp(20));
        glass.setStroke(dp(1), UiProfiles.secondary(this));
        audioRow.setBackground(glass);
        audioRow.setPadding(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, -2);
        slot.topMargin = dp(15);
        body.addView(audioRow, slot);

        micLabel = audioKey("Микрофон", R.drawable.sim_mic, () -> {
            if (isCurrent(call, Call.STATE_ACTIVE) && service.muteKnown()) service.setMuted(!service.audioMuted());
        });
        speakerLabel = audioKey("Динамик", R.drawable.sim_speaker, () -> {
            if (MobileCalls.live == service && service != null && service.canControlAudio(call)) {
                service.toggleSpeaker(call);
                updateAudio();
            }
        });
        TextView keys = audioKey("Клавиши", R.drawable.ic_keypad, () -> { if (isCurrent(call, Call.STATE_ACTIVE)) openDigits(call); });
        keys.setEnabled(call.getState() == Call.STATE_ACTIVE);
        keys.setAlpha(keys.isEnabled() ? 1f : .45f);
    }

    private TextView audioKey(String title, int icon, Runnable action) {
        TextView key = new TextView(this);
        key.setText(title);
        key.setTextColor(UiProfiles.text(this));
        key.setTextSize(UiProfiles.type(this,14));
        key.setGravity(Gravity.CENTER);
        key.setMinHeight(dp(narrowControls ? 48 : 66));
        key.setPadding(dp(6), dp(6), dp(6), dp(6));
        key.setCompoundDrawablePadding(dp(3));
        audioSymbol(key, icon, ICE);
        key.setOnClickListener(v -> action.run());
        key.setFocusable(true);
        key.setBackground(pill(0x0015221b));
        audioRow.addView(key, narrowControls ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1));
        return key;
    }

    private void audioSymbol(TextView key, int resource, int color) {
        android.graphics.drawable.Drawable icon = getDrawable(resource).mutate();
        icon.setTint(color);
        icon.setBounds(0, 0, dp(22), dp(22));
        if (narrowControls) key.setCompoundDrawablesRelative(icon, null, null, null);
        else key.setCompoundDrawables(null, icon, null, null);
    }

    private void updateAudio() {
        if (service == null || micLabel == null || speakerLabel == null || displayed == null) return;
        boolean micReady = displayed.getState() == Call.STATE_ACTIVE && service.muteKnown();
        micLabel.setEnabled(micReady);
        micLabel.setAlpha(micReady ? 1f : .45f);
        speakerLabel.setEnabled(service.canControlAudio(displayed));
        boolean mic = !service.audioMuted(), loud = service.speakerSelected(displayed);
        setAudioKey(micLabel, mic, mic ? R.drawable.sim_mic : R.drawable.sim_muted);
        boolean pending = service.speakerPending(displayed);
        speakerLabel.setText(pending ? "Динамик…" : "Динамик");
        setAudioKey(speakerLabel, loud, R.drawable.sim_speaker);
        if (pending) speakerLabel.setContentDescription(loud ? "Громкая связь выбрана, ожидаем аудиоканал" : "Переключаем звук с динамика");
    }

    private void setAudioKey(TextView key, boolean on, int symbol) {
        key.setSelected(on);
        key.setContentDescription(key.getText() + (on ? ", включён" : ", выключен"));
        key.setTextColor(on ? UiProfiles.accent(this) : UiProfiles.text(this));
        audioSymbol(key, symbol, on ? UiProfiles.accent(this) : UiProfiles.text(this));
        key.setBackground(pill(on ? UiProfiles.button(this) : 0x0015221b));
    }

    private RippleDrawable pill(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(16));
        shape.setColor(color);
        return new RippleDrawable(ColorStateList.valueOf(0x448ae8a6), shape, null);
    }

    private void openDigits(Call call){
        if(digitsSheet!=null||call.getState()!=Call.STATE_ACTIVE)return;
        keypadCall=call;
        LinearLayout pad=new LinearLayout(this);pad.setOrientation(LinearLayout.VERTICAL);
        TextView entered=new TextView(this);entered.setGravity(Gravity.CENTER);entered.setTextColor(ICE);
        entered.setTextSize(22);entered.setSingleLine();entered.setEllipsize(android.text.TextUtils.TruncateAt.START);
        entered.setMinHeight(dp(38));pad.addView(entered,-1,-2);
        String keys="123456789*0#";
        for(int i=0;i<4;i++){
            LinearLayout row=new LinearLayout(this);pad.addView(row,-1,-2);
            for(int j=0;j<3;j++){
                char digit=keys.charAt(i*3+j);TextView k=new TextView(this);
                k.setText(String.valueOf(digit));k.setTextColor(ICE);k.setTextSize(23);k.setGravity(Gravity.CENTER);k.setMinimumHeight(dp(48));
                GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(15));bg.setColor(0xff25322b);
                k.setBackground(new RippleDrawable(ColorStateList.valueOf(0x448ae8a6),bg,null));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(k,lp);
                k.setOnClickListener(v->{if(call.getState()!=Call.STATE_ACTIVE)return;endTone();call.playDtmfTone(digit);
                    entered.append(String.valueOf(digit));stopTone=()->{call.stopDtmfTone();stopTone=null;};tones.postDelayed(stopTone,150);});
            }
        }
        digitsSheet=new StationSheet.Builder(this).setTitle("Добавочный номер").setView(pad).setNegativeButton("Готово",null).create();
        digitsSheet.setOnDismissListener(d->{endTone();digitsSheet=null;keypadCall=null;});digitsSheet.show();
    }
    private void endTone(){if(stopTone!=null){tones.removeCallbacks(stopTone);stopTone.run();stopTone=null;}}
    private void closeDigits(){if(digitsSheet!=null)digitsSheet.dismiss();}
    @Override protected void onStop(){reply.close();super.onStop();}
    @Override protected void onDestroy(){reply.close();closeDigits();endTone();super.onDestroy();}

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(UiProfiles.type(this,size));
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(4), 0, dp(4));
        body.addView(view, -1, -2);
        return view;
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private TextView action(LinearLayout parent, String text, int symbol, Runnable click) {
        TextView key = new TextView(this);
        key.setText(text);
        key.setMinHeight(dp(48));
        key.setPadding(dp(14), dp(6), dp(14), dp(6));
        key.setTextSize(15);
        key.setTextColor(ICE);
        key.setGravity(Gravity.CENTER);
        if (symbol != 0) {
            key.setCompoundDrawablesRelativeWithIntrinsicBounds(symbol, 0, 0, 0);
            key.setCompoundDrawablePadding(dp(8));
        }
        key.setBackground(pill(UiProfiles.button(this)));
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-2, -2);
        slot.gravity = Gravity.CENTER_HORIZONTAL;
        slot.topMargin = dp(8);
        parent.addView(key, slot);
        key.setOnClickListener(v -> click.run());
        key.setFocusable(true);
        return key;
    }
}
