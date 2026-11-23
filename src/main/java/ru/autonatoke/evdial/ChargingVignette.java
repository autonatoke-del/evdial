package ru.autonatoke.evdial;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A decorative strip above the footer, in its own layout space on both phone screens. */
final class ChargingVignette extends View {
    private static final float WIDTH = 576f, HEIGHT = 165f;
    private static Bitmap scene;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF imageBounds = new RectF(0, 0, WIDTH, HEIGHT);
    private final RadialGradient spill = new RadialGradient(0, 0, 1,
            new int[]{0xff8effae, 0xb66deba2, 0x006deba2},
            new float[]{0, .42f, 1}, Shader.TileMode.CLAMP);
    private final PathMeasure cable;
    private final float[] point = new float[2];
    private final ValueAnimator flow;
    private boolean presented;
    private float phase;

    ChargingVignette(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (scene == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            scene = BitmapFactory.decodeResource(getResources(), R.drawable.charging_scene, options);
        }
        // Geometric landmarks refer to the original image, not the screen bounds.
        Path lead = new Path();
        lead.moveTo(530f, 69f);
        lead.cubicTo(524f, 96f, 512f, 119f, 494f, 125f);
        lead.cubicTo(452f, 141f, 435f, 92f, 421f, 77f);
        lead.quadTo(418f, 73f, 413f, 73f);
        cable = new PathMeasure(lead, false);
        flow = ValueAnimator.ofFloat(0f, 1f);
        flow.setDuration(2000);
        flow.setRepeatCount(1);
        flow.setInterpolator(new LinearInterpolator());
        flow.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int viewport = getResources().getConfiguration().screenHeightDp;
        int heightDp = viewport < 480 ? 30 : viewport < 650 ? 43 : 56;
        setMeasuredDimension(MeasureSpec.getSize(widthSpec),
                resolveSize(dp(heightDp), heightSpec));
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (flow == null) return;
        if (visible && !presented) {
            presented = true;
            if (ValueAnimator.areAnimatorsEnabled()) flow.start();
        } else if (!visible) {
            presented = false;
            flow.cancel();
            phase = 0;
        }
    }

    @Override protected void onDetachedFromWindow() {
        flow.cancel();
        presented = false;
        phase = 0;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min((getHeight() - dp(3)) / HEIGHT,
                (getWidth() * .64f) / WIDTH);
        if (scene == null || scale <= 0) return;
        float right = getWidth() - dp(15);
        float bottom = getHeight() - dp(1);
        int save = canvas.save();
        canvas.translate(right - scale * WIDTH, bottom - scale * HEIGHT);
        canvas.scale(scale, scale);
        paint.setColor(0x283c947c);
        canvas.drawOval(10, 147, 570, 164, paint);
        float energy = flow.isRunning() ? (float) Math.pow(Math.sin(Math.PI * phase), 2) : 0f;
        // A soft pool of light stays visible at the strip's physical size of 43–56 dp.
        int lightLayer = canvas.save();
        canvas.translate(252, 148);
        canvas.scale(223, 19);
        paint.setColor(0xffffffff);
        paint.setShader(spill);
        paint.setAlpha(Math.round(58 + 185 * energy));
        canvas.drawCircle(0, 0, 1, paint);
        paint.setShader(null);
        canvas.restoreToCount(lightLayer);
        paint.setColor(0xffffffff);
        canvas.drawBitmap(scene, null, imageBounds, paint);
        int portLight = canvas.save();
        canvas.translate(413, 73);
        canvas.scale(20, 15);
        paint.setShader(spill);
        paint.setAlpha(Math.round(36 + 168 * energy));
        canvas.drawCircle(0, 0, 1, paint);
        paint.setShader(null);
        canvas.restoreToCount(portLight);
        paint.setColor(0xff9ef5c0);
        canvas.drawCircle(413, 73, 2.2f, paint);
        if (flow.isRunning()) {
            float fade = Math.min(1f, Math.min(phase, 1f - phase) * 10f);
            cable.getPosTan(phase * cable.getLength(), point, null);
            paint.setColor(0x6685f3ad);
            paint.setAlpha((int) (80 * fade));
            canvas.drawCircle(point[0], point[1], 7, paint);
            paint.setColor(0xffb6ffcd);
            paint.setAlpha((int) (255 * fade));
            canvas.drawCircle(point[0], point[1], 3.2f, paint);
        }
        canvas.restoreToCount(save);
    }
}
