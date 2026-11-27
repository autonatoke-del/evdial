# EVDial — Google Play submission worksheet

Prepared 27 November 2026. **Not uploaded. Not approved.** This file separates reusable app facts from answers that require the owner, the final artifact or the actual Play Console. It must not be submitted as a blanket compliance assertion.

## Listing fields

| Field | Prepared value or remaining action |
| --- | --- |
| Title | EVDial |
| Package | `ru.autonatoke.evdial.design` |
| Candidate | Version code 22, `0.3.3-rc8-play`, `-PgooglePlay=true` |
| Listing text | [English](metadata/en-US/full_description.txt), [Russian](metadata/ru-RU/full_description.txt), with separate titles and short descriptions |
| Category | Communication is proposed from the app's calling purpose; confirm in Console |
| Privacy URL | `https://autonatoke.ru/evdial/privacy/` — prepared destination, deployment/access verification pending |
| Website/source | `https://autonatoke.ru/evdial/` / `https://github.com/autonatoke-del/evdial` |
| Support email | Owner must provide an authorized public address; none invented |
| Developer/account identity | Verify from the actual Console; the public project handle is `autonatoke-del` |
| Price, countries, age audience | Unconfirmed; do not infer from the source or business profile |
| Graphics | 512×512 icon and EN/RU 1024×500 feature graphics prepared; actual UI screenshots pending |

Store limits and contact requirements: [Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en). Graphics: [preview asset specifications](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en). Use only cleared artwork, and synthetic contact/call data in screenshots.

## App access — draft reviewer instructions

EVDial has no developer-operated sign-up or app account. The dialer, theme settings and offline charging directory can be opened without an EVDial login. Android may request the default-phone role and permissions for relevant functions.

SIP is an optional provider account configured in SIP settings with login, server, password and transport. Review access for that part is **not ready** until an authorized disposable test account, reachable server and safe test destination are supplied privately through Console. Do not use personal or production credentials. Provide the exact tested setup steps with the final build; do not claim that every feature is unrestricted merely because the app has no sign-up screen.

See [review access and app-content requirements](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en).

## Data Safety — draft mapping, not final form answers

The following is inferred from application source and must be checked against the final Play build and its SDKs. Google defines collection and sharing separately; absence of a developer backend does not establish absence of collection. See [the form definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).

| Implemented flow | Proposed treatment / unresolved point |
| --- | --- |
| Contact lookup, local Android/SIP history, speed dial | Local use alone is not off-device collection. A dialed recipient or transmitted call metadata must be considered separately. |
| SIP login/registration | Optional collection of account identity for calling/authentication. Include phone-number identity when applicable; check actual SDK traffic. |
| SIP voice and call signalling | Collection for calling. Map live voice and recipient/interaction data in the form. Provider retention is unknown; do not label everything ephemeral. |
| Direct SMS via Android/carrier | Assess SMS/MMS collection for message text and recipient; direct sending is role-gated. |
| User-selected contact/message/file sharing | Assess the user-initiated sharing exception. It is not a general exemption for other collection. |
| Wallpaper import and existing-recording playback | Local selected-file use; no EVDial upload implemented for these features. |
| Browser/maps/DIAGNOST links | Browser-controlled external activity; inspect the URL/query data EVDial passes. No automatic contact/history payload is attached to DIAGNOST. |
| Analytics, advertising IDs, crash reports | None implemented in EVDial application source. Confirm third-party SDK behavior before choosing final answers. |

Candidate purposes are app functionality and SIP account authentication. Do not assert that an arbitrary user-configured SIP provider processes data on the developer's behalf. Current transports include UDP/TCP; password encryption in Android Keystore does not prove encryption of all transmitted data. A Yes answer for universal transport encryption is not established.

There is no EVDial account-creation flow. Local data can be cleared through Android; the app cannot promise deletion of carrier or SIP-provider records. Read [PRIVACY.md](../PRIVACY.md) with the final declaration and resolve any mismatch before submission.

## Permission and service declarations

### Default phone, call log and SMS

Draft explanation, usable only after validating role checks:

> EVDial is selected by the user as the default phone application. It reads recent calls for the call-history screen and number lookup. Direct SMS is sent only after the user submits a message. Restricted access and permission prompts require the current default-phone role; the app stops using that access after role loss. Without the role, messaging opens a draft in the user's SMS application.

Evidence required: role grant, denied role, revoked role and SMS draft fallback in the final artifact. [Permitted-use policy](https://support.google.com/googleplay/android-developer/answer/10208820).

### SIP foreground service

Proposed `specialUse` explanation:

> When the user enables incoming SIP calls, EVDial maintains registration and signalling with the configured provider so incoming calls can arrive. This client does not use a push-notification integration. A persistent notification exposes the listener; disabling incoming SIP stops the idle listener. Delaying or interrupting it can prevent timely incoming-call delivery. Idle listening does not capture microphone audio.

The separate `microphone` mode supports audio during an initiated or answered voice call. `specialUse` eligibility and background transitions remain subject to Android behavior testing and Play review. Supply a demonstration of enabling/disabling the listener, its notification, an incoming call and the microphone-mode transition. Do not describe the service as approved or system-exempted. [Android type guidance](https://developer.android.com/about/versions/14/changes/fgs-types-required).

### Full-screen incoming-call UI

Draft purpose: alert the user to an incoming SIM or SIP voice call and offer answer/decline controls. Demonstrate denied full-screen access and the notification fallback. Complete the [full-screen and foreground-service declarations](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en) against the packaged manifest.

## Other Console decisions

The application contains communication functions and opens web services. Complete the IARC questionnaire from the actual UI; do not invent a rating or infer that it targets children. The owner must confirm intended age groups and distribution countries. For the ads declaration, review the DIAGNOST links and any promotional presentation: "no advertising SDK" alone does not decide the answer. [App-content guidance](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en).

## Gates before sending for review

- Verify the developer account, public support contact, production access and any account-specific testing requirement. [Account information](https://support.google.com/googleplay/android-developer/answer/13628312?hl=en).
- Confirm redistribution rights for all remaining visual assets; prepare real screenshots without personal data.
- Verify the public privacy page and in-app link, and reconcile it with the final Data Safety answers.
- Complete controlled SIP/SIM device tests, native 16 KB validation and permission/service demonstrations; record the actual results.
- Enroll the existing app-signing identity securely for website-update continuity and prepare a separate upload key. [Signing guidance](https://developer.android.com/studio/publish/app-signing).
- Identify the exact signed AAB by commit, version, hash and certificate; submit that artifact only after these gates are satisfied. Record upload, review and publication as separate outcomes.
