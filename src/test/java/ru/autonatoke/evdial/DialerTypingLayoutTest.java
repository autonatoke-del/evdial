package ru.autonatoke.evdial;

import android.content.Intent;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.os.Looper;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises the real dialer, text watcher, asynchronous search and keypad listeners. */
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = {34, 35}, application = DialerTypingLayoutTest.NoCallsPhone.class)
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
    private final String theme;
    private Dialer activity;
    private View decor, content, appRoot;
    private WindowInsets playWindowInsets;
    private int safeTop, safeBottom;
    private ScrollView scroll;
    private ExecutorService worker;
    private Bitmap bitmap;
    private Geometry baseline;

    @Parameters(name = "{0}x{1}dp,font={2},profile={3}%,theme={4}")
    public static Collection<Object[]> configurations() {
        return Arrays.asList(new Object[][]{
                {320, 568, 1f, 100, UiProfiles.EV},
                {320, 640, 1f, 100, UiProfiles.EV},
                {360, 640, 1f, 100, UiProfiles.LIGHT},
                {360, 740, 1f, 100, UiProfiles.EV},
                {393, 800, 1f, 100, UiProfiles.MINIMAL},
                {393, 873, 1f, 100, UiProfiles.EV},
                {320, 568, 1.3f, 125, UiProfiles.EV},
                {320, 640, 2f, 150, UiProfiles.EV},
                {360, 640, 1.3f, 150, UiProfiles.EV},
                {360, 740, 2f, 150, UiProfiles.EV},
                {393, 800, 1.3f, 125, UiProfiles.EV},
                {393, 873, 2f, 150, UiProfiles.EV}
        });
    }

    public DialerTypingLayoutTest(int width, int height, float font, int profile, String theme) {
        widthDp = width;
        heightDp = height;
        fontScale = font;
        profilePercent = profile;
        this.theme = theme;
    }

    @Test
    public void dialingControlsStayVisibleAndStableAtEverySupportedSize() throws Exception {
        Phone app = (Phone) RuntimeEnvironment.getApplication();
        RuntimeEnvironment.setQualifiers("ru-rRU-w" + widthDp + "dp-h" + heightDp + "dp-xhdpi");
        RuntimeEnvironment.setFontScale(fontScale);
        UiProfiles.select(app, theme);
        UiProfiles.set(app, UiProfiles.SCALE, profilePercent);
        // Upgrade from RC4 can retain this preference; it must not recreate an inline EV block.
        String className = Dialer.class.getName(), packageName = app.getPackageName();
        String preferenceName = className.startsWith(packageName + ".")
                ? className.substring(packageName.length() + 1) : className;
        app.getSharedPreferences(preferenceName, 0).edit().putBoolean("showEvServices", true).apply();
        // ACTION_DIAL avoids launcher-only role prompts and startup tab selection.
        ActivityController<Dialer> controller = Robolectric.buildActivity(Dialer.class,
                new Intent(Intent.ACTION_DIAL));
        try {
            activity = controller.create().start().resume().visible().get();
            assertEquals(fontScale, activity.getResources().getConfiguration().fontScale, .001f);
            assertEquals(widthDp, activity.getResources().getConfiguration().screenWidthDp);
            decor = activity.getWindow().getDecorView();
            content = activity.findViewById(android.R.id.content);
            appRoot = activity.findViewById(android.R.id.custom);
            if (BuildConfig.GOOGLE_PLAY_BUILD && Build.VERSION.SDK_INT >= 35) {
                // A 32dp notch overlaps the 24dp status bar. Exercise both three-button
                // (48dp) and gesture (24dp) navigation with the existing viewport cases.
                safeTop = dp(32);
                safeBottom = dp(widthDp < 393 ? 48 : 24);
                playWindowInsets = new WindowInsets.Builder()
                        .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, dp(24), 0, 0))
                        .setVisible(WindowInsets.Type.statusBars(), true)
                        .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, safeBottom))
                        .setVisible(WindowInsets.Type.navigationBars(), true)
                        .setInsets(WindowInsets.Type.displayCutout(), Insets.of(0, safeTop, 0, 0))
                        .setVisible(WindowInsets.Type.displayCutout(), true)
                        .build();
            }
            scroll = firstScrollView(appRoot);
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
            // The complete number/grid/call pane must fit before the user scrolls anything.
            assertDialingControlsVisible("initial viewport");
            baseline = new Geometry(activity, scroll);
            assertUpperScrollingDoesNotMoveDialingControls();
            assertHeaderPanelsDoNotMoveDialingControls();

            // A previously resolved name must not add/remove a row on the next edit.
            activity.lookupNumber = activity.number.getText().toString();
            activity.knownName = "Синтетический контакт с длинным именем";
            activity.refresh();
            layoutAndDraw();
            baseline.assertUnchanged(new Geometry(activity, scroll), "known name becomes visible");
            assertDialingControlsVisible("known name");

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
            // Already-visible number fields need no ancestor scroll for accessibility reveal.
            activity.number.performAccessibilityAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId(), null);
            layoutAndDraw();
            baseline.assertUnchanged(new Geometry(activity, scroll), "accessibility reveal");
            assertDialingControlsVisible("accessibility reveal");
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
        assertFalse(phase + ": stale primary action removed", activity.smartPrimary.performClick());
        assertFalse(phase + ": stale contact action removed", activity.smartInfo.performClick());
        assertFalse(phase + ": stale More action removed", activity.smartMore.performClick());
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), phase + " / pending");
        assertDialingControlsVisible(phase + " / pending");
        // A line-registration observer refresh can arrive during the search debounce.
        activity.refresh();
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), phase + " / pending observer refresh");
        assertDialingControlsVisible(phase + " / pending observer refresh");
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
        assertDialingControlsVisible(phase + " / resolved");
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
        baseline.assertLocalUnchanged(new Geometry(activity, scroll), phase + " / restored");
        // Call mode intentionally reflows the viewport. Restore this fixture's scroll before
        // continuing the strict screen-position checks for ordinary idle typing.
        if (scroll != null) scroll.scrollTo(0, baseline.scrollY);
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), phase + " / idle fixture restored");
        assertDialingControlsVisible(phase + " / idle fixture restored");
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

    private void assertUpperScrollingDoesNotMoveDialingControls() {
        if (scroll == null) return;
        scroll.scrollTo(0, Integer.MAX_VALUE);
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), "upper content scrolled to bottom");
        assertDialingControlsVisible("upper content scrolled to bottom");
        scroll.scrollTo(0, 0);
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), "upper content scrolled to top");
        assertDialingControlsVisible("upper content scrolled to top");
    }

    private void assertHeaderPanelsDoNotMoveDialingControls() {
        View picker = findDescription(appRoot, "Выбрать линию вызова");
        assertNotNull("Compact line selector is discoverable", picker);
        assertTrue("Line selector remains above the number", bounds(picker).bottom <= bounds(activity.number).top);
        assertTrue("Line selector leaves room for the header actions", picker.getWidth() < appRoot.getWidth() * .65f);
        assertTrue("Real line-selector click", picker.performClick());
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("Line choice opens a separate panel", dialog);
        assertTrue("Line choice shown", dialog.isShowing());
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), "line chooser open");
        View sip = dialogChoice(dialog.getWindow().getDecorView(), "SIP");
        assertNotNull("SIP remains selectable", sip);
        assertTrue("SIP choice click", sip.performClick());
        assertFalse("SIP route selected without starting the engine", activity.sim);
        assertEquals("SIP choice persisted", "sip", activity.getPreferences(0).getString("route", ""));
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), "SIP selected");
        assertDialingControlsVisible("SIP selected");

        assertTrue("Reopen line selector", picker.performClick());
        dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        View sim = dialogChoice(dialog.getWindow().getDecorView(), "SIM");
        assertNotNull("SIM remains selectable", sim);
        assertTrue("SIM choice click", sim.performClick());
        assertTrue("SIM route restored", activity.sim);
        assertEquals("SIM choice persisted", "sim", activity.getPreferences(0).getString("route", ""));
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), "SIM restored");
        assertDialingControlsVisible("SIM restored");

        View services = findDescription(appRoot, "Сервисы EV");
        assertNotNull("EV services remain discoverable", services);
        int bodyChildren = activity.body.getChildCount();
        assertTrue("Real car-button click", services.performClick());
        dialog = ShadowDialog.getLatestDialog();
        assertNotNull("EV services open a separate panel", dialog);
        assertTrue("EV services panel shown", dialog.isShowing());
        assertTrue("Existing EV destinations available", clickableCount(dialog.getWindow().getDecorView()) >= 4);
        layoutAndDraw();
        assertEquals("EV services do not insert another inline block", bodyChildren, activity.body.getChildCount());
        baseline.assertUnchanged(new Geometry(activity, scroll), "EV services open");
        dialog.dismiss();
        layoutAndDraw();
        baseline.assertUnchanged(new Geometry(activity, scroll), "EV services closed");
        assertDialingControlsVisible("EV services closed");
    }

    private static View findDescription(View view, String prefix) {
        CharSequence description = view.getContentDescription();
        if (view.isShown() && description != null && description.toString().startsWith(prefix)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View dialogChoice(View view, String token) {
        if (view instanceof android.widget.TextView
                && ((android.widget.TextView) view).getText().toString().contains(token)) {
            View target = view;
            while (!target.hasOnClickListeners() && target.getParent() instanceof View)
                target = (View) target.getParent();
            if (target.hasOnClickListeners()) return target;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = dialogChoice(group.getChildAt(i), token);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int clickableCount(View view) {
        int result = view.hasOnClickListeners() ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) result += clickableCount(group.getChildAt(i));
        }
        return result;
    }

    private static ScrollView firstScrollView(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ScrollView found = firstScrollView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void assertDialingControlsVisible(String phase) {
        Rect viewport = bounds(appRoot), nav = bounds(activity.navigation);
        assertTrue(phase + ": navigation visible", activity.navigation.isShown());
        assertTrue(phase + ": navigation inside app", nav.bottom <= viewport.bottom + 1);
        assertFullyVisible(activity.number, viewport, nav.top, phase + ": number");
        assertTrue(phase + ": number has a touchable height", activity.number.getHeight() >= dp(48));
        Layout numberText = activity.number.getLayout();
        assertNotNull(phase + ": number layout", numberText);
        assertTrue(phase + ": number text fits its fixed-height field", numberText.getHeight()
                <= activity.number.getHeight() - activity.number.getCompoundPaddingTop()
                - activity.number.getCompoundPaddingBottom() + 1);
        Rect numberCard = bounds(activity.idleCard), grid = bounds(activity.keypad);
        assertTrue(phase + ": number immediately above keys", numberCard.bottom <= grid.top + 1
                && grid.top - numberCard.bottom <= dp(16));
        assertTrue(phase + ": keypad exists", activity.keypad.getChildCount() == 4);
        for (int i = 0; i < DIGITS.length(); i++) {
            LinearLayout row = (LinearLayout) activity.keypad.getChildAt(i / 3);
            LinearLayout slot = (LinearLayout) row.getChildAt(i % 3);
            DialpadKey key = (DialpadKey) slot.getChildAt(0);
            String label = phase + ": key " + DIGITS.charAt(i);
            assertFullyVisible(key, viewport, nav.top, label);
            assertTrue(label + " width target", key.getWidth() >= dp(48));
            assertTrue(label + " height target", key.getHeight() >= dp(48));
            assertEquals(label + " numeric contents", String.valueOf(DIGITS.charAt(i)),
                    key.digitView.getText().toString());
            assertTextFits(key.digitView, label);
        }
        assertFullyVisible(activity.action, viewport, nav.top, phase + ": call button");
        assertTrue(phase + ": call touch target", activity.action.getHeight() >= dp(48));
        assertTrue(phase + ": call follows keypad", bounds(activity.action).top >= grid.bottom - 1);
        assertTextFits(activity.action, phase + ": call label");
    }

    private void assertFullyVisible(View view, Rect viewport, int bottom, String phase) {
        Rect full = bounds(view), visible = new Rect();
        assertTrue(phase + ": shown", view.isShown());
        assertTrue(phase + ": visible rectangle exists", view.getGlobalVisibleRect(visible));
        assertEquals(phase + ": unclipped width", view.getWidth(), visible.width());
        assertEquals(phase + ": unclipped height", view.getHeight(), visible.height());
        assertTrue(phase + ": inside left/right", full.left >= viewport.left - 1
                && full.right <= viewport.right + 1);
        assertTrue(phase + ": above navigation", full.top >= viewport.top - 1
                && full.bottom <= bottom + 1);
    }

    private static Rect bounds(View view) {
        int[] position = new int[2];
        view.getLocationOnScreen(position);
        return new Rect(position[0], position[1], position[0] + view.getWidth(),
                position[1] + view.getHeight());
    }

    private void assertTextFits(android.widget.TextView view, String phase) {
        Layout layout = view.getLayout();
        assertNotNull(phase + ": native text layout", layout);
        assertEquals(phase + ": single line", 1, layout.getLineCount());
        assertEquals(phase + ": full label", view.length(), layout.getLineEnd(0));
        assertEquals(phase + ": no ellipsis", 0, layout.getEllipsisCount(0));
        int width = view.getWidth() - view.getCompoundPaddingLeft() - view.getCompoundPaddingRight();
        int height = view.getHeight() - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom();
        assertTrue(phase + ": text width", layout.getLineWidth(0) <= width + 1);
        assertTrue(phase + ": text height", layout.getHeight() <= height + 1);
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
            // Dispatch into the production content listener, never install a test listener
            // or shrink the outer window: the safe area must be deducted exactly once.
            if (playWindowInsets != null) content.dispatchApplyWindowInsets(playWindowInsets);
            decor.measure(width, height);
            decor.layout(0, 0, dp(widthDp), dp(heightDp));
            decor.getViewTreeObserver().dispatchOnPreDraw();
            // Text/cursor geometry settles on every pass; one real draw is sufficient.
            if (pass == 2) decor.draw(new Canvas(bitmap));
            shadowOf(Looper.getMainLooper()).idle();
        }
        if (playWindowInsets != null) {
            assertEquals("Fixture keeps the complete edge-to-edge window width", dp(widthDp), content.getWidth());
            assertEquals("Fixture keeps the complete edge-to-edge window height", dp(heightDp), content.getHeight());
            assertEquals("The real lifecycle-installed listener protects the notch", safeTop, content.getPaddingTop());
            assertEquals("The real listener protects navigation exactly once", safeBottom, content.getPaddingBottom());
            Rect container = bounds(content), viewport = bounds(appRoot);
            assertEquals(container.top + safeTop, viewport.top);
            assertEquals(container.bottom - safeBottom, viewport.bottom);
        }
    }

    private void renderPreview() throws Exception {
        boolean smallest = widthDp == 320 && heightDp == 568 && fontScale == 1f;
        boolean typical = widthDp == 360 && heightDp == 740 && fontScale == 1f;
        boolean enlarged = widthDp == 320 && heightDp == 640 && fontScale == 2f;
        if (!smallest && !typical && !enlarged) return;
        String directory = System.getProperty("evdial.previewDir");
        if (directory == null || directory.isEmpty()) return;
        File file = new File(directory, "dialer-responsive-" + widthDp + "x" + heightDp
                + "-" + (enlarged ? "large" : "normal") + "-api" + Build.VERSION.SDK_INT + ".webp");
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        int width = Math.min(320, bitmap.getWidth());
        int height = Math.round(bitmap.getHeight() * width / (float) bitmap.getWidth());
        Bitmap compact = Bitmap.createScaledBitmap(bitmap, width, height, true);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            assertTrue(compact.compress(Bitmap.CompressFormat.WEBP_LOSSY, 72, stream));
        } finally {
            if (compact != bitmap) compact.recycle();
        }
    }

    private static final class Geometry {
        final int numberHeight, numberWidth, keypadTop, callHeight, scrollY;
        final int[] numberScreen = new int[2], keypadScreen = new int[2], callScreen = new int[2],
                navigationScreen = new int[2];

        Geometry(Dialer dialer, ScrollView scroll) {
            numberHeight = ((View) dialer.number.getParent()).getHeight();
            numberWidth = dialer.number.getWidth();
            keypadTop = dialer.keypad.getTop();
            callHeight = dialer.action.getHeight();
            scrollY = scroll == null ? 0 : scroll.getScrollY();
            dialer.number.getLocationOnScreen(numberScreen);
            dialer.keypad.getLocationOnScreen(keypadScreen);
            dialer.action.getLocationOnScreen(callScreen);
            dialer.navigation.getLocationOnScreen(navigationScreen);
        }

        void assertUnchanged(Geometry now, String phase) {
            assertLocalUnchanged(now, phase);
            assertArrayEquals(phase + ": number screen position", numberScreen, now.numberScreen);
            assertArrayEquals(phase + ": keypad screen position", keypadScreen, now.keypadScreen);
            assertArrayEquals(phase + ": call-button screen position", callScreen, now.callScreen);
            assertArrayEquals(phase + ": navigation screen position", navigationScreen, now.navigationScreen);
        }

        void assertLocalUnchanged(Geometry now, String phase) {
            assertEquals(phase + ": number-row height", numberHeight, now.numberHeight);
            assertEquals(phase + ": number-field width", numberWidth, now.numberWidth);
            assertEquals(phase + ": keypad position in body", keypadTop, now.keypadTop);
            assertEquals(phase + ": call-button height", callHeight, now.callHeight);
        }
    }
}
