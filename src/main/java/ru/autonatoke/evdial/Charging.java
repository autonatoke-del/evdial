package ru.autonatoke.evdial;

import android.app.Activity;
import android.content.Intent;

public final class Charging {
    private Charging() {}
    public static void open(Activity activity) {
        activity.startActivity(new Intent(activity, ChargingActivity.class));
    }
}
