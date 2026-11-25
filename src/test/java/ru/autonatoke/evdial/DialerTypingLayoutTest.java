package ru.autonatoke.evdial;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises the real dialer, text watcher, asynchronous search and keypad listeners. */
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 34, application = DialerTypingLayoutTest.NoCallsPhone.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class DialerTypingLayoutTest {
    private static final String DIGITS = "123456789*0#";
    private static final List<SmartDialSearch.Entry> CONTACTS = Arrays.asList(
            person("Алина", "+79161234567"),
            person("Борис", "+79171234567"),
            person("Зоя", "+79261234567"),
            new SmartDialSearch.Entry("Алина", "+79161234567", "", true, false, 100),
            new SmartDialSearch.Entry("Зоя", "+79261234567", "", true, false, 200));

    /** Empty preferences initialize the real application without loading native SIP. */
    public static class NoCallsPhone extends Phone {
        @Override synchronized Core sipCore() {
            throw new AssertionError("Typing/layout must not initialize SIP");
        }
        @Override void dial(String number) {
            throw new AssertionError("Typing/layout must not place a call");
        }
        @Override void updateService() {
            throw new AssertionError("Typing/layout must not activate a call service");
        }
    }

    private final int widthDp, heightDp, profilePercent;
    private final float fontScale;
    private final boolean preScrolled;
    private Dialer activity;
    private View decor;
    private ScrollView scroll;
    private ExecutorService worker;
    private Bitmap bitmap;
    private Geometry baseline;

    @Parameters(name = "{0}x{1}dp,font={2},profile={3}%,scrolled={4}")
    public static Collection<Object[]> configurations() {
        return Arrays.asList(new Object[][]{
                {320, 640, 1f, 100, false},
                {360, 800, 1f, 100, false},
                {393, 800, 1f, 125, false},
                {320, 640, 1.3f, 125, false},
                {393, 800, 2f, 125, false},
                {320, 640, 1f, 100, true},
                {360, 640, 1.3f, 125, true},
                {393, 800, 2f, 150, true}
        });
    }

    public DialerTypingLayoutTest(int width, int height, float font, int profile, boolean scrolled) {
        widthDp = width;
        heightDp = height;
        fontScale = font;
        profilePercent = profile;
        preScrolled = scrolled;
    }

    @Test
    public void typingAndResultChangesKeepTheKeypadAndScrollStationary() throws Exception {
        Phone app = (Phone) RuntimeEnvironment.getApplication();
        RuntimeEnvironment.setQualifiers("ru-rRU-w" + widthDp + "dp-h" + heightDp + "dp-xhdpi");
        RuntimeEnvironment.setFontScale(fontScale);
        UiProfiles.select(app, UiProfiles.EV);
        UiProfiles.set(app, UiProfiles.SCALE, profilePercent);
        // ACTION_DIAL avoids launcher-only role prompts and startup tab selection.
        ActivityController<Dialer> controller = Robolectric.buildActivity(Dialer.class,
                new Intent(Intent.ACTION_DIAL));
        try {
            activity = controller.create().start().resume().visible().get();
            assertEquals(fontScale, activity.getResources().getConfiguration().fontScale, .001f);
            assertEquals(widthDp, activity.getResources().getConfiguration().screenWidthDp);
            decor = activity.getWindow().getDecorView();
            scroll = (ScrollView) activity.body.getParent();
            worker = ReflectionHelpers.getField(activity, "smartWorker");
            bitmap = Bitmap.createBitmap(dp(widthDp), dp(heightDp), Bitmap.Config.ARGB_8888);

            // Drain the initial provider load before replacing it with synthetic contacts.
            awaitWorker();
            shadowOf(Looper.getMainLooper()).idle();
            ReflectionHelpers.setField(activity, "smartEntries", CONTACTS);
            ReflectionHelpers.setField(activity, "smartLoaded", true);
            activity.erase.performLongClick();
            resolveSearch();
            assertMatches(2, true);
            layoutAndDraw();
            activity.number.requestFocus();
            activity.number.setSelection(activity.number.length());
            layoutAndDraw();
            // The small-screen case puts the number field offscreen while the keypad is reachable.
            int initialScroll = !preScrolled ? 0 : widthDp == 320 ? activity.body.getHeight() : dp(60);
            scroll.scrollTo(0, initialScroll);
            layoutAndDraw();
            if (preScrolled) assertTrue("Fixture must start below the top", scroll.getScrollY() > 0);
            else assertEquals("Fixture starts at the top", 0, scroll.getScrollY());
            if (preScrolled && widthDp == 320) {
                int[] fieldPosition = new int[2], viewportPosition = new int[2];
                activity.number.getLocationOnScreen(fieldPosition);
                scroll.getLocationOnScreen(viewportPosition);
                assertTrue("Near-bottom fixture must put the entire number field above the viewport",
                        fieldPosition[1] + activity.number.getHeight() < viewportPosition[1]);
            }
            baseline = new Geometry(activity, scroll);

            // A previously resolved name must not add/remove a row on the next edit.
            activity.lookupNumber = activity.number.getText().toString();
            activity.knownName = "Синтетический контакт с длинным именем";
            activity.refresh();
            assertEquals("Known name is displayed", View.VISIBLE, activity.detail.getVisibility());
            layoutAndDraw();
            baseline.assertUnchanged(new Geometry(activity, scroll), "known name becomes visible");

            press('7');
            resolvedAcrossBusyCall(3, false, "many results after call-state race");
            press('9');
            press('1');
            resolved(2, false, "two results");
            press('6');
            resolved(1, false, "single result");
            press('0');
            resolved(0, false, "no result");
            backspace();
            resolved(1, false, "single result after backspace");

            // Long input changes fitted text size and disables the number menu.
            type("1234567890123456789012345");
            resolved(0, false, "long number");
            press('8');
            resolved(0, false, "next digit after long number");
            assertHorizontalCursorVisible();
            assertEquals("Unavailable menu keeps its slot", View.INVISIBLE, activity.numberMenu.getVisibility());
            clear();
            resolved(2, true, "recent calls after clearing");
            assertEquals(View.VISIBLE, activity.numberMenu.getVisibility());

            // These labels used to be able to grow the call button or number row.
            type("112");
            resolved(0, false, "emergency number label");
            clear();
            type("*#");
            resolved(0, false, "service code label");
            assertEquals(View.INVISIBLE, activity.numberMenu.getVisibility());
            clear();
            resolved(2, true, "return to recents");
            renderPreview();
            if (preScrolled && widthDp == 320) assertAccessibilityCanRevealNumber();
        } finally {
            if (activity != null) controller.pause().stop().destroy();
            if (bitmap != null) bitmap.recycle();
        }
    }

    private static SmartDialSearch.Entry person(String name, String number) {
        return new SmartDialSearch.Entry(name, number,
                "content://com.android.contacts/contacts/lookup/synthetic", false, false, 0);
    }

    private void press(char digit) throws Exception {
        int index = DIGITS.indexOf(digit);
        assertTrue(index >= 0);
        LinearLayout row = (LinearLayout) activity.keypad.getChildAt(index / 3);
        LinearLayout slot = (LinearLayout) row.getChildAt(index % 3);
        assertTrue("Production keypad listener " + digit, slot.getChildAt(0).performClick());
        pending("key " + digit);
    }

    private void type(String digits) throws Exception {
        for (int i = 0; i < digits.length(); i++) press(digits.charAt(i));
    }

    private void backspace() throws Exception {
        assertTrue("Production erase listener", activity.erase.performClick());
        pending("backspace");
    }

    private void clear() throws Exception {
        assertTrue("Production clear listener", activity.erase.performLongClick());
        pending("clear");
    }

    private void pending(String phase) throws Exception {
        assertEquals(phase + ": pending suggestions retain their space",
                View.INVISIBLE, activity.smartPanel.getVisibility());
        assertFalse(phase + ": stale primary action removed", activity.smartPrimary.performClick());
        assertFalse(phase + ": stale contact action removed", activity.smartInfo.performClick());
        assertFalse(phase + ": stale More action removed", activity.smartMore.performClick());
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), phase + " / pending");
        assertEquals(phase + ": editing hides the known name without collapsing its row",
                View.INVISIBLE, activity.detail.getVisibility());
        // A line-registration observer refresh can arrive during the search debounce.
        activity.refresh();
        assertEquals(phase + ": observer refresh preserves pending slot",
                View.INVISIBLE, activity.smartPanel.getVisibility());
        assertEquals(phase + ": observer does not restore the stale name",
                View.INVISIBLE, activity.detail.getVisibility());
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), phase + " / pending observer refresh");
    }

    private void resolved(int total, boolean recent, String phase) throws Exception {
        resolveSearch();
        assertMatches(total, recent);
        assertEquals(phase + ": results shown", View.VISIBLE, activity.smartPanel.getVisibility());
        assertEquals(phase + ": More slot", recent || total > 1 ? View.VISIBLE : View.INVISIBLE,
                activity.smartMore.getVisibility());
        assertEquals(phase + ": contact-info slot", total > 0 ? View.VISIBLE : View.INVISIBLE,
                activity.smartInfo.getVisibility());
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), phase + " / resolved");
    }

    private void resolvedAcrossBusyCall(int total, boolean recent, String phase) throws Exception {
        // Only null/non-null call state is relevant to binding a completed search result.
        Call marker = (Call) Proxy.newProxyInstance(Call.class.getClassLoader(),
                new Class<?>[]{Call.class}, (proxy, method, arguments) -> {
                    throw new AssertionError("Layout race must not invoke Call." + method.getName());
                });
        activity.phone.active = marker;
        try {
            resolveSearch();
            assertMatches(total, recent);
            assertEquals(phase + ": suggestions hidden during call", View.GONE,
                    activity.smartPanel.getVisibility());
        } finally {
            activity.phone.active = null;
        }
        // Do not refresh while the marker is active: this test does not exercise call UI.
        activity.refresh();
        assertEquals(phase + ": completed result appears after call", View.VISIBLE,
                activity.smartPanel.getVisibility());
        for (View action : new View[]{activity.smartPrimary, activity.smartInfo, activity.smartMore}) {
            assertTrue(phase + ": completed result action enabled", action.isEnabled());
            assertTrue(phase + ": completed result action bound", action.hasOnClickListeners());
        }
        // The result actions are inspected, never clicked: they can initiate a call.
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), phase + " / restored");
    }

    private void assertMatches(int total, boolean recent) {
        SmartDialSearch.Matches matches = ReflectionHelpers.getField(activity, "smartMatches");
        assertNotNull("Production search completed", matches);
        assertEquals("Synthetic result count for " + activity.number.getText(), total, matches.total);
        assertEquals("Recent mode", recent, matches.isRecent);
    }

    private void assertHorizontalCursorVisible() {
        assertEquals("A real keypad press advances the caret", activity.number.length(),
                activity.number.getSelectionEnd());
        android.text.Layout textLayout = activity.number.getLayout();
        assertNotNull(textLayout);
        int width = activity.number.getWidth() - activity.number.getCompoundPaddingLeft()
                - activity.number.getCompoundPaddingRight();
        if (textLayout.getLineWidth(0) > width) {
            float caret = textLayout.getPrimaryHorizontal(activity.number.getSelectionEnd())
                    - activity.number.getScrollX();
            assertTrue("Overflowing number still scrolls horizontally to its caret", caret >= -dp(2)
                    && caret <= width + dp(2));
        }
    }

    private void assertAccessibilityCanRevealNumber() {
        assertTrue("Explicit accessibility reveal remains supported",
                activity.number.performAccessibilityAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId(), null));
        layoutAndDraw();
        int[] fieldPosition = new int[2], viewportPosition = new int[2];
        activity.number.getLocationOnScreen(fieldPosition);
        scroll.getLocationOnScreen(viewportPosition);
        assertTrue("Accessibility reveals the full number field",
                fieldPosition[1] >= viewportPosition[1] - 1
                && fieldPosition[1] + activity.number.getHeight()
                <= viewportPosition[1] + scroll.getHeight() + 1);
    }

    private void resolveSearch() throws Exception {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        awaitWorker();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void awaitWorker() throws Exception {
        worker.submit(() -> { }).get(10, TimeUnit.SECONDS);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void layoutAndDraw() {
        int width = View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(dp(heightDp), View.MeasureSpec.EXACTLY);
        for (int pass = 0; pass < 3; pass++) {
            decor.measure(width, height);
            decor.layout(0, 0, dp(widthDp), dp(heightDp));
            decor.getViewTreeObserver().dispatchOnPreDraw();
            // Text/cursor geometry settles on every pass; one real draw is sufficient.
            if (pass == 2) decor.draw(new Canvas(bitmap));
            shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private void renderPreview() throws Exception {
        if (widthDp != 320 || fontScale != 1f || preScrolled) return;
        String directory = System.getProperty("evdial.previewDir");
        if (directory == null || directory.isEmpty()) return;
        File file = new File(directory, "dialer-stable-typing-320dp.webp");
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        int width = Math.min(393, bitmap.getWidth());
        int height = Math.round(bitmap.getHeight() * width / (float) bitmap.getWidth());
        Bitmap compact = Bitmap.createScaledBitmap(bitmap, width, height, true);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            assertTrue(compact.compress(Bitmap.CompressFormat.WEBP_LOSSY, 82, stream));
        } finally {
            if (compact != bitmap) compact.recycle();
        }
    }

    private static final class Geometry {
        final int numberHeight, numberWidth, suggestionsHeight, keypadTop, callHeight, scrollY;
        final int[] keypadScreen = new int[2], callScreen = new int[2];

        Geometry(Dialer dialer, ScrollView scroll) {
            numberHeight = ((View) dialer.number.getParent()).getHeight();
            numberWidth = dialer.number.getWidth();
            suggestionsHeight = dialer.smartPanel.getHeight();
            keypadTop = dialer.keypad.getTop();
            callHeight = dialer.action.getHeight();
            scrollY = scroll.getScrollY();
            dialer.keypad.getLocationOnScreen(keypadScreen);
            dialer.action.getLocationOnScreen(callScreen);
        }

        void assertUnchanged(Geometry now, String phase) {
            assertEquals(phase + ": number-row height", numberHeight, now.numberHeight);
            assertEquals(phase + ": number-field width", numberWidth, now.numberWidth);
            assertEquals(phase + ": reserved suggestions height", suggestionsHeight, now.suggestionsHeight);
            assertEquals(phase + ": keypad position in body", keypadTop, now.keypadTop);
            assertArrayEquals(phase + ": keypad screen position", keypadScreen, now.keypadScreen);
            assertArrayEquals(phase + ": call-button screen position", callScreen, now.callScreen);
            assertEquals(phase + ": call-button height", callHeight, now.callHeight);
            assertEquals(phase + ": scroll position", scrollY, now.scrollY);
        }
    }
}
