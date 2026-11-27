# EVDial — SIM and SIP dialer with EV themes

Application ID: `ru.autonatoke.evdial.design`

This draft packaging request is prepared on behalf of the upstream project owner, who requested inclusion in the official F-Droid repository. It has not been submitted or accepted. The project is an actively used preview; it does not yet have a long public release history.

## Links

- Source: https://github.com/autonatoke-del/evdial
- Packaging change: https://github.com/autonatoke-del/evdial/pull/6
- Original website/boundary validation revision: `4fdf0f9a303b9a153cf1253facc1c0dcb86de7f9`
- Pinned packaging revision: `a11262bb457db0c35d580789aaf5ee3ab312122c`
- Build and remaining work: https://github.com/autonatoke-del/evdial/blob/main/docs/F-DROID.md
- Validation record: https://github.com/autonatoke-del/evdial/blob/main/docs/F-DROID-VALIDATION.md
- Privacy: https://github.com/autonatoke-del/evdial/blob/main/PRIVACY.md
- Candidate metadata: https://github.com/autonatoke-del/evdial/blob/main/fdroid/metadata/ru.autonatoke.evdial.design.yml

## App description

EVDial combines SIM calling through Android's default-phone integration with optional SIP calling through a user-configured provider. It includes local contact lookup, recent calls, SMS and sharing shortcuts, selectable themes, and an offline selection of Russian charging locations from Open Charge Map. It requires Android 8.0 or later.

Both F-Droid update-navigation tests and the debug/release manifest boundaries also passed using a cached upstream SDK AAR; that is an application integration check, not a source-build claim.

English and Russian Fastlane metadata and an icon rendered from the application's existing vector source are included upstream. User contact data is not included in listing assets.

## Packaging preparation

Original EVDial code is licensed AGPL-3.0-only. The offline station data includes its Open Charge Map CC BY 4.0 attribution and exact source revision. The 3 Rubina theme, logo and links have been removed at the owner's request.

The optional F-Droid build mode requires a local Linphone AAR, removes the website APK-install permission/provider, and delegates updates to the repository client. It never falls back to the Linphone binary Maven repository. Its preparation helper removes that unused repository declaration before scanning.

Linphone SDK 5.4.128 is pinned to `ce38159e55897c695b7095019b5a1ff612bb181f`, with recursive submodules. The helper applies the Firebase cleanup used by F-Droid's Linphone recipe and the upstream minimal audio flags, then records source/build provenance. A local-AAR dependency must include Commons Compress and AndroidX annotation explicitly; those are declared.

## Checks and open gates

The ordinary website application build, Android Lint, 147 source assertions,
118 JVM assertions and 58 Robolectric tests passed at revision
`4fdf0f9a303b9a153cf1253facc1c0dcb86de7f9`. These checks include old-theme migration
and the current call/dialer layouts. They are distinct from source-SDK
integration at pinned packaging revision
`a11262bb457db0c35d580789aaf5ee3ab312122c` and do not establish physical-device
telephony behavior.

The isolated Linphone SDK source build completed with exit code 0. Its AAR is
21,241,756 bytes, SHA-256
`d786a9296a7917ba70fe6049af7dc35f33e2fd44c8e894cc4acb368739aba0ae`;
the corresponding provenance SHA-256 is
`18f851b20653ca2cae9148ebe44fa73ada55293344d15859bb4366a54e623d53`.
The AAR passed ZIP integrity checks and contains `classes.jar` and nine native
libraries for each of `arm64-v8a`, `armeabi-v7a` and `x86_64`.

The prepared application source at `a11262bb457db0c35d580789aaf5ee3ab312122c`
passed fdroidserver 2.4.6 source scanning with zero findings, warnings or
informational removals and no scanner exclusions. A
[separate full-SDK audit](../docs/F-DROID-SOURCE-SCAN.md) records findings in
upstream fixtures and tools; it is not an official rejection or proof that those
files are proprietary or shipped in EVDial. Its cleanup proposal is unapplied.

The app at that pinned packaging revision then built successfully against the
source-built AAR and passed all 58 Android/Robolectric tests in F-Droid mode.
Android Lint reported zero fatal findings and errors, with 67 warnings. The
unsigned APK is 24,550,881 bytes, SHA-256
`8ce2dfde1c895b4d46e9e97e80cb38eeebab1860ca6d9c5c22d223468787451f`.
fdroidserver 2.4.6 scanned 2,327 classes in that APK with zero problems and zero
warnings. It contains nine native libraries for each of the three target ABIs.
Firebase classes are absent from the source AAR, and the runtime dependency
report contains neither Firebase nor a Maven Linphone dependency. Debug and
release permission/service/provider sets match the prior F-Droid boundary;
there is no APK-install permission or provider, and the F-Droid client query is
present. This is isolated integration evidence, not an official runner or
publication result. The exact metadata, including its updated disabled-status
wording, also passed fdroidserver 2.4.6 lint; hashes and dependency details are in
the validation record.

The candidate F-Droid build entry remains disabled. Remaining gates are documented openly:

- Validate the complete recipe and dependency provisioning in F-Droid's official runner, including its resulting APK checks; isolated source-SDK/app builds and source/binary scans have passed.
- Establish source/rightsholder and redistribution licenses for the remaining EV raster images listed in `docs/F-DROID-ARTWORK.md`; the code license alone is not presented as evidence for those assets.
- Verify SIM/SIP behavior on physical devices with the source-built SDK, including the Xiaomi/Samsung layout cases.
- Review AntiFeatures for optional proprietary web search, maps, messaging shortcuts and DIAGNOST AI. The candidate declares NonFreeNet; messaging promotion may also warrant NonFreeAdd.
- Confirm release/update tracking and the final enabled build recipe after those gates pass.

We are not claiming full inclusion compliance, reproducibility, acceptance or publication at this stage. This request is for packaging review and a path to completing the remaining requirements; the existing website APK is not offered as a substitute for F-Droid's build.
