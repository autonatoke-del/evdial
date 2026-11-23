package ru.autonatoke.evdial;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** A separately clickable contact photo; its owner supplies the click action. */
public final class ContactAvatar extends View {
    private static final int CACHE_BYTES = 4 * 1024 * 1024;
    private static final int PHOTO_EDGE = 384;
    private static final int DECODE_EDGE = PHOTO_EDGE * 2;
    private static final Object CACHE_LOCK = new Object();
    private static int cacheGeneration;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(CACHE_BYTES) {
        @Override protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    };
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final ThreadPoolExecutor PHOTOS = new ThreadPoolExecutor(
            2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(48),
            new ThreadFactory() {
                @Override public Thread newThread(final Runnable runnable) {
                    Thread thread = new Thread(new Runnable() {
                        @Override public void run() {
                            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                            runnable.run();
                        }
                    }, "contact-photos-" + THREAD_NUMBER.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            }, new ThreadPoolExecutor.AbortPolicy());
    static {
        PHOTOS.allowCoreThreadTimeOut(true);
    }

    private final ContentResolver resolver;
    private final int desiredSize;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix photoMatrix = new Matrix();
    private final Path silhouette = new Path();
    private Uri boundPhoto;
    private String initial = "";
    private int bindingGeneration;
    private Future<?> pending;
    private Bitmap photo;
    private BitmapShader photoShader;
    private LinearGradient placeholder;
    private int tint;

    public ContactAvatar(Context context, int sizeDp) {
        super(context);
        // Tasks retain the application's resolver, never the Activity or this View.
        resolver = context.getApplicationContext().getContentResolver();
        density = getResources().getDisplayMetrics().density;
        desiredSize = Math.round(Math.max(48, sizeDp) * density);
        setMinimumWidth(desiredSize);
        setMinimumHeight(desiredSize);
        setClickable(true);
        // ListView's row remains clickable; accessibility still exposes this button.
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xffe2fbf0);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        setForeground(new RippleDrawable(ColorStateList.valueOf(0x4876dcd0), null, mask));
        setContentDescription("Контакт");
    }

    /** Call on the UI thread. Rebinding always removes the previous contact's image. */
    public void bind(String displayName, Uri photoUri) {
        cancelPending();
        bindingGeneration++;
        String name = displayName == null ? "" : displayName.trim();
        initial = firstLetter(name);
        tint = (name.hashCode() & 0x7fffffff) % 3;
        placeholder = null;
        boundPhoto = photoUri;
        setPhoto(null);
        setContentDescription(name.isEmpty() ? "Контакт" : "Контакт: " + name);
        loadPhoto();
    }

    /** Clear after a contact/photo edit; the next bind reloads even an unchanged URI. */
    public static void invalidateCache() {
        synchronized (CACHE_LOCK) {
            cacheGeneration++;
            CACHE.evictAll();
        }
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(desiredSize, widthMeasureSpec),
                resolveSize(desiredSize, heightMeasureSpec));
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        placeholder = null;
        updatePhotoMatrix();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (photo == null && pending == null) loadPhoto();
    }

    @Override protected void onDetachedFromWindow() {
        bindingGeneration++;
        cancelPending();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() * .5f;
        float cy = getHeight() * .5f;
        float radius = Math.max(0, Math.min(cx, cy) - 2 * density);
        if (radius <= 0) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        if (photoShader != null) {
            paint.setShader(photoShader);
            canvas.drawCircle(cx, cy, radius, paint);
        } else {
            if (placeholder == null) {
                int start = tint == 1 ? 0xff163f3f : tint == 2 ? 0xff243c47 : 0xff1b3f31;
                int end = tint == 1 ? 0xff203649 : tint == 2 ? 0xff1b3931 : 0xff1c3349;
                placeholder = new LinearGradient(cx - radius, cy - radius,
                        cx + radius, cy + radius, start, end, Shader.TileMode.CLAMP);
            }
            paint.setShader(placeholder);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setShader(null);
            if (!initial.isEmpty()) {
                textPaint.setTextSize(radius * .98f);
                Paint.FontMetrics metrics = textPaint.getFontMetrics();
                canvas.drawText(initial, cx, cy - (metrics.ascent + metrics.descent) * .5f,
                        textPaint);
            } else {
                paint.setColor(0xffa0d6cf);
                canvas.drawCircle(cx, cy - radius * .27f, radius * .24f, paint);
                silhouette.reset();
                silhouette.moveTo(cx - radius * .48f, cy + radius * .48f);
                silhouette.cubicTo(cx - radius * .48f, cy - radius * .02f,
                        cx + radius * .48f, cy - radius * .02f,
                        cx + radius * .48f, cy + radius * .48f);
                silhouette.close();
                canvas.drawPath(silhouette, paint);
            }
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(0xff3a6b64);
        canvas.drawCircle(cx, cy, radius - density * .5f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void setPhoto(Bitmap bitmap) {
        photo = bitmap;
        photoShader = bitmap == null ? null
                : new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        updatePhotoMatrix();
        invalidate();
    }

    private void updatePhotoMatrix() {
        if (photoShader == null || photo == null) return;
        float diameter = Math.max(0, Math.min(getWidth(), getHeight()) - 4 * density);
        if (diameter <= 0) return;
        float scale = Math.max(diameter / photo.getWidth(), diameter / photo.getHeight());
        photoMatrix.setScale(scale, scale);
        photoMatrix.postTranslate((getWidth() - photo.getWidth() * scale) * .5f,
                (getHeight() - photo.getHeight() * scale) * .5f);
        photoShader.setLocalMatrix(photoMatrix);
    }

    private void loadPhoto() {
        if (boundPhoto == null || !ContentResolver.SCHEME_CONTENT.equals(boundPhoto.getScheme())) {
            return;
        }
        String key = boundPhoto.toString();
        final int cacheVersion;
        Bitmap cached;
        synchronized (CACHE_LOCK) {
            cacheVersion = cacheGeneration;
            cached = CACHE.get(key);
        }
        if (cached != null) {
            setPhoto(cached);
            return;
        }
        try {
            pending = PHOTOS.submit(new PhotoLoad(resolver, boundPhoto, key,
                    new WeakReference<ContactAvatar>(this), bindingGeneration, cacheVersion));
        } catch (RejectedExecutionException ignored) {
            // A very fast scroll can fill the bounded queue. Keep the initial and retry on bind.
            pending = null;
        }
    }

    private void cancelPending() {
        if (pending == null) return;
        pending.cancel(true);
        if (pending instanceof Runnable) PHOTOS.remove((Runnable) pending);
        pending = null;
    }

    private static String firstLetter(String name) {
        for (int offset = 0; offset < name.length();) {
            int codePoint = name.codePointAt(offset);
            if (Character.isLetter(codePoint)) {
                return new String(Character.toChars(Character.toUpperCase(codePoint)));
            }
            offset += Character.charCount(codePoint);
        }
        return "";
    }

    private static final class PhotoLoad implements Runnable {
        private final ContentResolver resolver;
        private final Uri uri;
        private final String key;
        private final WeakReference<ContactAvatar> target;
        private final int bindingVersion;
        private final int cacheVersion;

        PhotoLoad(ContentResolver resolver, Uri uri, String key,
                WeakReference<ContactAvatar> target, int bindingVersion, int cacheVersion) {
            this.resolver = resolver;
            this.uri = uri;
            this.key = key;
            this.target = target;
            this.bindingVersion = bindingVersion;
            this.cacheVersion = cacheVersion;
        }

        @Override public void run() {
            if (Thread.currentThread().isInterrupted()) return;
            Bitmap bitmap;
            synchronized (CACHE_LOCK) {
                bitmap = cacheVersion == cacheGeneration ? CACHE.get(key) : null;
            }
            if (bitmap == null) bitmap = decodePhoto(resolver, uri);
            if (Thread.currentThread().isInterrupted()) return;
            synchronized (CACHE_LOCK) {
                if (cacheVersion != cacheGeneration) return;
                if (bitmap != null) CACHE.put(key, bitmap);
            }
            MAIN.post(new PhotoResult(target, bindingVersion, cacheVersion, bitmap));
        }
    }

    private static final class PhotoResult implements Runnable {
        private final WeakReference<ContactAvatar> target;
        private final int bindingVersion;
        private final int cacheVersion;
        private final Bitmap bitmap;

        PhotoResult(WeakReference<ContactAvatar> target, int bindingVersion,
                int cacheVersion, Bitmap bitmap) {
            this.target = target;
            this.bindingVersion = bindingVersion;
            this.cacheVersion = cacheVersion;
            this.bitmap = bitmap;
        }

        @Override public void run() {
            ContactAvatar avatar = target.get();
            if (avatar == null || avatar.bindingGeneration != bindingVersion) return;
            avatar.pending = null;
            synchronized (CACHE_LOCK) {
                if (cacheVersion != cacheGeneration) return;
            }
            avatar.setPhoto(bitmap);
        }
    }

    private static Bitmap decodePhoto(ContentResolver resolver, Uri uri) {
        Bitmap decoded = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = resolver.openInputStream(uri)) {
                if (input == null) return null;
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                    || Thread.currentThread().isInterrupted()) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / options.inSampleSize > DECODE_EDGE
                    || bounds.outHeight / options.inSampleSize > DECODE_EDGE) {
                options.inSampleSize *= 2;
            }
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inScaled = false;
            try (InputStream input = resolver.openInputStream(uri)) {
                if (input == null) return null;
                decoded = BitmapFactory.decodeStream(input, null, options);
            }
            if (decoded == null) return null;
            if (Thread.currentThread().isInterrupted()) {
                decoded.recycle();
                return null;
            }
            int longest = Math.max(decoded.getWidth(), decoded.getHeight());
            if (longest <= PHOTO_EDGE) return decoded;
            float scale = PHOTO_EDGE / (float) longest;
            Bitmap scaled = Bitmap.createScaledBitmap(decoded,
                    Math.max(1, Math.round(decoded.getWidth() * scale)),
                    Math.max(1, Math.round(decoded.getHeight() * scale)), true);
            if (scaled != decoded) decoded.recycle();
            return scaled;
        } catch (IOException | RuntimeException | OutOfMemoryError unavailable) {
            // Deleted contacts, revoked permission and malformed photos all use the initial.
            if (decoded != null && !decoded.isRecycled()) decoded.recycle();
            return null;
        }
    }
}
