#!/usr/bin/env python3
"""Offline review of EVDial 0.3.2 compact settings and contact card.
Complements Java compilation and manual on-device screen-size checks.
"""
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
src=ROOT/"src/main/java/ru/autonatoke/evdial"
dial=(src/"Dialer.java").read_text(encoding="utf-8")
contacts=(src/"ContactsScreen.java").read_text(encoding="utf-8")
gradle=(ROOT/"build.gradle").read_text(encoding="utf-8")

def method(s,name):
    marker=name+"("
    i=s.find(marker)
    assert i>=0, name
    start=s.find("{",i)
    depth=1
    j=start+1
    while depth and j<len(s):
        depth+=(s[j]=="{")-(s[j]=="}")
        j+=1
    assert depth==0
    return s[start:j]

cfg=method(dial,"void configure")
card=method(contacts,"private void renderSubscriber")
short=method(contacts,"private TextView contactQuickAction")
numbers=method(contacts,"private static boolean hasSameContactNumber")
history=method(contacts,"private String briefCallDescription")
checks={
 "version_14":"versionCode 14; versionName '0.3.2-rc1'" in gradle,
 "settings_are_grouped":'titles={"ОФОРМЛЕНИЕ","ЗВОНКИ И ЛИНИИ","ПОВЕДЕНИЕ","ПРИЛОЖЕНИЕ"}' in cfg,
 "settings_touch_rows":"row.setMinimumHeight(dp(50))" in cfg,
 "settings_no_large_buttons":"item.setMinHeight(dp(60))" not in cfg,
 "settings_update_action":"AppUpdates.open(this)" in cfg,
 "settings_sim_action":"defaultPhone()" in cfg,
 "settings_sip_action":"configureSip()" in cfg,
 "settings_call_action":"showCallDisplay(false)" in cfg,
 "settings_theme_action":"PhoneAppearance.show(this)" in cfg,
 "settings_keyboard_preferences":"chooseStart()" in cfg and "ScreenOrientation.choose(this)" in cfg,
 "card_has_three_actions":all(x in card for x in ['contactQuickAction("Звонок"','contactQuickAction("SMS"','contactQuickAction("Отправить"']),
 "card_share_label_is_not_clipped":'new LinearLayout.LayoutParams(-1,dp(52))' not in card,
 "card_avatar_72":'new ContactAvatar(this,72)' in card,
 "card_short_name_23":'text(person.title,23,WHITE)' in card,
 "card_no_photo_hint":"Нажмите на фото, чтобы изменить" not in card,
 "card_short_actions":"action.setMinHeight(dp(70))" in short,
 "card_touches_safe":"action.setFocusable(true)" in short,
 "card_share_accessibility":'Поделиться карточкой контакта .vcf' in card,
 "number_normalizes":"PhoneNumberUtils.normalizeNumber(candidate)" in numbers,
 "number_dedup_list":"hasSameContactNumber(e.numbers,number)" in contacts,
 "number_dedup_detail":"hasSameContactNumber(person.numbers,n)" in contacts,
 "history_rows_limited":"Math.min(2,events.size())" in card,
 "history_no_repeated_phone":"e.number()" not in history,
 "history_still_shows_time":"DateFormat.getTimeFormat(this)" in history,
 "no_share_logic_regression":'Intent.createChooser(send,"Поделиться контактом (.vcf)")' in contacts,
 "shares_vcf_provider":"ContactsContract.Contacts.CONTENT_VCARD_URI" in contacts,
 "vcard_read_permission":"chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)" in contacts,
 "retains_existing_swipes":"swipeFromFavorites" in contacts and "swipeStartX" in dial,
 "retains_sim_sip":"sipReady=phone.ready()" in dial and 'void route(boolean value)' in dial,
 "no_call_engine_refactor":"MobileCalls.live" in dial and "batterySlot.addView(callBattery" in dial,
}
failed=[name for name,passed in checks.items() if not passed]
for name in failed: print("FAIL",name)
print(f"{len(checks)-len(failed)}/{len(checks)} compact UI assertions passed")
if failed: raise SystemExit(1)
