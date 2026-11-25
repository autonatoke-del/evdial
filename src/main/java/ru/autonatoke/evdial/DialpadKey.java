package ru.autonatoke.evdial;

import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A fixed-size dialpad key with one centered, automatically sized digit. */
final class DialpadKey extends LinearLayout {
    final TextView digitView;

    DialpadKey(Context context, char digit, int digitSp, int textColor) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setBaselineAligned(false);
        setContentDescription(String.valueOf(digit));
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        setPadding(dp(4, metrics), dp(5, metrics), dp(4, metrics), dp(5, metrics));

        digitView = text(context, String.valueOf(digit), textColor);
        digitView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        fit(digitView, 18, digitSp, metrics);
        addView(digitView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private static TextView text(Context context, String value, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 0, 0, 0);
        view.setIncludeFontPadding(false);
        view.setSingleLine(false);
        view.setLines(1);
        view.setHorizontallyScrolling(false);
        view.setFocusable(false);
        view.setClickable(false);
        view.setLongClickable(false);
        // The containing key announces the digit once and owns all touch actions.
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
