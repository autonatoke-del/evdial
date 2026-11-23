package ru.autonatoke.evdial;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

/** One preference shared by the dialer, contacts, call screens and appearance screens. */
final class ScreenOrientation implements Application.ActivityLifecycleCallbacks {
    private static final String KEY = "portrait_lock";

    static void register(Application app) {
        app.registerActivityLifecycleCallbacks(new ScreenOrientation());
    }

    static boolean locked(Context context) {
        return context.getSharedPreferences("drive", Context.MODE_PRIVATE).getBoolean(KEY, true);
    }

    static void apply(Activity activity) {
        int requested = locked(activity) ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if (activity.getRequestedOrientation() != requested) activity.setRequestedOrientation(requested);
    }

    static void choose(Activity activity) {
        new StationSheet.Builder(activity).setTitle("Поворот экрана")
                .setSingleChoiceItems(new String[]{"Всегда вертикально", "Как в настройках телефона"},
                        locked(activity) ? 0 : 1, (dialog, choice) -> {
                            activity.getSharedPreferences("drive", Context.MODE_PRIVATE).edit()
                                    .putBoolean(KEY, choice == 0).apply();
                            dialog.dismiss();
                            apply(activity);
                        }).setNegativeButton("Отмена", null).show();
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedState) { apply(activity); }
    @Override public void onActivityResumed(Activity activity) { apply(activity); }
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
