package ru.autonatoke.evdial;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.widget.Toast;

final class PhoneRole {
    private PhoneRole() {}

    static boolean held(Context context) {
     TelecomManager t=context.getSystemService(TelecomManager.class);
     boolean selected=t!=null&&context.getPackageName().equals(t.getDefaultDialerPackage());
     if(Build.VERSION.SDK_INT<29)return selected;
     RoleManager r=context.getSystemService(RoleManager.class);
     return selected||(r!=null&&r.isRoleAvailable(RoleManager.ROLE_DIALER)&&r.isRoleHeld(RoleManager.ROLE_DIALER));
    }

    static boolean canReadCallLog(Context context) {
        return held(context) && context.checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED;
    }

    static BroadcastReceiver watch(Context context, Runnable changed) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (TelecomManager.ACTION_DEFAULT_DIALER_CHANGED.equals(intent.getAction())) changed.run();
            }
        };
        // This protected system broadcast also reaches a resumed activity in multi-window mode.
        context.registerReceiver(receiver, new IntentFilter(TelecomManager.ACTION_DEFAULT_DIALER_CHANGED));
        return receiver;
    }
    static void request(Activity activity, int requestCode, int settingsCode) {
        try {
            Intent intent = null;
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager roles = activity.getSystemService(RoleManager.class);
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_DIALER))
                    intent = roles.createRequestRoleIntent(RoleManager.ROLE_DIALER);
            } else {
                intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                        .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.getPackageName());
            }
            if (intent != null) {
                activity.startActivityForResult(intent, requestCode);
                return;
            }
        } catch (ActivityNotFoundException | SecurityException ignored) {}
        offerSettings(activity, settingsCode);
    }

    static void offerSettings(Activity activity, int settingsCode) {
        new StationSheet.Builder(activity).setTitle("Телефон по умолчанию")
                .setMessage("Для звонков по SIM выберите EVDial в разделе «Приложение телефона».")
                .setPositiveButton("Открыть настройки Android", (dialog, index) -> {
                    try {
                        activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), settingsCode);
                    } catch (ActivityNotFoundException | SecurityException unavailable) {
                        Toast.makeText(activity, "Настройки Android → Приложения по умолчанию → Телефон", Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("Позже", null).show();
    }
}
