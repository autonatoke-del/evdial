package ru.autonatoke.evdial;

import android.Manifest;
import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.CallLog;
import android.telecom.TelecomManager;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.linphone.core.Core;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowSmsManager;
import org.robolectric.shadows.ShadowSubscriptionManager;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Synthetic providers and SmsManager shadows only: no network, real messages, or calls. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = RestrictedPhoneAccessTest.NoCallsPhone.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class RestrictedPhoneAccessTest {
    private static final int SUB = 1;
    private static final String SIM_NUMBER = "+15550001111", SIP_NUMBER = "+15550002222";
    private static final String RECIPIENT = "+15550003333", DRAFT = "Only a synthetic test draft";
    private NoCallsPhone app;
    private CallsProvider provider;

    public static class NoCallsPhone extends Phone {
        private final Core historyOnly = (Core) Proxy.newProxyInstance(Core.class.getClassLoader(),
                new Class<?>[]{Core.class}, (proxy, method, args) -> {
                    // ContactsScreen merges saved SIP history with the current core's logs.
                    // This is a Java-only reader; any native/call operation still fails the test.
                    if (method.getName().equals("getCallLogs")) return new org.linphone.core.CallLog[0];
                    throw new AssertionError("Unexpected SIP operation in role test: " + method.getName());
                });
        @Override synchronized Core sipCore() { return historyOnly; }
        @Override void dial(String number) { throw new AssertionError("Role tests must not place a call"); }
        @Override void updateService() { throw new AssertionError("Role tests must not start call services"); }
    }

    @Before public void prepareSyntheticData() {
        app = (NoCallsPhone) RuntimeEnvironment.getApplication();
        role(false);
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALL_LOG, Manifest.permission.SEND_SMS);
        ShadowSubscriptionManager.setDefaultSmsSubscriptionId(SUB);
        app.getSharedPreferences("sip_history", 0).edit().putString("calls",
                "[{\"number\":\"" + SIP_NUMBER + "\",\"time\":1000,\"type\":2}]").commit();
        provider = new CallsProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = CallLog.AUTHORITY;
        provider.attachInfo(app, info);
        ShadowContentResolver.registerProviderInternal(CallLog.AUTHORITY, provider);
    }

    @Test public void grantedCallLogPermissionIsInsufficientWithoutCurrentPhoneRole() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
        assertFalse(PhoneRole.held(app.getApplicationContext()));
        assertFalse(PhoneRole.canReadCallLog(app));
        List<SmartDialSearch.Entry> index = SmartDialIndex.load(app);
        assertEquals(0, provider.queries);
        assertFalse(hasNumber(index, SIM_NUMBER));
        assertTrue("App-owned SIP history remains usable", hasNumber(index, SIP_NUMBER));

        role(true);
        assertTrue(PhoneRole.held(app.getApplicationContext()));
        assertTrue(hasNumber(SmartDialIndex.load(app), SIM_NUMBER));
        assertEquals(1, provider.queries);

        role(false);
        assertFalse(hasNumber(SmartDialIndex.load(app), SIM_NUMBER));
        assertEquals("No query after role loss even though permission remains granted", 1, provider.queries);
    }

    @Test public void losingRoleDuringProviderQueryDiscardsSimRows() {
        role(true);
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
        provider.beforeResult = () -> role(false);
        List<SmartDialSearch.Entry> index = SmartDialIndex.load(app);
        assertEquals(1, provider.queries);
        assertFalse(hasNumber(index, SIM_NUMBER));
        assertTrue(hasNumber(index, SIP_NUMBER));
    }

    @Test public void smsOutsidePhoneRoleOpensComposerWithoutRequestingPermission() {
        ActivityController<Activity> host = host();
        try {
            QuickSms editor = editor(host.get());
            submit(editor);
            assertNull(shadowOf(host.get()).getLastRequestedPermission());
            assertComposer(host.get());
            assertNoSms();
        } finally { host.pause().stop().destroy(); }
    }

    @Test public void alreadyGrantedSmsPermissionDoesNotBypassRoleCheck() {
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS);
        ActivityController<Activity> host = host();
        try {
            QuickSms editor = editor(host.get());
            submit(editor);
            assertComposer(host.get());
            assertNoSms();
        } finally { host.pause().stop().destroy(); }
    }

    @Test public void roleLostWhilePermissionDialogIsOpenPreservesDraftInComposer() {
        role(true);
        ActivityController<Activity> host = host();
        try {
            QuickSms editor = editor(host.get());
            assertTrue(PhoneRole.held(host.get()));
            assertEquals(PackageManager.PERMISSION_DENIED,
                    host.get().checkSelfPermission(Manifest.permission.SEND_SMS));
            submit(editor);
            // Platform Fragment requests bypass Activity.requestPermissions(), so
            // ShadowActivity.getLastRequestedPermission() does not record this path.
            ShadowActivity.IntentForResult request = shadowOf(host.get()).getNextStartedActivityForResult();
            assertNotNull("The fragment opens Android's permission request", request);
            assertEquals(73, request.requestCode);
            assertEquals("android.content.pm.action.REQUEST_PERMISSIONS", request.intent.getAction());
            assertArrayEquals(new String[]{Manifest.permission.SEND_SMS},
                    request.intent.getStringArrayExtra("android.content.pm.extra.REQUEST_PERMISSIONS_NAMES"));
            Intent started = shadowOf(host.get()).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(request.intent.getAction(), started.getAction());
            assertNull(shadowOf(host.get()).getNextStartedActivity());
            assertNoSms();
            role(false);
            shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS);
            editor.onRequestPermissionsResult(73, new String[]{Manifest.permission.SEND_SMS},
                    new int[]{PackageManager.PERMISSION_GRANTED});
            assertComposer(host.get());
            assertNoSms();
        } finally { host.pause().stop().destroy(); }
    }

    @Test public void currentDefaultPhoneCanStillSubmitThroughSyntheticSmsManager() {
        role(true);
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS);
        ActivityController<Activity> host = host();
        try {
            submit(editor(host.get()));
            ShadowSmsManager.TextSmsParams sent = sms().getLastSentTextMessageParams();
            assertNotNull("Allowed direct SMS remains supported", sent);
            assertEquals(RECIPIENT, sent.getDestinationAddress());
            assertEquals(DRAFT, sent.getText());
            assertNull(shadowOf(host.get()).getNextStartedActivity());
        } finally { host.pause().stop().destroy(); }
    }

    @Test public void dialerDropsVisibleAndCachedSimSuggestionsOnRoleChange() {
        role(true);
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
        ActivityController<Dialer> controller = Robolectric.buildActivity(Dialer.class, new Intent(Intent.ACTION_DIAL));
        Dialer dialer = controller.get();
        QueuedExecutor worker = replaceWorker(dialer, "smartWorker");
        try {
            controller.create().start().resume().visible();
            finishSearch(worker);
            assertTrue(dialer.smartNumber.getText().toString().contains(SIM_NUMBER));
            int before = provider.queries;
            role(false);
            app.sendBroadcast(new Intent(TelecomManager.ACTION_DEFAULT_DIALER_CHANGED));
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(((List<?>) ReflectionHelpers.getField(dialer, "smartEntries")).isEmpty());
            assertFalse((Boolean) ReflectionHelpers.getField(dialer, "smartLoaded"));
            assertNotEquals(View.VISIBLE, dialer.smartPanel.getVisibility());
            assertFalse("The old call action is disconnected", dialer.smartPrimary.performClick());
            finishSearch(worker);
            assertEquals(before, provider.queries);
            assertTrue(dialer.smartNumber.getText().toString().contains(SIP_NUMBER));
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void staleSuggestionClickRechecksRoleBeforeUsingItsNumber() {
        role(true);
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
        ActivityController<Dialer> controller = Robolectric.buildActivity(Dialer.class, new Intent(Intent.ACTION_DIAL));
        Dialer dialer = controller.get();
        QueuedExecutor worker = replaceWorker(dialer, "smartWorker");
        try {
            controller.create().start().resume().visible();
            finishSearch(worker);
            role(false); // Deliberately do not deliver the broadcast before this stale tap.
            dialer.smartPrimary.performClick();
            assertEquals("", dialer.number.getText().toString());
            assertTrue(((List<?>) ReflectionHelpers.getField(dialer, "smartEntries")).isEmpty());
            assertNull(shadowOf(dialer).getNextStartedActivity());
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void recentCallsDropSimCacheAndObserverWhenRoleIsLost() {
        role(true);
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
        ActivityController<ContactsScreen> controller = Robolectric.buildActivity(ContactsScreen.class,
                new Intent().putExtra("recent", true));
        ContactsScreen contacts = controller.get();
        QueuedExecutor reader = replaceWorker(contacts, "reader");
        try {
            controller.create().start().resume().visible();
            reader.runAll();
            assertTrue(containsSim(ReflectionHelpers.getField(contacts, "visible")));
            int before = provider.queries;
            role(false);
            app.sendBroadcast(new Intent(TelecomManager.ACTION_DEFAULT_DIALER_CHANGED));
            shadowOf(Looper.getMainLooper()).idle();
            assertFalse(containsSim(ReflectionHelpers.getField(contacts, "visible")));
            assertFalse(containsSim(ReflectionHelpers.getField(contacts, "history")));
            assertFalse((Boolean) ReflectionHelpers.getField(contacts, "observingCalls"));
            reader.runAll();
            assertEquals(before, provider.queries);
            assertEquals("SIP history remains visible", 1,
                    ((List<?>) ReflectionHelpers.getField(contacts, "visible")).size());
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void staleHistoryGrantButtonCannotPromptAfterRoleLoss() {
        role(true);
        ActivityController<ContactsScreen> controller = Robolectric.buildActivity(ContactsScreen.class,
                new Intent().putExtra("recent", true));
        ContactsScreen contacts = controller.get();
        replaceWorker(contacts, "reader");
        try {
            controller.create().start().resume().visible();
            TextView grant = ReflectionHelpers.getField(contacts, "grant");
            assertEquals(View.VISIBLE, grant.getVisibility());
            role(false);
            grant.performClick();
            assertNull(shadowOf(contacts).getLastRequestedPermission());
            assertEquals(0, provider.queries);
        } finally { controller.pause().stop().destroy(); }
    }

    private void role(boolean held) {
        shadowOf(app.getSystemService(TelecomManager.class))
                .setDefaultDialerPackage(held ? app.getPackageName() : "test.other.phone");
    }

    private ActivityController<Activity> host() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
        controller.get().setTheme(R.style.StationTheme);
        return controller.create().start().resume().visible();
    }

    private QuickSms editor(Activity host) {
        QuickSms.open(host, RECIPIENT, "Synthetic recipient");
        host.getFragmentManager().executePendingTransactions();
        QuickSms editor = (QuickSms) host.getFragmentManager().findFragmentByTag("quick-sms");
        assertNotNull(editor);
        ((EditText) ReflectionHelpers.getField(editor, "draft")).setText(DRAFT);
        return editor;
    }

    private void submit(QuickSms editor) {
        Button submit = ReflectionHelpers.getField(editor, "submit");
        assertEquals(View.VISIBLE, submit.getVisibility());
        assertTrue(submit.isEnabled());
        submit.performClick();
    }

    private void assertComposer(Activity host) {
        Intent intent = shadowOf(host).getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(Intent.ACTION_SENDTO, intent.getAction());
        assertEquals("smsto", intent.getData().getScheme());
        assertEquals(RECIPIENT, intent.getData().getSchemeSpecificPart());
        assertEquals(DRAFT, intent.getStringExtra("sms_body"));
    }

    private ShadowSmsManager sms() { return shadowOf(SmsManager.getSmsManagerForSubscriptionId(SUB)); }
    private void assertNoSms() {
        assertNull(sms().getLastSentTextMessageParams());
        assertNull(sms().getLastSentMultipartTextMessageParams());
    }

    private static boolean hasNumber(List<SmartDialSearch.Entry> entries, String number) {
        for (SmartDialSearch.Entry entry : entries) if (number.equals(entry.number)) return true;
        return false;
    }

    private static boolean containsSim(List<?> entries) {
        for (Object entry : entries) if ("SIM".equals(ReflectionHelpers.getField(entry, "route"))) return true;
        return false;
    }

    private static QueuedExecutor replaceWorker(Object owner, String field) {
        ((ExecutorService) ReflectionHelpers.getField(owner, field)).shutdownNow();
        QueuedExecutor replacement = new QueuedExecutor();
        ReflectionHelpers.setField(owner, field, replacement);
        return replacement;
    }

    private static void finishSearch(QueuedExecutor worker) {
        worker.runAll();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        worker.runAll();
    }

    private static final class QueuedExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private boolean stopped;
        @Override public void execute(Runnable command) { tasks.add(command); }
        void runAll() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
        @Override public void shutdown() { stopped = true; }
        @Override public List<Runnable> shutdownNow() { stopped = true; tasks.clear(); return Collections.emptyList(); }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped && tasks.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }

    private static final class CallsProvider extends ContentProvider {
        int queries;
        Runnable beforeResult;
        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] columns, String selection, String[] args, String sort) {
            queries++;
            if (beforeResult != null) beforeResult.run();
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                switch (columns[i]) {
                    case CallLog.Calls.NUMBER: values[i] = SIM_NUMBER; break;
                    case CallLog.Calls.DATE: values[i] = 2000L; break;
                    case CallLog.Calls.TYPE: values[i] = CallLog.Calls.OUTGOING_TYPE; break;
                    case CallLog.Calls.NUMBER_PRESENTATION: values[i] = CallLog.Calls.PRESENTATION_ALLOWED; break;
                    case CallLog.Calls.DURATION: values[i] = 13L; break;
                    case CallLog.Calls.CACHED_NAME: values[i] = "Synthetic private SIM call"; break;
                    default: throw new AssertionError("Unexpected call-log column " + columns[i]);
                }
            }
            cursor.addRow(values);
            return cursor;
        }
        @Override public String getType(Uri uri) { return CallLog.Calls.CONTENT_TYPE; }
        @Override public Uri insert(Uri uri, ContentValues values) { throw new AssertionError("No writes expected"); }
        @Override public int delete(Uri uri, String selection, String[] args) { throw new AssertionError("No writes expected"); }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new AssertionError("No writes expected"); }
    }
}
