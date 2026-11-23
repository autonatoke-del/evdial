package ru.autonatoke.evdial;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.UUID;

final class CallNotice {
    static final String RING_CHANNEL = "ring.v2";
    static final String SIM_CHANNEL = "sim.incoming.silent.v3";
    static final String ANSWER = "ru.autonatoke.evdial.ANSWER";
    private static final String DECLINE = "ru.autonatoke.evdial.DECLINE";
    static final int SIP = 8, SIM = 18;
    private final Context app;
    private final Class<?> screen;
    private final int line;
    private Object owner;
    private String token, number, title;
    private boolean showing;

    CallNotice(Context context, Class<?> screen, int line) {
        this.app = context;
        this.screen = screen;
        this.line = line;
    }

    static PendingIntent open(Context context, Class<?> screen, int id) {
        Intent view = new Intent(context, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, id, view, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static NotificationChannel incomingChannel(Context c) {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        NotificationChannel existing = manager.getNotificationChannel(RING_CHANNEL);
        if (existing != null) return existing;
        NotificationChannel channel = new NotificationChannel(RING_CHANNEL, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        manager.createNotificationChannel(channel);
        return manager.getNotificationChannel(RING_CHANNEL);
    }

    static NotificationChannel silentSimChannel(Context c) {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        NotificationChannel existing = manager.getNotificationChannel(SIM_CHANNEL);
        if (existing != null) return existing;
        NotificationChannel channel = new NotificationChannel(SIM_CHANNEL, "Входящие SIM", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
        return manager.getNotificationChannel(SIM_CHANNEL);
    }

    // Post immediately. A stalled Contacts provider must never hold up the incoming screen.
    void show(Object call, String address, String platformName) {
        if (owner != call) {
            clear();
            owner = call;
            token = UUID.randomUUID().toString();
        }
        boolean named = platformName != null && !platformName.trim().isEmpty();
        if (showing && address.equals(number) && (!named || platformName.equals(title))) return;
        number = address;
        title = named ? platformName : address;
        showing = true;
        post();
        if (named || address.isEmpty() || "Номер скрыт".equals(address)
                || app.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
        String originalToken = token;
        new Thread(() -> {
            String name = Directory.name(app, address);
            if (name == null || name.isEmpty() || name.equals(address)) return;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!showing || !originalToken.equals(token) || !address.equals(number) || !address.equals(title)) return;
                title = name;
                post();
            });
        }, "IncomingCallerName").start();
    }

    private Uri identity() { return Uri.fromParts("evdial-call", token, null); }

    boolean matches(Object call, Intent intent) {
        return call != null && owner == call && token != null && intent != null && identity().equals(intent.getData());
    }

    private void post() {
        if (line == SIM) silentSimChannel(app); else incomingChannel(app);
        String label = line == SIM ? "Входящий звонок · SIM" : "Входящий звонок · Городская линия";
        Intent view = new Intent(app, screen).setData(identity())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(app, line, view, flags);
        PendingIntent answer = PendingIntent.getActivity(app, line, new Intent(view).setAction(ANSWER), flags);
        Intent reject = new Intent(app, Decline.class).setAction(DECLINE).setData(identity()).putExtra("line", line);
        PendingIntent decline = PendingIntent.getBroadcast(app, line, reject, flags);

        Notification.Builder incoming = base(label, title, open);
        Notification.Builder redacted = base(label, "Входящий звонок", open);
        addActions(incoming, title, decline, answer);
        addActions(redacted, "Входящий звонок", decline, answer);
        incoming.setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(
                redacted.setVisibility(Notification.VISIBILITY_PUBLIC).build());
        app.getSystemService(NotificationManager.class).notify(token, line, incoming.build());
    }

    private Notification.Builder base(String lineLabel, String caller, PendingIntent open) {
        return new Notification.Builder(app, line == SIM ? SIM_CHANNEL : RING_CHANNEL)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(caller).setContentText(lineLabel)
                .setCategory(Notification.CATEGORY_CALL).setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(open).setFullScreenIntent(open, true)
                .setOnlyAlertOnce(true).setOngoing(true);
    }

    private void addActions(Notification.Builder notice, String caller, PendingIntent decline, PendingIntent answer) {
        if (Build.VERSION.SDK_INT >= 31) {
            Person person = new Person.Builder().setName(caller.isEmpty() ? "Неизвестный номер" : caller).setImportant(true).build();
            notice.setStyle(Notification.CallStyle.forIncomingCall(person, decline, answer));
        } else {
            notice.addAction(R.drawable.sim_end, "Отклонить", decline)
                    .addAction(R.drawable.dir_callback, "Ответить", answer);
        }
    }

    // Stop ringing, but keep the original identity while the private SIP call screen is open.
    void dismiss() {
        if (showing) app.getSystemService(NotificationManager.class).cancel(token, line);
        showing = false;
    }

    void clear() {
        dismiss();
        owner = null;
        token = null;
    }

    public static final class Decline extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (!DECLINE.equals(intent.getAction())) return;
            if (intent.getIntExtra("line", 0) == SIM) {
                MobileCalls service = MobileCalls.live;
                if (service == null) return;
                android.telecom.Call call = service.current();
                if (service.ringNotice.matches(call, intent) && call.getState() == android.telecom.Call.STATE_RINGING)
                    call.reject(false, null);
            } else if (intent.getIntExtra("line", 0) == SIP) {
                Phone phone = (Phone) context.getApplicationContext();
                if (phone.ringing() && phone.ringNotice.matches(phone.active, intent))
                    phone.active.decline(org.linphone.core.Reason.Declined);
            }
        }
    }
}
