# EVDial — SIM and SIP dialer with EV themes

Application ID: `ru.autonatoke.evdial.design`

This packaging request is made on behalf of the upstream project owner, who requested inclusion in the official F-Droid repository. The project is an actively used preview; it does not yet have a long public release history.

## Links

- Source: https://github.com/autonatoke-del/evdial
- Packaging change: https://github.com/autonatoke-del/evdial/pull/6
- Tested application revision: `4fdf0f9a303b9a153cf1253facc1c0dcb86de7f9`
- Pinned packaging revision: `a11262bb457db0c35d580789aaf5ee3ab312122c`
- Build and remaining work: https://github.com/autonatoke-del/evdial/blob/packaging/fdroid-20261127/docs/F-DROID.md
- Privacy: https://github.com/autonatoke-del/evdial/blob/packaging/fdroid-20261127/PRIVACY.md
- Candidate metadata: https://github.com/autonatoke-del/evdial/blob/packaging/fdroid-20261127/fdroid/metadata/ru.autonatoke.evdial.design.yml

## App description

EVDial combines SIM calling through Android's default-phone integration with optional SIP calling through a user-configured provider. It includes local contact lookup, recent calls, SMS and sharing shortcuts, selectable themes, and an offline selection of Russian charging locations from Open Charge Map. It requires Android 8.0 or later.

Both F-Droid update-navigation tests and the debug/release manifest boundaries also passed using a cached upstream SDK AAR; that is an application integration check, not a source-build claim.

English and Russian Fastlane metadata and an icon rendered from the application's existing vector source are included upstream. User contact data is not included in listing assets.

## Packaging preparation

Original EVDial code is licensed AGPL-3.0-only. The offline station data includes its Open Charge Map CC BY 4.0 attribution and exact source revision. The 3 Rubina theme, logo and links have been removed at the owner's request.

The optional F-Droid build mode requires a local Linphone AAR, removes the website APK-install permission/provider, and delegates updates to the repository client. It never falls back to the Linphone binary Maven repository. Its preparation helper removes that unused repository declaration before scanning.

Linphone SDK 5.4.128 is pinned to `ce38159e55897c695b7095019b5a1ff612bb181f`, with recursive submodules. The helper applies the Firebase cleanup used by F-Droid's Linphone recipe and the upstream minimal audio flags, then records source/build provenance. A local-AAR dependency must include Commons Compress and AndroidX annotation explicitly; those are declared.

## Checks and open gates

The ordinary application build, Android Lint, 147 source assertions, 118 JVM assertions and 58 Robolectric tests passed at the candidate revision. These checks include old-theme migration and the current call/dialer layouts. They do not establish a complete Linphone source build or physical-device telephony testing.

The candidate F-Droid build entry remains disabled. Remaining gates are documented openly:

- Complete and validate the full Linphone source build plus F-Droid source/binary scans and runner dependency provisioning.
- Establish source/rightsholder and redistribution licenses for the remaining EV raster images listed in `docs/F-DROID-ARTWORK.md`; the code license alone is not presented as evidence for those assets.
- Review AntiFeatures for optional proprietary web search, maps, messaging shortcuts and DIAGNOST AI. The candidate declares NonFreeNet; messaging promotion may also warrant NonFreeAdd.
- Confirm release/update tracking and the final enabled build recipe after those gates pass.

We are not claiming full inclusion compliance, reproducibility, acceptance or publication at this stage. This request is for packaging review and a path to completing the remaining requirements; the existing website APK is not offered as a substitute for F-Droid's build.
