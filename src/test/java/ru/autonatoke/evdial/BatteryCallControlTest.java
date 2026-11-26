package ru.autonatoke.evdial;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Native call-control layout with callback counters only; no telephony application or calls. */
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class BatteryCallControlTest {
    private final int widthDp, profilePercent;
    private final float fontScale;
    private final boolean incoming;
    private Context context;

    @Parameters(name = "width={0}dp,font={1},profile={2}%,incoming={3}")
    public static Collection<Object[]> configurations() {
        List<Object[]> values = new ArrayList<>();
        Object[][] sizes = {{320, 1f, 100}, {360, 1f, 100}, {320, 2f, 150}, {393, 1.3f, 125}};
        for (Object[] size : sizes)
            for (boolean ringing : new boolean[]{true, false})
                values.add(new Object[]{size[0], size[1], size[2], ringing});
        return values;
    }

    public BatteryCallControlTest(int width, float font, int profile, boolean ringing) {
        widthDp = width;
        fontScale = font;
        profilePercent = profile;
        incoming = ringing;
    }

    @Test
    public void lightningActionsFitAndPreserveCallActionGuards() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        RuntimeEnvironment.setQualifiers("ru-rRU-w" + widthDp + "dp-h800dp-xhdpi");
        RuntimeEnvironment.setFontScale(fontScale);
        context = new ContextThemeWrapper(app, R.style.StationTheme);
        UiProfiles.select(context, UiProfiles.EV);
        UiProfiles.set(context, UiProfiles.SCALE, profilePercent);
        assertEquals(fontScale, context.getResources().getConfiguration().fontScale, .001f);
        assertEquals(widthDp, context.getResources().getConfiguration().screenWidthDp);

        int[] callbacks = new int[2]; // answer, reject/end
        BatteryCallControl control = new BatteryCallControl(context, incoming,
                () -> callbacks[0]++, () -> callbacks[1]++);
        LinearLayout root = measuredRoot(control);
        Button negative = ReflectionHelpers.getField(control, "negative");
        Button positive = ReflectionHelpers.getField(control, "positive");
        View track = ReflectionHelpers.getField(control, "track");
        assertActionFits(root, negative, "red reject/end");
        assertLightning(negative, false);
        assertLightning(positive, true);
        assertEquals("Answer only displayed during incoming call",
                incoming ? View.VISIBLE : View.GONE, positive.getVisibility());
        if (incoming) assertActionFits(root, positive, "green answer");
        assertFullyInside(root, track, "battery");
        assertTrue("Battery is a full-width control", track.getWidth() >= dp(widthDp - 40));
        renderPreview(root);

        negative.performClick();
        negative.performClick();
        positive.performClick();
        assertEquals("Reject/end dispatches once and repeated taps are suppressed", 1, callbacks[1]);
        assertEquals("Other action is also suppressed during debounce", 0, callbacks[0]);

        control.setEnabled(false);
        advance();
        negative.performClick();
        positive.performClick();
        assertEquals("Disabled reject/end is inert", 1, callbacks[1]);
        assertEquals("Disabled answer is inert", 0, callbacks[0]);
        MotionEvent down = event(MotionEvent.ACTION_DOWN, track.getWidth() / 2f, track.getHeight() / 2f, 0);
        try { assertFalse("Disabled battery cannot begin a gesture", track.onTouchEvent(down)); }
        finally { down.recycle(); }

        control.setEnabled(true);
        positive.performClick();
        assertEquals("Answer callback requires an incoming call", incoming ? 1 : 0, callbacks[0]);
        if (!incoming) {
            negative.performClick();
            assertEquals("End remains usable after re-enabling", 2, callbacks[1]);
        }

        int beforeAnswer = callbacks[0], beforeEnd = callbacks[1];
        control.setRinging(!incoming);
        measure(root);
        assertEquals("Mode transition updates answer visibility",
                incoming ? View.GONE : View.VISIBLE, positive.getVisibility());
        positive.performClick();
        assertEquals("A stale answer click cannot answer after ringing ends",
                beforeAnswer + (incoming ? 0 : 1), callbacks[0]);
        if (incoming) {
            negative.performClick();
            assertEquals("Mode transition allows ending the ongoing call", beforeEnd + 1, callbacks[1]);
        }
        assertActionFits(root, negative, "reject/end after mode transition");

        // The battery's existing drag alternative must continue to use the same guarded callbacks.
        int[] gestureCallbacks = new int[2];
        BatteryCallControl gestures = new BatteryCallControl(context, incoming,
                () -> gestureCallbacks[0]++, () -> gestureCallbacks[1]++);
        measuredRoot(gestures);
        View gestureTrack = ReflectionHelpers.getField(gestures, "track");
        drag(gestureTrack, -80, false);
        assertEquals("Left drag dispatches reject/end once", 1, gestureCallbacks[1]);
        assertEquals("Left drag never answers", 0, gestureCallbacks[0]);
        advance();
        drag(gestureTrack, 80, false);
        assertEquals("Right drag answers only while ringing", incoming ? 1 : 0, gestureCallbacks[0]);
        assertEquals("Right drag never ends the call", 1, gestureCallbacks[1]);
        advance();
        drag(gestureTrack, -80, true);
        assertEquals("Cancelled drag never commits", 1, gestureCallbacks[1]);
        gestures.setEnabled(false); // Cancel any rebound animation before leaving the fixture.
    }

    private LinearLayout measuredRoot(BatteryCallControl control) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(8));
        root.setBackgroundColor(UiProfiles.active(context).base);
        root.addView(control, new LinearLayout.LayoutParams(-1, -2));
        measure(root);
        return root;
    }

    private void measure(View view) {
        int width = View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(dp(320), View.MeasureSpec.AT_MOST);
        for (int i = 0; i < 3; i++) {
            view.measure(width, height);
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        }
        assertTrue("Control has finite compact height", view.getMeasuredHeight() < dp(320));
    }

    private void assertActionFits(ViewGroup root, Button button, String label) {
        assertEquals(label + " visible", View.VISIBLE, button.getVisibility());
        assertTrue(label + " touch width", button.getWidth() >= dp(48));
        assertTrue(label + " touch height", button.getHeight() >= dp(48));
        assertTrue(label + " clickable", button.isClickable());
        CharSequence accessible = button.getContentDescription() == null
                ? button.getText() : button.getContentDescription();
        assertTrue(label + " accessible action name", accessible != null && accessible.length() > 0);
        assertFullyInside(root, button, label);
        Layout text = button.getLayout();
        assertNotNull(label + " native text layout", text);
        assertEquals(label + " one short caption", 1, text.getLineCount());
        assertEquals(label + " no caption truncation", button.length(), text.getLineEnd(0));
        assertEquals(label + " no ellipsis", 0, text.getEllipsisCount(0));
        int width = button.getWidth() - button.getCompoundPaddingLeft() - button.getCompoundPaddingRight();
        int height = button.getHeight() - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom();
        assertTrue(label + " caption width", text.getLineWidth(0) <= width + .5f);
        assertTrue(label + " caption height", text.getHeight() <= height);
        assertEquals(label + " no horizontal scrolling", 0, button.getScrollX());
        assertEquals(label + " no vertical scrolling", 0, button.getScrollY());
    }

    private static void assertFullyInside(ViewGroup root, View child, String label) {
        Rect area = new Rect(0, 0, child.getWidth(), child.getHeight());
        root.offsetDescendantRectToMyCoords(child, area);
        assertTrue(label + " left", area.left >= root.getPaddingLeft());
        assertTrue(label + " right", area.right <= root.getWidth() - root.getPaddingRight());
        assertTrue(label + " top", area.top >= root.getPaddingTop());
        assertTrue(label + " bottom", area.bottom <= root.getHeight() - root.getPaddingBottom());
        View view = child;
        while (view != root && view.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view.getParent();
            assertTrue(label + " within parent width", view.getLeft() >= 0 && view.getRight() <= parent.getWidth());
            assertTrue(label + " within parent height", view.getTop() >= 0 && view.getBottom() <= parent.getHeight());
            view = parent;
        }
    }

    private void assertLightning(Button button, boolean green) {
        Drawable lightning = button.getCompoundDrawables()[1];
        assertNotNull("Lightning drawable above the action caption", lightning);
        Rect bounds = lightning.getBounds();
        assertTrue("Lightning has a visible size", bounds.width() >= dp(24) && bounds.height() >= dp(24));
        // Render the drawable itself: Android/OEM emoji rendering must not determine its shape or colour.
        Bitmap icon = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(icon);
            canvas.translate(-bounds.left, -bounds.top);
            lightning.draw(canvas);
            int coloured = 0;
            for (int y = 0; y < icon.getHeight(); y++)
                for (int x = 0; x < icon.getWidth(); x++) {
                    int pixel = icon.getPixel(x, y);
                    if (Color.alpha(pixel) < 128) continue;
                    if (green ? Color.green(pixel) > Color.red(pixel) + 25 && Color.green(pixel) > Color.blue(pixel) + 15
                            : Color.red(pixel) > Color.green(pixel) + 35 && Color.red(pixel) > Color.blue(pixel) + 35)
                        coloured++;
                }
            assertTrue((green ? "Green answer" : "Red reject/end") + " lightning is visibly rendered",
                    coloured > icon.getWidth() * icon.getHeight() / 30);
        } finally { icon.recycle(); }
        if (button.getVisibility() == View.VISIBLE) {
            assertTrue("Lightning fits button width", bounds.width() <= button.getWidth() - button.getPaddingLeft() - button.getPaddingRight());
            assertTrue("Lightning fits button height", bounds.height() <= button.getHeight() - button.getPaddingTop() - button.getPaddingBottom());
        }
    }

    private void drag(View track, int dxDp, boolean cancel) {
        float x = track.getWidth() / 2f, y = track.getHeight() / 2f;
        int[] actions = {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE,
                cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP};
        for (int i = 0; i < actions.length; i++) {
            MotionEvent motion = event(actions[i], x + (i == 0 ? 0 : dp(dxDp)), y, i * 35);
            try { assertTrue("Battery consumes its drag", track.onTouchEvent(motion)); }
            finally { motion.recycle(); }
        }
    }

    private static MotionEvent event(int action, float x, float y, long offset) {
        long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now + offset, action, x, y, 0);
    }

    private static void advance() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
    }

    private void renderPreview(View view) throws Exception {
        String size = widthDp == 360 && fontScale == 1f ? "normal"
                : widthDp == 320 && fontScale == 2f ? "large" : null;
        if (size == null) return;
        String name = "call-control-" + (incoming ? "incoming-" : "ongoing-") + size + ".webp";
        File target = new File(System.getProperty("evdial.previewDir", "build/reports/keypad"), name);
        assertTrue("Preview directory", target.getParentFile().isDirectory() || target.getParentFile().mkdirs());
        Bitmap full = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Bitmap scaled = null;
        try {
            view.draw(new Canvas(full));
            int width = Math.min(360, full.getWidth());
            int height = Math.round((float) full.getHeight() * width / full.getWidth());
            scaled = Bitmap.createScaledBitmap(full, width, height, true);
            try (FileOutputStream output = new FileOutputStream(target)) {
                assertTrue("Native call-control preview", scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 70, output));
            }
            System.out.println("CALL_CONTROL_PREVIEW " + name + " bytes=" + target.length());
        } finally {
            if (scaled != null && scaled != full) scaled.recycle();
            full.recycle();
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, context.getResources().getDisplayMetrics()));
    }
}
