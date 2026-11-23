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
    private static final int GREEN = 0xff9df2bd, RED = 0xffff9b87;
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
        return new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
    }

    private Button caption(boolean accept) {
        Button button = new Button(getContext());
        button.setStateListAnimator(null);
        button.setBackgroundTintList(null);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(12));
        mask.setColor(Color.WHITE);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40599470), null, mask));
        button.setAllCaps(false);
        button.setLetterSpacing(0);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(17);
        button.setTextColor(accept ? UiProfiles.accent(getContext()) : RED);
        button.setText(accept ? "☎  Ответить" : "✕  Отклонить");
        button.setGravity((accept ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(64));
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
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
        negative.setText(ringing ? "☎  Отклонить" : "☎  Завершить вызов");
        negative.setTextColor(ringing ? RED : 0xffff625f);
        negative.setContentDescription(ringing ? "Отклонить входящий звонок" : "Завершить разговор");
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
            float width = getWidth() / density, bodyEnd = width - 11;
            float knob = handleX(), amount = Math.min(1f, Math.abs(travel) * reach() / commitDistance());
            float charge = (1f + Math.signum(travel) * amount) * .5f;
            int direction = travel < 0 ? RED : UiProfiles.accent(getContext());
            int rim = blend(0xff527061, direction, amount * .7f);

            fill(canvas, bodyEnd - 1, 23, width - 1, 49, 3, rim);
            fill(canvas, 1, 4, bodyEnd, 68, 15, rim);
            fill(canvas, 3, 6, bodyEnd - 2, 66, 13, 0xff101b16);

            final int segments = 10;
            float start = 10, gap = 3, span = bodyEnd - 2 * start;
            float cell = (span - gap * (segments - 1)) / segments;
            int energized = blend(ringing ? 0xff315746 : 0xff346749, direction, amount * .70f);
            for (int i = 0; i < segments; i++) {
                float x = start + i * (cell + gap), fraction = Math.max(0, Math.min(1, charge * segments - i));
                fill(canvas, x, 13, x + cell, 59, 4, 0xff1d3027);
                if (fraction > 0) {
                    canvas.save();
                    canvas.clipRect(x, 13, x + cell * fraction, 59);
                    fill(canvas, x, 13, x + cell, 59, 4, energized);
                    canvas.restore();
                }
            }

            // The fixed minus/plus and direction of travel also distinguish actions without colour.
            float left = center() - reach(), right = center() + reach();
            line(canvas, left - 5, 36, left + 5, 36, RED, 2);
            if (ringing) {
                line(canvas, right - 5, 36, right + 5, 36, UiProfiles.accent(getContext()), 2);
                line(canvas, right, 31, right, 41, UiProfiles.accent(getContext()), 2);
            }

            if (Math.abs(travel) < .07f) {
                float x = center() - 34;
                line(canvas, x + 4, 32, x, 36, 0xffa0b9a8, 1.5f);
                line(canvas, x, 36, x + 4, 40, 0xffa0b9a8, 1.5f);
                if (ringing) {
                    x = center() + 34;
                    line(canvas, x - 4, 32, x, 36, 0xffb5d0bd, 1.5f);
                    line(canvas, x, 36, x - 4, 40, 0xffb5d0bd, 1.5f);
                }
            }

            fill(canvas, knob - 24, 9, knob + 24, 63, 14, 0xff0c1510);
            fill(canvas, knob - 22, 11, knob + 22, 61, 12, blend(0xffd0e7d7, direction, amount));
            canvas.save();
            canvas.translate(knob, 36);
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
