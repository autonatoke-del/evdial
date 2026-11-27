# EVDial — open-source Android phone app

**EVDial** is an Android phone application combining a local SIM dialer and optional SIP calling, contacts, recent calls, messaging shortcuts, an EV charging directory, and visual themes.

- Source code: https://github.com/autonatoke-del/evdial
- Website / preview APK: https://autonatoke.ru/evdial/
- Android package used by the existing preview: `ru.autonatoke.evdial.design`.
- **License for EVDial original code: AGPL-3.0-only** (see [LICENSE](LICENSE)).
- The 15 project-generated EV raster assets are distributed under the same AGPL-3.0-only license to the extent copyright applies. The owner's confirmation and exact file hashes are recorded in the [artwork inventory](docs/F-DROID-ARTWORK.md).
- The SIP library `org.linphone.minimal:linphone-sdk-android:5.4.128` is supplied by Belledonne Communications under AGPLv3 or a separate commercial license. It is **not** developed or endorsed by EVDial.
- The offline charging dataset has separate attribution and license: [Open Charge Map / CC BY 4.0](src/main/assets/charging-license.txt).
- Original project brand names and logos remain trademarks of their respective holders. The code license does not grant trademark or endorsement rights. See [TRADEMARKS.md](TRADEMARKS.md).

## Build from source

Requires **JDK 17**, **Android SDK API 35 / build-tools 35.0.0**, **Gradle 8.13**, and Internet access to the repositories in `settings.gradle` (Google Maven, Maven Central, Linphone). Do not place a signing key in the repository.

```bash
gradle --version
gradle assembleDebug
# Android Lint: gradle lintDebug
python3 tests/ThemeSourceChecks.py
python3 tests/ModernUxSourceChecks.py
python3 tests/run_jvm_regressions.py
```

For the latest candidate, also run `python3 tests/CompactUiSourceChecks.py`. See [BUILDING.md](BUILDING.md) for configuration, permissions and verification.

## F-Droid preparation

An optional repository build is prepared for F-Droid. The Linphone SDK and app
have been built from source, 58 tests passed, and the application source and
binary scans passed. The project owner confirmed the bundled EV raster artwork
was generated for EVDial; its provenance and license scope are now recorded.
The build delegates app updates to the repository client.

This is **not yet an official F-Droid listing**. The candidate recipe remains
disabled pending packaging review and verification for the official F-Droid
build. The prepared evidence can accompany a packaging-review request; physical
SIM/SIP checks are follow-up quality work. See [F-Droid preparation](docs/F-DROID.md)
for the exact status and commands. English and Russian listing text is maintained
under `fastlane/metadata/android/`.

**Installation warning:** self-built/debug APKs do not have the official production signing key. Do not uninstall an installed signed EVDial to install a debug APK; Android rejects an in-place update if certificates differ. No signing material, access tokens, user contacts, CRM, backend, call records or service credentials are included in this repository.

## Privacy and scope

Contacts, call logs and links to user-selected existing recordings stay under Android access controls unless the user explicitly sends something or chooses a provider. SIP contacts the server the user configures; the website build's manual updater makes HTTPS calls to the preview feed. The optional DIAGNOST AI link opens an **external service with separate terms**. See [PRIVACY.md](PRIVACY.md) for permissions, local storage and external connections. This repository contains the *client only*, not the private НЕБЕНЗИН platform, CRM, telephony backend, server code or its deployments.

This is an evolving open-source preview. SIM default-dialer role, manufacturer restrictions, SIP, outgoing/incoming calls, vCard sharing and store-policy compliance require device-specific verification before relying on a build.
