package ru.autonatoke.evdial;

import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A fixed-size dialpad key whose digit and alphabet guides fit their measured space. */
final class DialpadKey extends LinearLayout {
    final TextView digitView;
    final TextView legendView;

    DialpadKey(Context context, char digit, String legend, int digitSp, int legendSp,
               int textColor, int legendColor) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setBaselineAligned(false);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        setPadding(dp(4, metrics), dp(5, metrics), dp(4, metrics), dp(5, metrics));

        digitView = text(context, String.valueOf(digit), 1, textColor);
        digitView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        fit(digitView, 18, digitSp, metrics);
        addView(digitView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 3f));

        String guide = legend == null ? "" : legend;
        legendView = text(context, guide, guide.indexOf('\n') >= 0 ? 2 : 1, legendColor);
        legendView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        legendView.setLetterSpacing(.025f);
        fit(legendView, 8, legendSp, metrics);
        // Keep this area even on 1, * and # so every digit has the same baseline.
        addView(legendView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 2f));
    }

    private static TextView text(Context context, String value, int lines, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 0, 0, 0);
        view.setIncludeFontPadding(false);
        view.setSingleLine(false);
        view.setLines(lines);
        view.setHorizontallyScrolling(false);
        view.setFocusable(false);
        view.setClickable(false);
        view.setLongClickable(false);
        // The containing key announces its digit and both alphabets as one control.
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private static void fit(TextView view, int minimumDp, int preferredSp, DisplayMetrics metrics) {
        int minimumPx = dp(minimumDp, metrics);
        int preferredPx = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, preferredSp, metrics));
        // SP respects the selected font size; a DP floor still fits at large system scales.
        view.setAutoSizeTextTypeUniformWithConfiguration(minimumPx,
                Math.max(minimumPx + 1, preferredPx), 1, TypedValue.COMPLEX_UNIT_PX);
    }

    private static int dp(int value, DisplayMetrics metrics) {
        return Math.max(1, Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, metrics)));
    }
}
