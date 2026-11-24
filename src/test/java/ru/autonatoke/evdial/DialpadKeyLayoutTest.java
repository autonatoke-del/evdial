package ru.autonatoke.evdial;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
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
import static org.junit.Assert.*;

/** Measures and draws the production Android component; does not initialize SIP or make calls. */
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class DialpadKeyLayoutTest {
    private static final String DIGITS = "123456789*0#";
    private static final String[] RU = {"", "АБВГ\nABC", "ДЕЖЗ\nDEF", "ИЙКЛ\nGHI",
            "МНОП\nJKL", "РСТУ\nMNO", "ФХЦЧ\nPQRS", "ШЩЪЫ\nTUV", "ЬЭЮЯ\nWXYZ", "", "+", ""};
    private static final String[] EN = {"", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "", "+", ""};
    private final int screenWidthDp;
    private final float fontScale;
    private final int profilePercent;
    private final boolean russian;

    @Parameters(name = "width={0}dp,font={1},profile={2}%,ru={3}")
    public static Collection<Object[]> configurations() {
        List<Object[]> values = new ArrayList<>();
        for (int width : new int[]{320, 360, 393})
            for (float font : new float[]{1f, 1.3f, 2f})
                for (int profile : new int[]{100, 150})
                    for (boolean ru : new boolean[]{true, false})
                        values.add(new Object[]{width, font, profile, ru});
        return values;
    }

    public DialpadKeyLayoutTest(int width, float font, int profile, boolean ru) {
        screenWidthDp = width;
        fontScale = font;
        profilePercent = profile;
        russian = ru;
    }

    @Test
    public void completeDigitsAndAlphabetsFitMeasuredKeys() throws Exception {
        // Create the plain application first: lazy creation must not reset fontScale.
        Application application = RuntimeEnvironment.getApplication();
        RuntimeEnvironment.setQualifiers((russian ? "ru-rRU" : "en-rUS")
                + "-w" + screenWidthDp + "dp-h800dp-xhdpi");
        RuntimeEnvironment.setFontScale(fontScale);
        Context context = new ContextThemeWrapper(application, R.style.StationTheme);
        assertEquals("Effective Android font scale", fontScale,
                context.getResources().getConfiguration().fontScale, .001f);
        assertEquals("Effective Android width", screenWidthDp,
                context.getResources().getConfiguration().screenWidthDp);
        assertEquals("Native graphics SDK", 34, android.os.Build.VERSION.SDK_INT);
        UiProfiles.select(context, UiProfiles.EV);
        UiProfiles.set(context, UiProfiles.SCALE, profilePercent);
        assertEquals("Effective app font preference", profilePercent, UiProfiles.value(context, UiProfiles.SCALE));

        boolean compact = fontScale >= 1.3f;
        int diameter = dp(context, compact ? 72 : 80);
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4));
        grid.setBackgroundColor(UiProfiles.active(context).base);
        List<DialpadKey> keys = new ArrayList<>();
        String[] legends = russian ? RU : EN;
        for (int row = 0; row < 4; row++) {
            LinearLayout line = new LinearLayout(context);
            line.setGravity(Gravity.CENTER);
            line.setBaselineAligned(false);
            grid.addView(line, new LinearLayout.LayoutParams(-1, -2));
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                LinearLayout slot = new LinearLayout(context);
                slot.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams column = new LinearLayout.LayoutParams(0, -2, 1f);
                column.setMargins(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));
                line.addView(slot, column);
                DialpadKey key = new DialpadKey(context, DIGITS.charAt(index), legends[index],
                        UiProfiles.type(context, compact ? 28 : 32),
                        UiProfiles.type(context, 10),
                        UiProfiles.text(context), UiProfiles.muted(context));
                GradientDrawable oval = new GradientDrawable();
                oval.setShape(GradientDrawable.OVAL);
                oval.setColor(UiProfiles.keypad(context));
                oval.setStroke(dp(context, 1), UiProfiles.opacity(UiProfiles.secondary(context), 45));
                key.setBackground(oval);
                slot.addView(key, new LinearLayout.LayoutParams(diameter, diameter));
                keys.add(key);
            }
        }
        int exactWidth = View.MeasureSpec.makeMeasureSpec(dp(context, screenWidthDp), View.MeasureSpec.EXACTLY);
        int maxHeight = View.MeasureSpec.makeMeasureSpec(dp(context, 800), View.MeasureSpec.AT_MOST);
        measureAndLayout(grid, exactWidth, maxHeight);
        // Native TextView auto-size can request a second layout after resolving line metrics.
        measureAndLayout(grid, exactWidth, maxHeight);

        File preview = previewFile();
        if (preview != null) render(grid, preview);

        int baseline = -1;
        for (int index = 0; index < keys.size(); index++) {
            DialpadKey key = keys.get(index);
            String description = "digit " + DIGITS.charAt(index) + " at " + screenWidthDp
                    + "dp / system " + fontScale + " / app " + profilePercent + "% / " + (russian ? "RU" : "EN");
            assertEquals(description + " width", diameter, key.getWidth());
            assertEquals(description + " height", diameter, key.getHeight());
            assertInside((LinearLayout) key.getParent(), key, description + " inside column");
            assertInside(key, key.digitView, description + " digit area");
            assertInside(key, key.legendView, description + " guide area");
            assertTrue(description + " areas must not overlap",
                    key.digitView.getBottom() <= key.legendView.getTop());
            assertEquals(description + " digit contents", String.valueOf(DIGITS.charAt(index)),
                    key.digitView.getText().toString());
            assertTextFits(key.digitView, 1, description + " digit");
            int actualBaseline = key.digitView.getTop() + key.digitView.getBaseline();
            if (baseline < 0) baseline = actualBaseline;
            assertEquals(description + " consistent digit baseline", baseline, actualBaseline);
            assertEquals(description + " alphabet contents", legends[index], key.legendView.getText().toString());
            assertEquals(description + " reserve guide area", View.VISIBLE, key.legendView.getVisibility());
            assertTrue(description + " guide height", key.legendView.getHeight() > 0);
            if (!legends[index].isEmpty()) {
                assertTextFits(key.legendView, legends[index].contains("\n") ? 2 : 1, description + " alphabet");
            }
        }
    }

    private static void measureAndLayout(View view, int width, int height) {
        view.measure(width, height);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static void assertInside(LinearLayout parent, View child, String label) {
        assertTrue(label + " left", child.getLeft() >= parent.getPaddingLeft());
        assertTrue(label + " right", child.getRight() <= parent.getWidth() - parent.getPaddingRight());
        assertTrue(label + " top", child.getTop() >= parent.getPaddingTop());
        assertTrue(label + " bottom", child.getBottom() <= parent.getHeight() - parent.getPaddingBottom());
    }

    private static void assertTextFits(TextView view, int lines, String label) {
        Layout layout = view.getLayout();
        assertNotNull(label + " native layout", layout);
        assertEquals(label + " line count", lines, layout.getLineCount());
        assertEquals(label + " full text retained", view.length(), layout.getLineEnd(lines - 1));
        int width = view.getWidth() - view.getCompoundPaddingLeft() - view.getCompoundPaddingRight();
        int height = view.getHeight() - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom();
        assertTrue(label + " positive text size", view.getTextSize() > 0);
        assertTrue(label + " layout height " + layout.getHeight() + " > " + height, layout.getHeight() <= height);
        assertEquals(label + " no horizontal scroll", 0, view.getScrollX());
        assertEquals(label + " no vertical scroll", 0, view.getScrollY());
        int baselineOffset = view.getBaseline() - layout.getLineBaseline(0);
        Rect ink = new Rect();
        for (int line = 0; line < lines; line++) {
            assertEquals(label + " no ellipsis on line " + line, 0, layout.getEllipsisCount(line));
            assertTrue(label + " actual measured line width", layout.getLineWidth(line) > 0);
            assertTrue(label + " line width " + layout.getLineWidth(line) + " > " + width,
                    layout.getLineWidth(line) <= width + .5f);
            assertTrue(label + " line starts inside content", layout.getLineLeft(line) >= -.5f);
            assertTrue(label + " line ends inside content", layout.getLineRight(line) <= width + .5f);
            int start = layout.getLineStart(line), end = layout.getLineEnd(line);
            String text = view.getText().subSequence(start, end).toString().replace("\n", "");
            view.getPaint().getTextBounds(text, 0, text.length(), ink);
            int baseline = baselineOffset + layout.getLineBaseline(line);
            assertTrue(label + " glyph top clipped", baseline + ink.top >= 0);
            assertTrue(label + " glyph bottom clipped", baseline + ink.bottom <= view.getHeight());
        }
    }

    private File previewFile() {
        String name = null;
        if (screenWidthDp == 393 && fontScale == 1f && profilePercent == 100 && russian)
            name = "keypad-preview-baseline-ru.png";
        if (screenWidthDp == 320 && fontScale == 2f && profilePercent == 150)
            name = "keypad-preview-large-" + (russian ? "ru" : "en") + ".png";
        return name == null ? null : new File(System.getProperty("evdial.previewDir", "build/reports/keypad"), name);
    }

    private static void render(View view, File target) throws Exception {
        assertTrue("Preview output directory", target.getParentFile().isDirectory() || target.getParentFile().mkdirs());
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        File webp = new File(target.getParentFile(), target.getName().replace(".png", ".webp"));
        try {
            view.draw(new Canvas(bitmap));
            try (FileOutputStream stream = new FileOutputStream(target)) {
                assertTrue("Native PNG preview", bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
            try (FileOutputStream stream = new FileOutputStream(webp)) {
                assertTrue("Native WEBP preview", bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, stream));
            }
            System.out.println("KEYPAD_PREVIEW " + target.getName() + "=" + target.length()
                    + " " + webp.getName() + "=" + webp.length());
        } finally {
            bitmap.recycle();
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, context.getResources().getDisplayMetrics()));
    }
}
