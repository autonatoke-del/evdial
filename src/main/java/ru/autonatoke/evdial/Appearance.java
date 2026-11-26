package ru.autonatoke.evdial;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A dialer preference that is also available inside the integrated theme settings. */
public final class Appearance {
    private static final int BACKGROUND_EDGE = 1536;
    private static final int PREVIEW_EDGE = 768;
    private static final int CACHE_BYTES = 12 * 1024 * 1024;
    private static final int BASE_COLOR = 0xff0c151a;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<Long, Bitmap> CACHE = new LruCache<Long, Bitmap>(CACHE_BYTES) {
        @Override protected int sizeOf(Long key, Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    };
    private static final ThreadPoolExecutor IMAGES = new ThreadPoolExecutor(
            1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(16),
            new ThreadFactory() {
                @Override public Thread newThread(final Runnable runnable) {
                    Thread worker = new Thread(new Runnable() {
                        @Override public void run() {
                            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                            runnable.run();
                        }
                    }, "phone-backgrounds");
                    worker.setDaemon(true);
                    return worker;
                }
            }, new ThreadPoolExecutor.AbortPolicy());
    static {
        IMAGES.allowCoreThreadTimeOut(true);
    }

    private Appearance() {}

    public static int backgroundMode(Activity activity) {
        return normalizeMode(DriveTools.prefs(activity).getInt("contrast_"+UiProfiles.id(activity),
                UiProfiles.EV.equals(UiProfiles.id(activity))?DriveTools.prefs(activity).getInt("contrast",2):2));
    }

    public static void backgroundMode(Activity activity, int mode) {
        DriveTools.prefs(activity).edit().putInt("contrast_"+UiProfiles.id(activity), normalizeMode(mode)).apply();
    }

    /** The live phone and the sample in Settings share the same crop, colour and opacity. */
    public static void applyBackground(Activity activity, View target) {
        if (activity == null || target == null || activity.isDestroyed()) return;
        if (WallpaperStore.enabled(activity)) { WallpaperStore.apply(activity, target, backgroundMode(activity)); return; }
        WallpaperStore.cancel(target);
        apply(activity, target, PhoneThemes.selectedResource(activity), backgroundMode(activity),
                BACKGROUND_EDGE, false);
    }

    /** Preview an explicit theme without changing the user's selected theme or contrast. */
    public static void applyPreview(Activity activity, View target, int resource, int mode) {
        if (activity == null || target == null || activity.isDestroyed()) return;
        // The gallery may contain all five images at once. Smaller decodes keep it lightweight.
        apply(activity, target, resource, mode, PREVIEW_EDGE, true);
    }

    /** Refresh the existing Activity surface after changing its appearance preferences. */
    public static void refresh(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        View target = activity.findViewById(android.R.id.custom);
        if (target == null) {
            View content = activity.findViewById(android.R.id.content);
            if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
                target = ((ViewGroup) content).getChildAt(0);
            } else {
                target = content;
            }
        }
        if (target != null) applyBackground(activity, target);
    }

    private static void apply(Activity activity, View target, int resource, int mode,
            int maximumEdge, boolean preloadWhenPlain) {
        PhoneBackground background;
        if (target.getBackground() instanceof PhoneBackground) {
            background = (PhoneBackground) target.getBackground();
        } else {
            // Background jobs only receive application Resources and a weak Drawable reference.
            background = new PhoneBackground(activity.getApplicationContext().getResources());
            target.setBackground(background);
        }
        background.update(resource == 0 ? R.drawable.flux : resource, normalizeMode(mode),
                maximumEdge, preloadWhenPlain, UiProfiles.active(activity).base);
    }

    private static int normalizeMode(int mode) {
        return Math.max(0, Math.min(2, mode));
    }

    private static long cacheKey(int resource, int maximumEdge) {
        return ((long) resource << 32) | (maximumEdge & 0xffffffffL);
    }

    private static final class PhoneBackground extends Drawable {
        private final Resources resources;
        private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint surfacePaint = new Paint();
        private final RectF destination = new RectF();
        private Bitmap texture;
        private int mode;
        private int skinBase=BASE_COLOR;
        private int resource;
        private int maximumEdge;
        private int generation;
        private int alpha = 255;
        private boolean preloadWhenPlain;
        private Future<?> pending;

        PhoneBackground(Resources resources) {
            this.resources = resources;
        }

        void update(int selectedResource, int selectedMode, int selectedEdge, boolean preload, int newSkinBase) {
            skinBase = newSkinBase;
            if (resource != selectedResource || maximumEdge != selectedEdge) {
                cancelLoad();
                texture = null;
                resource = selectedResource;
                maximumEdge = selectedEdge;
            }
            mode = selectedMode;
            preloadWhenPlain = preload;
            if (mode == 0 && !preloadWhenPlain) {
                // Keep already decoded images cached when the user chooses a plain surface.
                cancelLoad();
            } else {
                load();
            }
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty() || alpha == 0) return;
            int saved = alpha == 255 ? canvas.save()
                    : canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, alpha);
            canvas.clipRect(bounds);
            surfacePaint.setColor(skinBase);
            canvas.drawRect(bounds, surfacePaint);
            if (mode != 0 && texture != null) {
                float scale = Math.max(bounds.width() / (float) texture.getWidth(),
                        bounds.height() / (float) texture.getHeight());
                float halfWidth = texture.getWidth() * scale * .5f;
                float halfHeight = texture.getHeight() * scale * .5f;
                destination.set(bounds.exactCenterX() - halfWidth, bounds.exactCenterY() - halfHeight,
                        bounds.exactCenterX() + halfWidth, bounds.exactCenterY() + halfHeight);
                imagePaint.setColor(Color.WHITE);
                imagePaint.setAlpha(255);
                canvas.drawBitmap(texture, null, destination, imagePaint);
                // Both picture modes keep a stable dark blue/green veil under the controls.
                // The art itself has a dark centre; vivid mode leaves the car clearly visible.
                surfacePaint.setColor(mode == 2 ? 0x28061018 : 0x88061018);
                canvas.drawRect(bounds, surfacePaint);
            }
            canvas.restoreToCount(saved);
        }

        private void load() {
            if (texture != null || pending != null || resource == 0 || !isVisible()
                    || (mode == 0 && !preloadWhenPlain)) return;
            Bitmap cached = CACHE.get(cacheKey(resource, maximumEdge));
            if (cached != null) {
                texture = cached;
                invalidateSelf();
                return;
            }
            int request = ++generation;
            try {
                pending = IMAGES.submit(new ImageLoad(resources, resource, maximumEdge, request,
                        new WeakReference<PhoneBackground>(this)));
            } catch (RejectedExecutionException busy) {
                // Never decode on the UI thread if rapidly changing previews fills the queue.
                pending = null;
            }
        }

        private void cancelLoad() {
            generation++;
            if (pending == null) return;
            pending.cancel(true);
            if (pending instanceof Runnable) IMAGES.remove((Runnable) pending);
            pending = null;
        }

        @Override public boolean setVisible(boolean visible, boolean restart) {
            boolean changed = super.setVisible(visible, restart);
            if (visible) load();
            else cancelLoad();
            return changed;
        }

        @Override public void setAlpha(int value) {
            alpha = Math.max(0, Math.min(255, value));
            invalidateSelf();
        }

        @Override public int getAlpha() {
            return alpha;
        }

        @Override public void setColorFilter(ColorFilter filter) {
            imagePaint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override public int getOpacity() {
            return alpha == 255 ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT;
        }
    }

    private static final class ImageLoad implements Runnable {
        private final Resources resources;
        private final int resource, maximumEdge, generation;
        private final WeakReference<PhoneBackground> target;

        ImageLoad(Resources resources, int resource, int maximumEdge, int generation,
                WeakReference<PhoneBackground> target) {
            this.resources = resources;
            this.resource = resource;
            this.maximumEdge = maximumEdge;
            this.generation = generation;
            this.target = target;
        }

        @Override public void run() {
            if (Thread.currentThread().isInterrupted()) return;
            long key = cacheKey(resource, maximumEdge);
            Bitmap bitmap = CACHE.get(key);
            if (bitmap == null) bitmap = decode(resources, resource, maximumEdge);
            if (Thread.currentThread().isInterrupted()) return;
            if (bitmap != null) CACHE.put(key, bitmap);
            MAIN.post(new ImageResult(target, resource, maximumEdge, generation, bitmap));
        }
    }

    private static final class ImageResult implements Runnable {
        private final WeakReference<PhoneBackground> target;
        private final int resource, maximumEdge, generation;
        private final Bitmap bitmap;

        ImageResult(WeakReference<PhoneBackground> target, int resource, int maximumEdge,
                int generation, Bitmap bitmap) {
            this.target = target;
            this.resource = resource;
            this.maximumEdge = maximumEdge;
            this.generation = generation;
            this.bitmap = bitmap;
        }

        @Override public void run() {
            PhoneBackground background = target.get();
            if (background == null || background.generation != generation
                    || background.resource != resource || background.maximumEdge != maximumEdge) return;
            background.pending = null;
            background.texture = bitmap;
            background.invalidateSelf();
        }
    }

    private static Bitmap decode(Resources resources, int resource, int maximumEdge) {
        Bitmap decoded = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            bounds.inScaled = false;
            BitmapFactory.decodeResource(resources, resource, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                    || Thread.currentThread().isInterrupted()) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inScaled = false;
            options.inSampleSize = 1;
            while (bounds.outWidth / options.inSampleSize > maximumEdge
                    || bounds.outHeight / options.inSampleSize > maximumEdge) {
                options.inSampleSize *= 2;
            }
            decoded = BitmapFactory.decodeResource(resources, resource, options);
            if (decoded == null) return null;
            if (Thread.currentThread().isInterrupted()) {
                decoded.recycle();
                return null;
            }
            int longest = Math.max(decoded.getWidth(), decoded.getHeight());
            if (longest <= maximumEdge) return decoded;
            float scale = maximumEdge / (float) longest;
            Bitmap reduced = Bitmap.createScaledBitmap(decoded,
                    Math.max(1, Math.round(decoded.getWidth() * scale)),
                    Math.max(1, Math.round(decoded.getHeight() * scale)), true);
            if (reduced != decoded) decoded.recycle();
            return reduced;
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            if (decoded != null && !decoded.isRecycled()) decoded.recycle();
            return null;
        }
    }

    public static void brightness(Activity activity) {
        new StationSheet.Builder(activity)
                .setTitle("Яркость фона звонилки")
                .setSingleChoiceItems(new String[]{"Однотонный", "Приглушённый", "Выразительный"},
                        backgroundMode(activity), (dialog, choice) -> {
                            backgroundMode(activity, choice);
                            refresh(activity);
                        })
                .setNegativeButton("Закрыть", null)
                .show();
    }
}
