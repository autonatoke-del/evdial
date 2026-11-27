package ru.autonatoke.evdial;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.linphone.core.Core;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Run for website, -Pfdroid=true and -PgooglePlay=true distributions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "en-rUS", application = AppUpdatesDistributionTest.NoCallsPhone.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class AppUpdatesDistributionTest {
    public static class NoCallsPhone extends Phone {
        @Override synchronized Core sipCore() {
            throw new AssertionError("Update navigation must not initialize SIP");
        }
        @Override void dial(String number) {
            throw new AssertionError("Update navigation must not place a call");
        }
        @Override void updateService() {
            throw new AssertionError("Update navigation must not activate a call service");
        }
    }

    @Test
    public void directLaunchShowsDistributionControlsWithoutStartingNetworkWork() {
        ActivityController<AppUpdates> controller = Robolectric.buildActivity(AppUpdates.class);
        AppUpdates activity = controller.get();
        ExecutorService original = ReflectionHelpers.getField(activity, "worker");
        original.shutdownNow();
        // Any accidental automatic check/download fails before it can reach the network.
        ReflectionHelpers.setField(activity, "worker", new NoNetworkExecutor());
        try {
            controller.create().start().resume().visible();
            shadowOf(Looper.getMainLooper()).idle();
            View root = activity.getWindow().getDecorView();
            if (BuildConfig.FDROID_BUILD || BuildConfig.GOOGLE_PLAY_BUILD) {
                String store = BuildConfig.GOOGLE_PLAY_BUILD ? "Google Play" : "F-Droid";
                assertTrue("Store update action is visible", text(root, "Open " + store).isShown());
                List<String> labels = labels(root);
                String guidance = BuildConfig.GOOGLE_PLAY_BUILD ? "updates through Google Play" : "repository you installed it from";
                assertTrue("Explains the distribution's update route",
                        labels.stream().anyMatch(value -> value.contains(guidance)));
                assertTrue("Website download and install actions are absent", Collections.disjoint(labels,
                        Arrays.asList("Check for updates", "Download update", "Install update", "EVDial page ↗")));
            } else {
                assertTrue("Website checking remains available", text(root, "Check for updates").isShown());
                assertEquals("Download and install wait for a checked offer", Arrays.asList(View.GONE, View.GONE),
                        Arrays.asList(text(root, "Download update").getVisibility(), text(root, "Install update").getVisibility()));
                assertNull("Website build does not redirect updates to F-Droid", text(root, "Open F-Droid"));
                assertNull("Website build does not redirect updates to Google Play", text(root, "Open Google Play"));
            }
            assertNull("Opening updates makes no connection", ReflectionHelpers.getField(activity, "connection"));
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void openUsesDistributionEntryPointAndRepositoryFallbackRequiresATap() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
        Activity host = controller.get();
        host.setTheme(R.style.StationTheme);
        Dialog dialog = null;
        try {
            controller.create().start().resume().visible();
            AppUpdates.open(host);
            if (BuildConfig.FDROID_BUILD || BuildConfig.GOOGLE_PLAY_BUILD) {
                dialog = ShadowDialog.getLatestDialog();
                assertTrue("Repository guidance is shown", dialog instanceof StationSheet && dialog.isShowing());
                assertNull("Opening guidance does not launch another app", shadowOf(host).getNextStartedActivity());
                TextView open = ((StationSheet) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
                assertEquals(BuildConfig.GOOGLE_PLAY_BUILD ? "Open Google Play" : "Open F-Droid", open.getText().toString());
                open.performClick();
                // No F-Droid client is installed in this sandbox. Robolectric only records this
                // browser intent; it neither opens an external app nor performs a request.
                Intent fallback = shadowOf(host).getNextStartedActivity();
                String url = BuildConfig.GOOGLE_PLAY_BUILD
                        ? "https://play.google.com/store/apps/details?id=" + host.getPackageName()
                        : "https://f-droid.org/";
                assertEquals(Arrays.asList(Intent.ACTION_VIEW, url),
                        Arrays.asList(fallback.getAction(), fallback.getDataString()));
            } else {
                Intent target = shadowOf(host).getNextStartedActivity();
                assertEquals("Website entry point opens the updater", AppUpdates.class.getName(),
                        target.getComponent().getClassName());
                assertNull("Website entry point does not show repository guidance", ShadowDialog.getLatestDialog());
            }
        } finally {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
            controller.pause().stop().destroy();
        }
    }

    private static TextView text(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = text(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static List<String> labels(View view) {
        List<String> result = new ArrayList<>();
        if (view instanceof TextView) result.add(((TextView) view).getText().toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) result.addAll(labels(group.getChildAt(i)));
        }
        return result;
    }

    private static final class NoNetworkExecutor extends AbstractExecutorService {
        private boolean stopped;
        @Override public void execute(Runnable command) {
            throw new AssertionError("Opening update UI must not enqueue network work");
        }
        @Override public void shutdown() { stopped = true; }
        @Override public List<Runnable> shutdownNow() { stopped = true; return Collections.emptyList(); }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
    }
}
