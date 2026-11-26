package ru.autonatoke.evdial;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps controls inside the safe window when Play's target SDK enforces edge-to-edge. */
final class PlayInsets {
    private static final Map<View, Padding> installed = new WeakHashMap<>();

    private PlayInsets() {}

    static void apply(Activity activity) {
        if (!BuildConfig.GOOGLE_PLAY_BUILD || Build.VERSION.SDK_INT < 35) return;
        Window window = activity.getWindow();
        window.setDecorFitsSystemWindows(false);
        // IME insets describe the obscured area; the listener takes the maximum
        // with the navigation bar rather than adding the same area twice.
        int softInput = window.getAttributes().softInputMode;
        window.setSoftInputMode((softInput & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        int base = UiProfiles.active(activity).base;
        content.setBackgroundColor(base);
        WindowInsetsController controller = window.getInsetsController();
        if (controller != null) {
            int lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(Color.luminance(base) > .5f ? lightBars : 0, lightBars);
        }
        install(content);
    }

    /** The framework content container survives an activity replacing its page. */
    @android.annotation.TargetApi(30)
    static void install(View content) {
        if (!installed.containsKey(content)) {
            Padding original = new Padding(content);
            installed.put(content, original);
            content.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(original.left + safe.left, original.top + safe.top,
                        original.right + safe.right, original.bottom + safe.bottom);
                // Children have already been measured inside the protected area.
                return WindowInsets.CONSUMED;
            });
        }
        content.requestApplyInsets();
    }

    private static final class Padding {
        final int left, top, right, bottom;
        Padding(View view) {
            left = view.getPaddingLeft(); top = view.getPaddingTop();
            right = view.getPaddingRight(); bottom = view.getPaddingBottom();
        }
    }
}
