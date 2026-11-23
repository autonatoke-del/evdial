package ru.autonatoke.evdial;

import android.content.Intent;

/** Private lock-screen task bound to the incoming SIP call that opened it. */
public final class SipCallScreen extends Dialer {
    @Override void refresh() {
        if (!phone.ringNotice.matches(phone.active, getIntent())) {
            finishAndRemoveTask();
            return;
        }
        super.refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        Intent request = getIntent();
        if (!isFinishing() && CallNotice.ANSWER.equals(request.getAction())) {
            setIntent(new Intent(request).setAction(null));
            if (phone.ringNotice.matches(phone.active, request)) answerSip(phone.active);
        }
    }
}
