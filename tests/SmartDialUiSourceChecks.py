#!/usr/bin/env python3
"""Source invariants for iPhone-style keypad suggestions in EVDial 0.3.3.
No Android permissions, UI automation, or real calls are used here.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/"src/main/java/ru/autonatoke/evdial"
dial = (JAVA/"Dialer.java").read_text(encoding="utf-8")
matcher = (JAVA/"SmartDialSearch.java").read_text(encoding="utf-8")
index = (JAVA/"SmartDialIndex.java").read_text(encoding="utf-8")
manifest = (ROOT/"src/main/AndroidManifest.xml").read_text(encoding="utf-8")
build = (ROOT/"build.gradle").read_text(encoding="utf-8")

checks = {
    "version_17": "versionCode 17; versionName '0.3.3-rc3'" in build,
    "suggestions_below_number": "idleCard.addView(status,box(-1,-2));\n  buildSmartSuggestions();" in dial,
    "suggestions_before_sim_route": "buildSmartSuggestions();\n  routes=row();" in dial,
    "typing_triggers_search": "scheduleSmartSearch();" in dial and "addTextChangedListener" in dial,
    "source_on_device": "SmartDialIndex.load(getApplicationContext())" in dial,
    "background_executor": "Executors.newSingleThreadExecutor()" in dial,
    "search_debounced": "smartUi.postDelayed(smartPending,95);" in dial,
    "stale_results_rejected": "ticket!=smartQueryGeneration" in dial,
    "stale_suggestion_not_clickable": "smartPanel.setVisibility(View.GONE);\n  smartMatches=null;" in dial,
    "suggestions_follow_active_call": "if(phone.active!=null){smartPanel.setVisibility(View.GONE);return;}" in dial,
    "one_best_match": "results.items.get(0).entry" in dial,
    "more_matches": 'smartMore.setText("Ещё "+smartCountLabel(results.total-1))' in dial,
    "latest_call": 'smartTitle.setText(results.isRecent?"Последний · "+first.name:first.name)' in dial,
    "open_all_recent_calls": 'smartMore.setText("Все недавние вызовы")' in dial,
    "keeps_keypad_visible": "keypad.setVisibility(!busy||(!incoming&&sipKeysOpen)" in dial,
    "contact_card": "ContactsScreen.EXTRA_CONTACT_LOOKUP" in dial,
    "call_by_tap": "smartPrimary.setOnClickListener(v->applySmartSelection(first,true))" in dial,
    "accessibility": "smartPrimary.setContentDescription" in dial and "smartInfo.setContentDescription" in dial,
    "more_scrollable": "ListView list=new ListView(this)" in dial,
    "more_selects_call": "applySmartSelection(entry,true)" in dial,
    "uses_existing_sim_sip": "if(call)startOutgoing();" in dial,
    "permissions_not_added": "READ_CONTACTS" in manifest and "READ_CALL_LOG" in manifest
        and "READ_SMS" not in manifest,
    "local_only": "getContentResolver().query(" in index and "HttpURLConnection" not in index,
    "permission_respecting": "checkSelfPermission(Manifest.permission.READ_CONTACTS)" in index
        and "checkSelfPermission(Manifest.permission.READ_CALL_LOG)" in index,
    "sip_history_read": "SipHistory.read(context)" in index,
    "do_not_show_hidden_caller": "CallLog.Calls.PRESENTATION_ALLOWED" in index,
    "cached_contact_name": "contactsByNumber.get(" in index,
    "international_prefix": '"7" + d.substring(1)' in matcher,
    "russian_t9": '"ABCАБВГ"' in matcher and '"WXYZЬЭЮЯ"' in matcher,
    "round_keypad_buttons": "round.setShape(GradientDrawable.OVAL)" in dial,
    "cyrillic_and_latin_key_labels": '"АБВГ\\nABC"' in dial and '"ЬЭЮЯ\\nWXYZ"' in dial,
    "sip_dtmf_preserved": "shownCall.sendDtmf(ch)" in dial,
    "keypad_plus_held": 'if(ch==\'0\')key.setOnLongClickListener' in dial,
    "speed_dial_preserved": "speedDial.press(ch-'0')" in dial,
    "latin_t9": '"DEFДЕЖЗ"' in matcher,
    "word_boundary_match": 'mapped.split(" +")' in matcher,
    "empty_recent_only": 'if (query.isEmpty() && !item.recent) continue' in matcher,
    "no_external_upload": "https://" not in index and "https://" not in matcher,
}
failed = [k for k,v in checks.items() if not v]
for name in failed: print("FAIL",name)
print(f"{len(checks)-len(failed)}/{len(checks)} smart dialer source invariants passed")
if failed: raise SystemExit(1)
