package ru.autonatoke.evdial;

import android.app.Activity;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Entry to driver assistance on the dialer and the optional home screen. */
public final class DriverBadge {
    static void add(Activity activity, LinearLayout header) {
        TextView brand = new TextView(activity);
        String title="EVDial";
        android.text.SpannableString logo=new android.text.SpannableString(title);
        logo.setSpan(new android.text.style.ForegroundColorSpan(UiProfiles.accent(activity)),0,Math.min(UiProfiles.EV.equals(UiProfiles.id(activity))?2: title.length(),title.length()),33);
        brand.setText(logo);
        brand.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        brand.setTextColor(UiProfiles.text(activity));
        brand.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        brand.setLetterSpacing(.045f);
        brand.setMaxLines(1);
        int maxPx = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 28,
                activity.getResources().getDisplayMetrics()));
        brand.setAutoSizeTextTypeUniformWithConfiguration(Station.dp(activity, 10), maxPx,
                Station.dp(activity, 1), TypedValue.COMPLEX_UNIT_PX);
        brand.setPadding(0, 0, Station.dp(activity, 5), 0);
        int size = Station.dp(activity, 48);
        header.addView(brand, new LinearLayout.LayoutParams(0, size, 1));
        
    }

    public static void addButton(Activity activity, LinearLayout header, Runnable open) {
        Button key = new Button(activity);
        key.setId(R.id.driver_help_badge);
        key.setStateListAnimator(null);
        key.setBackgroundTintList(null);
        key.setBackgroundResource(R.drawable.driver_console_key);
        key.setAllCaps(false);
        key.setText("EV");
        key.setTextSize(14);
        key.setTextColor(0xffd8f7e3);
        key.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        key.setLetterSpacing(0);
        key.setSingleLine();
        key.setIncludeFontPadding(false);
        key.setGravity(Gravity.CENTER);
        key.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.driver_car, 0, 0, 0);
        key.setCompoundDrawablePadding(Station.dp(activity, 6));
        int inset = Station.dp(activity, 11);
        key.setPaddingRelative(inset, 0, inset, 0);
        key.setMinWidth(0);
        key.setMinimumWidth(0);
        key.setMinHeight(Station.dp(activity, 48));
        key.setContentDescription("Авто. Помощь водителю");
        key.setTooltipText("Помощь водителю");
        key.setOnClickListener(v -> open.run());
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slot.setMarginEnd(Station.dp(activity, 5));
        header.addView(key, slot);
    }

    private DriverBadge() {}
}
