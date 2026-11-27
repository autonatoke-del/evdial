# Google Play candidate: validation record

Checks recorded on **27 November 2026** (Moscow time). This is a prepared candidate, **not an upload, a submitted review or a published Google Play release**.

Tested source: `099d94d0a3dbac8a938c8713aed1322d7632b60b`.

Tested tree: `512fac35b55ff7f39dc1acdd642e35a1d0b46bc8`.

## Play application build

The `-PgooglePlay=true` build passed `assembleRelease`, `bundleRelease`, `lintRelease` and `testDebugUnitTest` with Java 17, Gradle 8.13, Android Gradle Plugin 8.9.1 and Android API 36. The package is `ru.autonatoke.evdial.design`, version **22 / 0.3.3-rc8-play**, minimum API 26 and target API 36.

All **100 Android/Robolectric tests** passed, with no failures, errors or skips:

| Suite | Passed scenarios |
| --- | ---: |
| Dialpad key layout | 36 |
| Dialer typing and search layout | 24 |
| Battery/lightning call controls | 8 |
| Distribution-specific update actions | 2 |
| Restricted call-log/SMS access and role loss | 10 |
| SIP foreground-service transitions | 14 |
| Google Play window insets | 6 |

The dialer layout suite covers API 34 and 35, small screens and enlarged text. In Play mode on API 35, it dispatches system-bar, display-cutout and navigation insets through the production listener and verifies exactly one inset deduction. It checks stable control positions while typing and asynchronous results change, text clipping and touch-target bounds. These are simulated Android UI checks, not physical Xiaomi/Samsung tests or an Android 16 call test.

An additional **147 source assertions** and **118 JVM behavior assertions** passed. They cover theme migration, compact UI behavior, number lookup, update validation and audio-route requests.

The merged Play manifest was checked for the expected package/version/SDK, absence of `REQUEST_INSTALL_PACKAGES` and the website update FileProvider, and the declared SIP `microphone|specialUse` foreground-service types. Store policy eligibility is still subject to Google's review.

## Exact unsigned artifacts

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `EVDial-0.3.3-rc8-play-unsigned.aab` | 24,142,748 | `d313363506230d455db1cb0389861d6732fb2bc4b3b312f116d4a4401607b5e4` |
| `EVDial-play-unsigned.apk` | 24,153,740 | `8b9b5170f37212c3d934ce5f63ef7ef0b0473f3cd741fb2cb8f8d8afaa89a4af` |

The AAB remains unsigned. App-signing enrollment, an upload key and account access have not been established in Play Console. Do not upload a test-signed APK or silently replace the existing website signing identity.

## Bundle and 16 KB artifact structure

Official **bundletool 1.18.3** validated the AAB and generated a universal APK for inspection. The tool's release-asset metadata, 32,520,401-byte download and SHA-256 `a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29` matched before execution. The bundle manifest and generated APK both retained the expected package, version and minimum/target SDK values.

All **18 ARM64/x86_64 native libraries** passed the repository ELF LOAD/RELRO structure checks in the original APK, the AAB and the generated APK. Their inventories and per-library hashes matched across all three artifacts. Both APKs passed `zipalign -c -P 16 -v 4`. The bundle records `PAGE_ALIGNMENT_16K`; the generated APK's native entries are compressed, consistent with `useLegacyPackaging=true`.

Bundletool used a newly generated, explicit test signing identity. The generated APK's signature matched that test certificate, and the temporary private key was removed afterward. No production or upload key was used. The generated APK/APKS are validation artifacts and must not be uploaded or distributed. The original unsigned AAB and APK remained byte-for-byte unchanged.

The final artifact-validation report has SHA-256 `8eb29efe2a83ad9d4883fae279839abe8932436a7a2331993d957604dd6b0e1e`. Its first report-formatting attempt expected AAPT2's older `sdkVersion` label; the installed tool emitted `minSdkVersion`. The parser was corrected, and the already-validated artifacts and evidence hashes were rechecked without regenerating them. The initial failure receipt remains preserved alongside the successful finalization receipt.

These results establish artifact structure. **No physical device, real SIM/SIP call, 16 KB runtime test or Google Play acceptance is claimed.**

## Website-mode regression

The same tested source also passed `assembleRelease`, `lintRelease` and all **100 Android/Robolectric tests** with `-PgooglePlay=false`, Java 17, Gradle 8.13, AGP 8.7.3 and the installed API 35 SDK. There were no test failures, errors or skips. The manifest retained website version **21 / 0.3.3-rc7**, target API 33, its APK-install permission and update FileProvider; the Play-only `specialUse` overlay was absent.

The unsigned website-mode validation APK is **24,153,988 bytes**, SHA-256 `80f1f8678dc02ccde93decf28f4fc302216c7fe33b0ac2e9fc66f3c6690d8b4d`. It was produced for regression verification and was not signed or deployed. The published website RC7 continues to be identified by its existing release receipt in [F-DROID-VALIDATION.md](F-DROID-VALIDATION.md).

## Published privacy page

The English/Russian page at <https://autonatoke.ru/evdial/privacy/> was published from source `8fe49fe2e5d455a01d0b092d389e20f88c1201b1`. Its public response was HTTP 200, `text/html`, with **29,247 bytes** and SHA-256 `1b339a5909eac29fd57039e50f13bbb63917eb6274372db34fc3c51ba8b77c7b`. Both language sections were visually inspected in the browser. The website RC7 APK, update feed and landing page were verified unchanged by that deployment.

The candidate adds this URL to Settings. Its device navigation still needs to be exercised. See [the submission worksheet](../play/SUBMISSION.md) for account, support contact, app access, declarations, artwork and actual screenshot requirements.
