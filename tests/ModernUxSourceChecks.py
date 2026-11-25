#!/usr/bin/env python3
"""Source-only regression checks for EVDial 0.3.3-rc4 UX patch.

No calls, network, signing keys, Android emulator, or publishing involved.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
java = ROOT / "src/main/java/ru/autonatoke/evdial"
dial = (java / "Dialer.java").read_text(encoding="utf-8")
contacts = (java / "ContactsScreen.java").read_text(encoding="utf-8")
manifest = (ROOT / "src/main/AndroidManifest.xml").read_text(encoding="utf-8")
gradle = (ROOT / "build.gradle").read_text(encoding="utf-8")
callcontrol = (java / "BatteryCallControl.java").read_text(encoding="utf-8")

checks = {
    "dialer_is_native": "class Dialer extends Activity" in dial,
    "package_unchanged": "ru.autonatoke.evdial.design" in gradle,
    "version_18": "versionCode 18" in gradle and "versionName '0.3.3-rc4'" in gradle,
    "original_sdk_baseline": "minSdk 26; targetSdk 33" in gradle,
    "no_new_manifest_permission": "READ_CONTACTS" in manifest and "BIND_INCALL_SERVICE" in manifest,
    "ev_shortcuts_lazy_constructed": dial.count("quickBar=ElectricUi.shortcuts(this)") == 1
       and "if(servicesExpanded&&quickBar==null)" in dial,
    "ev_focus_persisted": 'getBoolean("showEvServices",false)' in dial
       and 'putBoolean("showEvServices",servicesExpanded)' in dial,
    "ev_shortcuts_controllable": "Сервисы EV: показать или скрыть" in dial,
    "dial_compact_header": "compact?64:76" in dial,
    "sim_status_readable": "Выбрана SIM" in dial,
    "sip_status_readable": "Выбрана SIP" in dial,
    "sip_readiness_truthful": 'sipReady?"SIP подключён":"SIP не подключён"' in dial,
    "no_sip_account_change": "void route(boolean value)" in dial,
    "swipe_between_tabs_preserved": "dispatchTouchEvent(android.view.MotionEvent e)" in dial
       and "swipeStartX" in dial,
    "call_end_control_preserved": "Завершить вызов" in callcontrol,
    "call_controls_preserved": "batterySlot.addView(callBattery" in dial,
    "favorites_strip_available": "private void renderFavorites()" in contacts,
    "favorites_not_overpopulated": "if(picks.size()==8)break;" in contacts,
    "favorites_accessible_target": 'call.setMinimumHeight(dp(48))' in contacts,
    "favorites_choose_number": "call.setOnClickListener(v->chooseNumber(entry,n->pick(entry,n,true)))" in contacts,
    "favorites_card_access": "tile.setOnClickListener(v->subscriber(entry))" in contacts,
    "favorites_guest_safe": "if(picks.isEmpty())" in contacts,
    "favorites_swipe_guard": "swipeFromFavorites" in contacts and "!swipeFromFavorites&&" in contacts,
    "recents_grouping_preserved": "callGroupCounts.clear()" in contacts,
    "recent_direct_call_preserved": "if(callable)callDisplayed(e)" in contacts,
    "vcf_content_provider": "ContactsContract.Contacts.CONTENT_VCARD_URI" in contacts,
    "vcf_read_permission": "FLAG_GRANT_READ_URI_PERMISSION" in contacts,
    "vcf_android_chooser": 'Intent.createChooser(send,"Поделиться контактом (.vcf)")' in contacts,
    "vcf_chooser_grant": "chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)" in contacts,
    "vcf_stream": "Intent.EXTRA_STREAM" in contacts and "new ClipData.Item(vcard)" in contacts,
    "text_number_fallback": "shareNumber(person,selectedNumber)" in contacts,
    "no_forced_share": "shareContact(person,number)" in contacts,
}
failures = [name for name, ok in checks.items() if not ok]
for name in failures:
    print("FAIL:", name)
print(f"{len(checks) - len(failures)}/{len(checks)} checks passed.")
if failures:
    raise SystemExit(1)
print("PASS: source-level UI behavior and regressions; physical Android/SIP/VCF not proven.")
