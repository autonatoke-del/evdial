package ru.autonatoke.evdial;

import android.app.Activity;
import android.app.Application;
import android.graphics.Insets;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Exercises actual inset dispatch and child measurement without starting SIP. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = PlayInsetsTest.InsetsApplication.class)
public final class PlayInsetsTest {
    public static class InsetsApplication extends Application {
        @Override public void onCreate() {
            super.onCreate();
            ScreenOrientation.register(this);
        }
    }

    public static class PageActivity extends Activity {
        @Override public void onCreate(Bundle state) {
            setTheme(R.style.StationTheme);
            super.onCreate(state);
            replacePage();
        }

        void replacePage() {
            DialerViewport page = new DialerViewport(this);
            page.setPadding(9, 10, 11, 12);
            setContentView(page);
        }
    }

    @Test public void systemBarsAndLandscapeCutoutProtectChildBoundsAndOriginalPadding() {
        FrameLayout container = container();
        View page = new View(container.getContext());
        page.setPadding(17, 19, 23, 29);
        container.addView(page, new FrameLayout.LayoutParams(-1, -1));
        PlayInsets.install(container);
        WindowInsets returned = container.dispatchApplyWindowInsets(insets(
                Insets.of(0, 24, 0, 36), Insets.of(32, 26, 8, 0), Insets.NONE));
        measure(container);

        assertPadding(container, 37, 33, 19, 49);
        assertPadding(page, 17, 19, 23, 29);
        assertEquals(37, page.getLeft());
        assertEquals(33, page.getTop());
        assertEquals(400 - 19, page.getRight());
        assertEquals(800 - 49, page.getBottom());
        assertTrue("Descendants must not add system insets a second time", returned.isConsumed());
    }

    @Test public void redispatchAndReinstallDoNotAccumulateNavigationPadding() {
        FrameLayout container = container();
        WindowInsets buttons = insets(Insets.of(0, 24, 0, 48), Insets.NONE, Insets.NONE);
        PlayInsets.install(container);
        container.dispatchApplyWindowInsets(buttons);
        PlayInsets.install(container);
        container.dispatchApplyWindowInsets(buttons);
        assertPadding(container, 5, 31, 11, 61);

        container.dispatchApplyWindowInsets(insets(Insets.of(0, 24, 0, 16), Insets.NONE, Insets.NONE));
        assertPadding(container, 5, 31, 11, 29);
    }

    @Test public void keyboardUsesMaximumInsetAndHidingItRestoresNavigationArea() {
        FrameLayout container = container();
        View page = new View(container.getContext());
        container.addView(page, new FrameLayout.LayoutParams(-1, -1));
        PlayInsets.install(container);
        container.dispatchApplyWindowInsets(insets(
                Insets.of(0, 24, 0, 48), Insets.NONE, Insets.of(0, 0, 0, 300)));
        measure(container);
        assertPadding(container, 5, 31, 11, 313);
        assertEquals("The keyboard and navigation bar must not be summed", 800 - 313, page.getBottom());

        container.dispatchApplyWindowInsets(insets(Insets.of(0, 24, 0, 48), Insets.NONE, Insets.NONE));
        measure(container);
        assertEquals(800 - 61, page.getBottom());
    }

    @Test public void hiddenBarsRestoreOriginalPadding() {
        FrameLayout container = container();
        PlayInsets.install(container);
        container.dispatchApplyWindowInsets(insets(Insets.of(0, 24, 0, 36), Insets.NONE, Insets.NONE));
        container.dispatchApplyWindowInsets(insets(Insets.NONE, Insets.NONE, Insets.NONE));
        assertPadding(container, 5, 7, 11, 13);
    }

    @Test public void activityHooksProtectReplacementPagesOnlyInPlayBuild() {
        ActivityController<PageActivity> controller = Robolectric.buildActivity(PageActivity.class);
        try {
            PageActivity activity = controller.setup().get();
            FrameLayout content = activity.findViewById(android.R.id.content);
            WindowInsets bars = insets(Insets.of(0, 24, 0, 36), Insets.NONE, Insets.NONE);
            content.dispatchApplyWindowInsets(bars);
            measure(content);
            int expectedTop = BuildConfig.GOOGLE_PLAY_BUILD ? 24 : 0;
            int expectedBottom = BuildConfig.GOOGLE_PLAY_BUILD ? 36 : 0;
            assertEquals(expectedTop, content.getPaddingTop());
            assertEquals(expectedBottom, content.getPaddingBottom());
            assertPadding(content.getChildAt(0), 9, 10, 11, 12);

            // Charging/setup pages use setContentView again while already visible.
            activity.replacePage();
            measure(content);
            assertSame(content, activity.findViewById(android.R.id.content));
            assertEquals(expectedTop, content.getChildAt(0).getTop());
            assertEquals(800 - expectedBottom, content.getChildAt(0).getBottom());
            assertPadding(content.getChildAt(0), 9, 10, 11, 12);
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test @Config(sdk = 34)
    public void olderAndroidKeepsLegacyWindowLayoutEvenInPlayBuild() {
        ActivityController<PageActivity> controller = Robolectric.buildActivity(PageActivity.class);
        try {
            PageActivity activity = controller.setup().get();
            FrameLayout content = activity.findViewById(android.R.id.content);
            content.dispatchApplyWindowInsets(insets(Insets.of(0, 24, 0, 36), Insets.NONE, Insets.NONE));
            assertPadding(content, 0, 0, 0, 0);
            assertPadding(content.getChildAt(0), 9, 10, 11, 12);
        } finally {
            controller.pause().stop().destroy();
        }
    }

    private static FrameLayout container() {
        FrameLayout frame = new FrameLayout(RuntimeEnvironment.getApplication());
        frame.setPadding(5, 7, 11, 13);
        return frame;
    }

    private static WindowInsets insets(Insets bars, Insets cutout, Insets ime) {
        return new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(), bars)
                .setVisible(WindowInsets.Type.systemBars(), !Insets.NONE.equals(bars))
                .setInsets(WindowInsets.Type.displayCutout(), cutout)
                .setVisible(WindowInsets.Type.displayCutout(), !Insets.NONE.equals(cutout))
                .setInsets(WindowInsets.Type.ime(), ime)
                .setVisible(WindowInsets.Type.ime(), !Insets.NONE.equals(ime))
                .build();
    }

    private static void measure(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 400, 800);
    }

    private static void assertPadding(View view, int left, int top, int right, int bottom) {
        assertArrayEquals(new int[]{left, top, right, bottom}, new int[]{view.getPaddingLeft(),
                view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom()});
    }
}
