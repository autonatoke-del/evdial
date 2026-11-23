package ru.autonatoke.evdial;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.ContactsContract.PhoneLookup;
import android.util.LruCache;

final class Directory {
    private static final LruCache<String, String> names = new LruCache<>(256);
    private static long expires;
    static synchronized void invalidate() {
        names.evictAll();
        expires = SystemClock.elapsedRealtime() + 60000;
    }
    static String name(Context context, String number) {
        if (number == null) return "";
        if (number.isEmpty() || context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return number;
        synchronized (Directory.class) {
            if (SystemClock.elapsedRealtime() >= expires) invalidate();
            String cached = names.get(number);
            if (cached != null) return cached;
        }
        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String result = number;
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) result = cursor.getString(0);
        } catch (RuntimeException ignored) {
            return number;
        }
        names.put(number, result);
        return result;
    }
}
