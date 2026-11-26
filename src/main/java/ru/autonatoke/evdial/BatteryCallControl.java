package ru.autonatoke.evdial;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Charge right to answer, discharge left to decline or hang up. */
public final class BatteryCallControl extends LinearLayout {
    private static final int GREEN = 0xff65ed9d, RED = 0xffff625f;
    private final Runnable answer, end;
    private final Track track;
    private final Button negative, positive;
    private final TextView hint;
    private boolean ringing;
    private long nextActionMs;

    public BatteryCallControl(Context context, boolean ringing, Runnable answer, Runnable end) {
        super(context);
        this.answer = answer;
        this.end = end;
        this.ringing = ringing;
        setOrientation(VERTICAL);
        setLayoutDirection(LAYOUT_DIRECTION_LTR); // Charging always runs to the right.
        setClipToPadding(false);
        track = new Track(context);
        addView(track, LayoutParams.MATCH_PARENT, dp(88));

        LinearLayout captions = new LinearLayout(context);
        captions.setGravity(Gravity.CENTER_VERTICAL);
        captions.setBaselineAligned(false);
        addView(captions, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        negative = caption(false);
        positive = caption(true);
        hint = new TextView(context);
        hint.setTextSize(12);
        hint.setTextColor(0xffadbbb3);
        hint.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        hint.setText("Сдвиньте трубку влево");
        hint.setPadding(dp(4), 0, dp(4), 0);
        hint.setMaxLines(2);
        hint.setAutoSizeTextTypeUniformWithConfiguration(dp(10),
                Math.max(dp(11), Math.round(hint.getTextSize())), 1,
                android.util.TypedValue.COMPLEX_UNIT_PX);
        hint.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        captions.addView(negative, captionSlot());
        captions.addView(positive, captionSlot());
        captions.addView(hint, captionSlot());
        updateMode();
    }

    private int dp(float units) {
        return Math.round(units * getResources().getDisplayMetrics().density);
    }

    private LayoutParams captionSlot() {
        return new LayoutParams(0, dp(76), 1);
    }

    private Button caption(boolean accept) {
        Button button = new Button(getContext());
        button.setStateListAnimator(null);
        button.setBackgroundTintList(null);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(12));
        mask.setColor(Color.WHITE);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(accept ? 0x3365ed9d : 0x33ff625f), null, mask));
        button.setAllCaps(false);
        button.setLetterSpacing(0);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(14);
        button.setTextColor(accept ? GREEN : RED);
        button.setText(accept ? "Принять" : "Отклонить");
        button.setContentDescription(accept ? "Принять входящий звонок" : "Отклонить входящий звонок");
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setHorizontallyScrolling(false);
        button.setAutoSizeTextTypeUniformWithConfiguration(dp(11),
                Math.max(dp(12), Math.round(button.getTextSize())), 1,
                android.util.TypedValue.COMPLEX_UNIT_PX);
        // Each action owns its tint; no vendor emoji or shared mutable drawable state.
        Drawable lightning = getContext().getDrawable(R.drawable.call_lightning).mutate();
        lightning.setTint(accept ? GREEN : RED);
        lightning.setBounds(0, 0, dp(30), dp(36));
        button.setCompoundDrawables(null, lightning, null, null);
        button.setCompoundDrawablePadding(dp(3));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(76));
        button.setPadding(dp(8), dp(5), dp(8), dp(5));
        button.setOnClickListener(v -> commit(accept));
        return button;
    }

    public void setRinging(boolean ringing) {
        if (this.ringing == ringing) return;
        this.ringing = ringing;
        nextActionMs = 0;
        track.reset();
        updateMode();
    }

    private void updateMode() {
        negative.setText(ringing ? "Отклонить" : "Завершить");
        negative.setTextColor(RED);
        negative.setContentDescription(ringing ? "Отклонить входящий звонок" : "Завершить вызов");
        positive.setVisibility(ringing ? VISIBLE : GONE);
        hint.setVisibility(ringing ? GONE : VISIBLE);
        track.invalidate();
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (track == null) return;
        track.setEnabled(enabled);
        negative.setEnabled(enabled);
        positive.setEnabled(enabled);
        setAlpha(enabled ? 1f : .5f);
        if (!enabled) track.reset();
    }

    private void commit(boolean accept) {
        Runnable callback = accept ? answer : end;
        long now = SystemClock.uptimeMillis();
        if (callback == null || !isEnabled() || (accept && !ringing) || now < nextActionMs) return;
        nextActionMs = now + 900;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        callback.run();
    }

    private final class Track extends View {
        private static final float MAX_COMMIT_DP = 56f;
        private final float density = getResources().getDisplayMetrics().density;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable handset;
        private ValueAnimator rebound;
        private float travel, downX, downY;
        private final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        private int pointer = MotionEvent.INVALID_POINTER_ID;
        private boolean armed;

        Track(Context context) {
            super(context);
            handset = context.getDrawable(R.drawable.dir_callback).mutate();
            handset.setTint(0xff102019);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        // All geometry is in dp. The handle and the half charge share a neutral position.
        private float center() { return getWidth() / density * .5f; }
        private float reach() { return Math.max(1f, center() - 36f); }
        private float commitDistance() { return Math.min(MAX_COMMIT_DP, reach() * .64f); }
        private float handleX() { return center() + travel * reach(); }

        private void fill(Canvas c, float l, float t, float r, float b, float radius, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            c.drawRoundRect(l, t, r, b, radius, radius, paint);
        }

        private void line(Canvas c, float x1, float y1, float x2, float y2, int color, float stroke) {
            paint.setColor(color);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(x1, y1, x2, y2, paint);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(density, density);
            float width = getWidth() / density, bodyEnd = width - 14;
            float knob = handleX(), amount = Math.min(1f, Math.abs(travel) * reach() / commitDistance());
            float charge = (1f + Math.signum(travel) * amount) * .5f;
            int direction = travel < 0 ? RED : UiProfiles.accent(getContext());
            int rim = blend(0xff527061, direction, amount * .7f);

            // A thicker body and a distinct terminal use the existing track space.
            fill(canvas, bodyEnd - 1, 27, width - 1, 57, 3, rim);
            fill(canvas, 1, 4, bodyEnd, 80, 11, rim);
            fill(canvas, 4, 7, bodyEnd - 3, 77, 8, 0xff101b16);

            final int segments = 10;
            float start = 10, gap = 3, span = bodyEnd - 2 * start;
            float cell = (span - gap * (segments - 1)) / segments;
            int energized = blend(ringing ? 0xff315746 : 0xff346749, direction, amount * .70f);
            for (int i = 0; i < segments; i++) {
                float x = start + i * (cell + gap), fraction = Math.max(0, Math.min(1, charge * segments - i));
                fill(canvas, x, 15, x + cell, 69, 4, 0xff1d3027);
                if (fraction > 0) {
                    canvas.save();
                    canvas.clipRect(x, 15, x + cell * fraction, 69);
                    fill(canvas, x, 15, x + cell, 69, 4, energized);
                    canvas.restore();
                }
            }

            // The fixed minus/plus and direction of travel also distinguish actions without colour.
            float left = center() - reach(), right = center() + reach();
            line(canvas, left - 5, 42, left + 5, 42, RED, 2);
            if (ringing) {
                line(canvas, right - 5, 42, right + 5, 42, UiProfiles.accent(getContext()), 2);
                line(canvas, right, 37, right, 47, UiProfiles.accent(getContext()), 2);
            }

            if (Math.abs(travel) < .07f) {
                float x = center() - 34;
                line(canvas, x + 4, 38, x, 42, 0xffa0b9a8, 1.5f);
                line(canvas, x, 42, x + 4, 46, 0xffa0b9a8, 1.5f);
                if (ringing) {
                    x = center() + 34;
                    line(canvas, x - 4, 38, x, 42, 0xffb5d0bd, 1.5f);
                    line(canvas, x, 42, x - 4, 46, 0xffb5d0bd, 1.5f);
                }
            }

            fill(canvas, knob - 24, 13, knob + 24, 71, 14, 0xff0c1510);
            fill(canvas, knob - 22, 15, knob + 22, 69, 12, blend(0xffd0e7d7, direction, amount));
            canvas.save();
            canvas.translate(knob, 42);
            canvas.rotate(travel < 0 ? travel * 45f : 0);
            handset.setBounds(-11, -11, 11, 11);
            handset.draw(canvas);
            canvas.restore();
            canvas.restore();
        }

        private int blend(int from, int to, float f) {
            return Color.rgb(
                    Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * f),
                    Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * f),
                    Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f));
        }

        private void move(float x) {
            float delta = (x - downX) / density;
            travel = Math.max(-1, Math.min(ringing ? 1 : 0, delta / reach()));
            boolean ready = Math.abs(travel) * reach() >= commitDistance() && Math.abs(x - downX) > slop;
            if (ready && !armed) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            armed = ready;
            invalidate();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!isEnabled()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (Math.abs(e.getX() / density - handleX()) > 30) return false;
                    reset();
                    pointer = e.getPointerId(0);
                    downX = e.getX();
                    downY = e.getY();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    int index = e.findPointerIndex(pointer);
                    if (index < 0) return true;
                    float y = e.getY(index) / density;
                    float vertical = Math.abs(e.getY(index) - downY);
                    if (y < -10 || y > getHeight() / density + 10
                            || (vertical > slop && vertical > Math.abs(e.getX(index) - downX))) {
                        finishTouch();
                        return true;
                    }
                    move(e.getX(index));
                    if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                        boolean fire = armed, accept = travel > 0;
                        finishTouch();
                        if (fire) commit(accept);
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_CANCEL:
                    finishTouch();
                    return true;
                default:
                    return true;
            }
        }

        private void finishTouch() {
            pointer = MotionEvent.INVALID_POINTER_ID;
            armed = false;
            getParent().requestDisallowInterceptTouchEvent(false);
            if (rebound != null) rebound.cancel();
            if (travel == 0) return;
            rebound = ValueAnimator.ofFloat(travel, 0f);
            rebound.setDuration(170);
            rebound.setInterpolator(new DecelerateInterpolator());
            rebound.addUpdateListener(animation -> {
                travel = (float) animation.getAnimatedValue();
                invalidate();
            });
            rebound.start();
        }

        private void reset() {
            if (rebound != null) rebound.cancel();
            if (pointer != MotionEvent.INVALID_POINTER_ID && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            travel = 0;
            armed = false;
            pointer = MotionEvent.INVALID_POINTER_ID;
            invalidate();
        }

        @Override protected void onDetachedFromWindow() {
            reset();
            super.onDetachedFromWindow();
        }
    }
}
