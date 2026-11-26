# F-Droid preparation: validation record

Application revision: `4fdf0f9a303b9a153cf1253facc1c0dcb86de7f9`.

Application tree: `ee836f515df1bc5f722568405d6830e8a94b00f4`.

Candidate: `ru.autonatoke.evdial.design`, **21 / 0.3.3-rc7**. The application change is reviewed in [PR 6](https://github.com/autonatoke-del/evdial/pull/6). Checks ran on 27 November 2026 (Moscow time).

## Completed application checks

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

`fdroid lint ru.autonatoke.evdial.design` passed with **fdroidserver 2.4.6**, using the complete official configuration (including category/AntiFeature assets) from fdroiddata revision `fe9315158a207f6d56dfd4eeb15e24a193540e91`. The metadata's build is disabled; this checks the metadata, not successful execution of its native build commands. Plain YAML parsing is not substituted for this check.

## Source SDK work

The exact SDK source commit `ce38159e55897c695b7095019b5a1ff612bb181f` was retrieved from upstream. All **52 recursive submodules** were initialized at their recorded revisions, including `liblinphone` commit `df95d846913d8f04d2b48ee05f9089e272dfc10f`.

Full native SDK compilation, source/binary scanning and the complete F-Droid runner build are not yet recorded as passed. The candidate recipe remains disabled. The [artwork inventory](F-DROID-ARTWORK.md) also remains an open redistribution-license gate.

No real call was placed during these checks. Source, JVM and UI tests do not establish physical Xiaomi/Samsung behavior or end-to-end SIP interoperability. No acceptance or publication by F-Droid is claimed.
