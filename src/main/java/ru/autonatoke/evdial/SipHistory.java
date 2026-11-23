package ru.autonatoke.evdial;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.CallLog.Calls;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;

final class SipHistory {
    private static final int LIMIT = 200;

    static JSONArray read(Context context) {
        String data = context.getSharedPreferences("sip_history", Context.MODE_PRIVATE)
                .getString("calls", "[]");
        try {
            return new JSONArray(data);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void record(Context context, CallLog log) {
        Address remote = log.getRemoteAddress();
        if (remote == null || remote.getUsername() == null) return;
        long time = log.getStartDate() * 1000L;
        String number = remote.getUsername();
        String id = log.getCallId();
        if (id == null || id.isEmpty()) id = number + "/" + time + "/" + log.getDir();
        int type = Calls.OUTGOING_TYPE;
        if (log.getDir() == Call.Dir.Incoming) {
            type = log.getStatus() == Call.Status.Success ? Calls.INCOMING_TYPE : Calls.MISSED_TYPE;
        }
        JSONArray previous = read(context);
        JSONArray calls = new JSONArray();
        try {
            calls.put(new JSONObject().put("id",id).put("number",number).put("time",time).put("type",type).put("duration",log.getDuration()));
            for (int i = 0; i < previous.length() && calls.length() < LIMIT; i++) {
                JSONObject item = previous.optJSONObject(i);
                if (item != null && !id.equals(item.optString("id"))) calls.put(item);
            }
            context.getSharedPreferences("sip_history", Context.MODE_PRIVATE)
                    .edit().putString("calls", calls.toString()).apply();
        } catch (JSONException e) {
            android.util.Log.w("SipHistory","Invalid call log entry");
        }
    }
}
