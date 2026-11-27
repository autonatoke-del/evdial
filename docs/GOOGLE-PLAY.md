# Google Play preparation

Status as of 27 November 2026: **candidate built and checked; not uploaded to Play Console and not approved**. A successful local build is not store approval. See [the validation record](GOOGLE-PLAY-VALIDATION.md) for exact artifacts and results, and [the submission worksheet](../play/SUBMISSION.md) for unresolved account and review fields.

## Candidate build

| Setting | Google Play mode |
| --- | --- |
| Gradle property | `-PgooglePlay=true` |
| Application ID | `ru.autonatoke.evdial.design` |
| Display name | EVDial |
| Version | `22` / `0.3.3-rc8-play` |
| Android SDK | Minimum 26; compile and target 36 |
| Native ABIs | arm64-v8a, armeabi-v7a, x86_64 |
| Updater | Google Play listing; website APK downloader/installer disabled |

The Play and F-Droid properties are mutually exclusive. This mode uses the declared Linphone SDK dependency; it does not establish that F-Droid's separate source-build requirements have passed. The normal website build retains its separate configuration.

With the JDK/Gradle toolchain in [BUILDING.md](../BUILDING.md), Android API 36 and build-tools 35.0.0 installed:

```bash
gradle -PgooglePlay=true assembleRelease bundleRelease lintRelease
gradle -PgooglePlay=true testDebugUnitTest
python3 tests/run_jvm_regressions.py
```

The release bundle is generated under `build/outputs/bundle/release/`. Signing is not configured in the repository. Record the exact tested commit, artifact hash, signing certificate, merged manifest and test results before submitting that artifact. Check the final bundle's native libraries and installation/runtime behavior on a 16 KB device or emulator; changing target SDK alone does not validate the bundled Linphone libraries. Follow Android's [16 KB compatibility guide](https://developer.android.com/guide/practices/page-sizes).

## Behavior and review gates

The Play overlay removes `REQUEST_INSTALL_PACKAGES` and the website updater's file provider. The application must keep its store-update guard on every download/install entry point. Verify the final merged manifest and packaged behavior.

Call-log access and direct SMS must be restricted to the current default-phone role, including permission prompts, asynchronous work and role loss. When the role is absent, SMS should open a draft in the user's messaging app. The permitted-use declaration remains subject to [Google's SMS/Call Log review](https://support.google.com/googleplay/android-developer/answer/10208820).

The proposed SIP foreground-service split is `specialUse` for user-enabled idle registration/incoming signalling, and `microphone` for an initiated or answered voice call. The idle mode must not capture microphone audio. **The `specialUse` classification is a review gate, not an approval already granted.** Its declaration needs a specific explanation and demonstration. Inspect background start restrictions, notification denial, locked-screen incoming calls and the transition between service modes on supported Android versions. See [foreground service types](https://developer.android.com/about/versions/14/changes/fgs-types-required) and [Play service/full-screen declarations](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en).

Target-36 device checks must include call audio/routing, role grant/revocation, rejected permissions, background and cold-start incoming calls, navigation insets, large text and native-library loading. No phone call, microphone capture or paid provider action is implied by preparing this document; arrange a controlled, authorized test environment.

## Store materials and signing

English and Russian copy is in `play/metadata/`. The 512 px icon comes from the existing repository listing asset. Both locales include an opaque 1024×500 feature graphic, generated from the original vector icon by `tools/render_play_graphic.py`. Actual UI screenshots are still required; use the final UI, synthetic contacts and cleared artwork. Do not upload users' phone photographs or invent screenshots. The [asset specifications](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en) apply.

The policy sources are [PRIVACY.md](../PRIVACY.md) and [PRIVACY.ru.md](../PRIVACY.ru.md). The bilingual page is published at <https://autonatoke.ru/evdial/privacy/>. On 27 November 2026, the public response returned HTTP 200 with the expected HTML hash, and both language sections were visually inspected in the browser. The candidate source adds the same URL to Settings; exercise that action on a device before submission. The store's support email is separate and remains unconfirmed.

To preserve update compatibility with existing website installations, enroll the existing production app-signing key in Play App Signing through Google's secure import flow, then use a separate upload key. Do not let an unrelated generated signing key replace the existing identity by accident. Keep private keys outside Git and chat. See [Android app signing](https://developer.android.com/studio/publish/app-signing).

## Release status discipline

The account's identity verification, applicable test track, review declarations, artwork rights and production access must be established in the actual Console. Do not infer the account type or testing exemption. A draft, an internal test upload, a submitted review and a publicly available release are different states; record the one actually reached in the release receipt.
