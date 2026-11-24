package ru.autonatoke.evdial;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * On-device contact/SIM/SIP index. Call on a background executor, never on the UI thread.
 * All reads honor existing Android runtime permissions and never upload contact data.
 */
final class SmartDialIndex {
    private static final int MAX_CONTACT_NUMBERS = 30000;
    private static final int MAX_SIM_EVENTS = 350;
    private static final int MAX_SIP_EVENTS = 200;

    private SmartDialIndex() { }

    static List<SmartDialSearch.Entry> load(Context context) {
        List<SmartDialSearch.Entry> result = new ArrayList<>();
        Map<String, SmartDialSearch.Entry> contactsByNumber = new HashMap<>();
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            readContacts(context, result, contactsByNumber);
        }
        if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            readSimCalls(context, result, contactsByNumber);
        }
        readSipCalls(context, result, contactsByNumber);
        return result;
    }

    private static void readContacts(Context context, List<SmartDialSearch.Entry> result,
                                     Map<String, SmartDialSearch.Entry> byNumber) {
        String[] columns = {
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.STARRED,
        };
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, columns, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
            if (cursor == null) return;
            int count = 0;
            while (cursor.moveToNext() && count++ < MAX_CONTACT_NUMBERS) {
                String number = cursor.getString(1);
                if (TextUtils.isEmpty(number)) continue;
                long id = cursor.getLong(2);
                String lookup = cursor.getString(3);
                String uri = TextUtils.isEmpty(lookup) ? "" :
                        ContactsContract.Contacts.getLookupUri(id, lookup).toString();
                SmartDialSearch.Entry contact = new SmartDialSearch.Entry(
                    cursor.getString(0), number, uri, false, cursor.getInt(4) != 0, 0);
                result.add(contact);
                String key = SmartDialSearch.nationalDigits(number);
                if (!key.isEmpty()) byNumber.putIfAbsent(key, contact);
            }
        } catch (RuntimeException permissionOrProviderUnavailable) {
            // A permission may be revoked while loading or an OEM provider may fail.
        }
    }

    private static void addCall(String number, String name, long date,
                                List<SmartDialSearch.Entry> result,
                                Map<String, SmartDialSearch.Entry> contactsByNumber) {
        if (TextUtils.isEmpty(number)) return;
        SmartDialSearch.Entry contact = contactsByNumber.get(SmartDialSearch.nationalDigits(number));
        String title = contact != null ? contact.name : (TextUtils.isEmpty(name) ? number : name);
        String lookup = contact == null ? "" : contact.contactUri;
        result.add(new SmartDialSearch.Entry(title, number, lookup, true,
                contact != null && contact.starred, date));
    }

    private static void readSimCalls(Context context, List<SmartDialSearch.Entry> result,
                                     Map<String, SmartDialSearch.Entry> contactsByNumber) {
        String[] columns = {
            CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.NUMBER_PRESENTATION,
            CallLog.Calls.CACHED_NAME
        };
        try (Cursor cursor = context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI, columns, null, null, CallLog.Calls.DATE + " DESC")) {
            if (cursor == null) return;
            int count = 0;
            while (cursor.moveToNext() && count++ < MAX_SIM_EVENTS) {
                if (cursor.getInt(2) != CallLog.Calls.PRESENTATION_ALLOWED) continue;
                addCall(cursor.getString(0), cursor.getString(3), cursor.getLong(1),
                        result, contactsByNumber);
            }
        } catch (RuntimeException permissionOrProviderUnavailable) {
            // Never require default dialer role or additional permissions just for T9.
        }
    }

    private static void readSipCalls(Context context, List<SmartDialSearch.Entry> result,
                                     Map<String, SmartDialSearch.Entry> contactsByNumber) {
        try {
            JSONArray history = SipHistory.read(context);
            for (int i = 0; i < Math.min(history.length(), MAX_SIP_EVENTS); i++) {
                JSONObject call = history.optJSONObject(i);
                if (call == null) continue;
                addCall(call.optString("number", ""), "",
                        call.optLong("time", 0), result, contactsByNumber);
            }
        } catch (RuntimeException ignored) {
            // SIP history is optional and never blocks the phone keypad.
        }
    }
}
