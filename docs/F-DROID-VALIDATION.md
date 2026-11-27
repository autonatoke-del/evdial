# F-Droid preparation: validation record

Candidate: `ru.autonatoke.evdial.design`, **21 / 0.3.3-rc7**. The application change is reviewed in [PR 6](https://github.com/autonatoke-del/evdial/pull/6). Checks ran on 27 November 2026 (Moscow time).

## Website and distribution-boundary checks

Application revision: `4fdf0f9a303b9a153cf1253facc1c0dcb86de7f9`.

Application tree: `ee836f515df1bc5f722568405d6830e8a94b00f4`.

These are the original website-release and distribution-boundary checks. They
are kept separate from the later F-Droid source-SDK integration at packaging
revision `a11262bb457db0c35d580789aaf5ee3ab312122c`.

| Check | Result |
| --- | --- |
| Website `assembleRelease` and `lintRelease` | Passed with Gradle 8.13 and Java 17 |
| Source assertions | 147 passed |
| JVM behavior assertions | 118 passed, including removed-theme migration |
| Native Android/Robolectric tests in website mode | 58 passed: 36 keypad, 12 dialer, 8 call controls, 2 update navigation |
| F-Droid update-navigation tests | Both passed in F-Droid mode |
| F-Droid debug/release manifest merge | APK-install permission and update FileProvider absent; F-Droid client visibility present |
| F-Droid debug/release BuildConfig | `FDROID_BUILD=true` |
| Missing local SDK AAR | Rejected without Maven fallback |
| Removed theme | Logo file and production theme/domain references absent |

The F-Droid boundary checks intentionally used the existing cached upstream SDK AAR to isolate application behavior. **They do not establish the SDK source-build requirement and did not produce an official F-Droid APK.**

The ordinary website APK's unsigned SHA-256 was `7d706cfe83efadc2287670b227799834b964b915a7583df962259791babd4556`. Its version/package, native ABIs and website update permission/provider were checked inside the APK, independently of generated files changed by the F-Droid-mode test.

The signed website RC7 was published and independently downloaded for verification. Its SHA-256 is `49b378da64c887996525372c853b673f0811b6b3c6a6cc92c66c90edc1b9b177`, size 24,173,179 bytes. The previous signing certificate is preserved. This is a website preview release, not an F-Droid release.

## Metadata validation

`fdroid lint ru.autonatoke.evdial.design` passed with **fdroidserver 2.4.6**, using
the official configuration assets, including categories and AntiFeatures, from
fdroiddata revision `fe9315158a207f6d56dfd4eeb15e24a193540e91`.

It passed again after the status-only `disable` and `MaintainerNotes` wording
update. The exact updated metadata SHA-256 is
`9cc998ca3a6ec366c53e697fdfd8ee9b69b9b3c7875c8d19d92a1309c8e553c1`.
That lint ran in a separate directory with a verified copy of all 241 official
configuration assets; the previous metadata input was left unchanged. The source
pin, recipe commands and build settings are unchanged, and the build remains
disabled. Metadata lint does not establish successful execution of the native
build commands. Plain YAML parsing is not substituted for this check.

## Completed SDK source build

The exact SDK source commit `ce38159e55897c695b7095019b5a1ff612bb181f` was retrieved from upstream. All **52 recursive submodules** were initialized at their recorded revisions, including `liblinphone` commit `df95d846913d8f04d2b48ee05f9089e272dfc10f`.

The existing source-build helper completed in an isolated container with exit
code **0**. Native compilation, Android `assembleRelease` and the final SDK
target succeeded. The resulting AAR was checked against its provenance; ZIP
integrity, `classes.jar` and all three target ABIs were verified.

| SDK output check | Verified result |
| --- | --- |
| AAR filename | `linphone-sdk-android-release.aar` |
| AAR size | 21,241,756 bytes |
| AAR SHA-256 | `d786a9296a7917ba70fe6049af7dc35f33e2fd44c8e894cc4acb368739aba0ae` |
| `provenance.json` SHA-256 | `18f851b20653ca2cae9148ebe44fa73ada55293344d15859bb4366a54e623d53` |
| Native libraries | 9 each for `arm64-v8a`, `armeabi-v7a` and `x86_64` |
| Provenance source identity | Expected SDK commit and actual AAR hash matched |

This verifies one isolated SDK source build. It does not establish
reproducibility or successful execution of the entire candidate recipe in
F-Droid's official runner.

## Application source scan

The disposable application checkout at
`a11262bb457db0c35d580789aaf5ee3ab312122c`, after its existing source-preparation
step, passed fdroidserver 2.4.6 `scan_source()` with **0 fatal findings,
0 warnings and 0 informational removals**, using no `scanignore` or `scandelete`.

The [supplemental SDK audit](F-DROID-SOURCE-SCAN.md) separately records 2,289
scanner findings, 29 warnings and 34 automatic removals in a disposable copy of
the entire SDK, mostly upstream fixtures and tools. That wider scan is not an
official rejection, evidence that proprietary files ship in the app, or a
failure of the zero-finding application scan. Its proposed, hash-pinned cleanup
has not been applied.

## Application integration with the source-built SDK

The integration target is pinned packaging revision
`a11262bb457db0c35d580789aaf5ee3ab312122c` and the AAR identified above.
Its application tree is `c058944196ad2589a126a933b0a9c188d0759558`.
The isolated application verification completed with exit code **0**, without
timeout, using that source-built AAR rather than the cached website SDK binary.

| Source-AAR application check | Result |
| --- | --- |
| `assembleRelease` and `lintRelease` | Passed with Gradle 8.13 and Java 17 |
| Android/Robolectric tests | 58 passed in F-Droid mode: 36 keypad, 12 dialer layout, 8 battery call controls, 2 update navigation; no failures, errors or skips |
| Android Lint | 0 fatal findings, 0 errors, **67 warnings** |
| Android API levels in this pinned candidate | Minimum 26, target 33 |
| Unsigned APK | `EVDial-0.3.3-rc7-fdroid-source-unsigned.apk` |
| APK size | 24,550,881 bytes |
| APK SHA-256 | `8ce2dfde1c895b4d46e9e97e80cb38eeebab1860ca6d9c5c22d223468787451f` |
| APK native libraries | 27 total, nine for each of the same three ABIs |
| fdroidserver 2.4.6 `scan_binary()` | 2,327 classes scanned, **0 problems and 0 warnings** |
| Binary scanner signature cache | `suss.json`, SHA-256 `0c9cfef55f4aabe06d45b61e948c656a1da3c4f154a665d9abc6461a51ad7de3` |

The binary scan used fdroidserver defaults with an explicit Android SDK and an
isolated copy of its signature cache. Android Lint warnings and F-Droid binary
scanner results are separate checks. The unsigned artifact is integration
evidence; it is not an official F-Droid build or a published update.

The source AAR's `classes.jar` contained 503 entries and passed the Firebase-class
absence check. The release runtime dependency report contained no Firebase or
Maven Linphone dependency. It resolved `commons-compress` to **1.16.1** and the
explicit AndroidX annotation **1.1.0** declaration to **1.2.0**.

Debug and release permission, service and provider sets matched the earlier
F-Droid boundary checks. The final release has no providers and no
`REQUEST_INSTALL_PACKAGES`; the F-Droid client package query is present.
`MobileCalls` is exported and protected by `BIND_INCALL_SERVICE`, while
`CallService` is not exported and declares the `microphone` foreground-service
type. The generated release manifest's hash is identical to that of the earlier
F-Droid boundary manifest.

| Captured integration evidence | SHA-256 |
| --- | --- |
| Combined `validation.json` | `7de909deb51ec7f72dde914c01460784df0627dc09e74adbb0f968870ca01ad2` |
| Binary-scan report | `45f4e93608cf3f2c16457e62d27f696a31c4e84f495e52cb9808ac894877fb84` |
| Generated release manifest | `2cfd53d38e000f24ffbbcdbbaae7f850cf7fa2cc02be35208e92033695bf9b89` |
| Release runtime dependency report | `a104dd5ef5ea35b3a3675eb08ec39f9a779cf1148390e9ee5e46e1ceaccf39fc` |

## Remaining gates

The candidate recipe remains disabled. Complete execution in the official
F-Droid runner, including its tool and dependency provisioning, remains
unverified. The [artwork inventory](F-DROID-ARTWORK.md) remains an open
redistribution-license gate. Optional-service AntiFeatures and the final recipe
still require maintainer review.

No real call was placed during these checks. Source, JVM and UI tests do not establish physical Xiaomi/Samsung behavior or end-to-end SIP interoperability. No acceptance or publication by F-Droid is claimed.
