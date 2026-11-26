# F-Droid preparation

## Publication status

EVDial is **not yet published in the official F-Droid repository**. This directory and `fdroid/metadata/ru.autonatoke.evdial.design.yml` contain packaging preparation for review. The build entry is deliberately disabled: a complete Linphone source build, F-Droid scans and redistribution licenses for the remaining EV raster artwork must be established before enabling it. A GitHub change or an APK on the EVDial website is not an official F-Droid release.

The candidate application is `ru.autonatoke.evdial.design`, version code **21**, version name **0.3.3-rc7**. The removed 3 Rubina theme, logo and website shortcuts are absent. An old selected profile falls back to the existing EV preferences. The remaining profiles are EV, Light, Pearl and Minimal.

## Distribution behavior

Normal Gradle builds continue to use the website preview configuration and Linphone's Maven AAR. With `-Pfdroid=true`:

- `-PlinphoneAar` must point to an existing absolute `.aar` path. No Linphone Maven fallback is allowed.
- `BuildConfig.FDROID_BUILD` enables repository-update guidance instead of website APK checks, downloads and installs.
- Both debug and release manifest overlays remove `REQUEST_INSTALL_PACKAGES` and the website update FileProvider.
- The update action can open the installed F-Droid client, with an explicit website fallback.
- Commons Compress and AndroidX annotation are declared explicitly because a local AAR does not supply Maven POM transitives.

Checking these behavior and manifest boundaries with an existing SDK binary is useful integration testing, but **does not verify F-Droid's source-build requirement**. An accepted build must consume the AAR built by the source recipe.

## Building the SDK from source

`fdroid/SDK_SOURCE.json` records the upstream references and build inputs. The source is Linphone SDK **5.4.128**, pinned to commit `ce38159e55897c695b7095019b5a1ff612bb181f`, including all recursive submodules. The `liblinphone` submodule must be `df95d846913d8f04d2b48ee05f9089e272dfc10f`.

Provision Java 17, CMake 3.22 or later, NDK 27, Android platforms 31 and 35, the required build tools, installed Gradle 8.11.1 for the SDK, and Gradle 8.13 for the app. Host tools and Python modules are listed in the JSON file. The initial offline build needs a populated Gradle dependency cache. The exact F-Droid runner provisioning remains to be validated.

The candidate metadata explicitly installs Java 17 and selects its app daemon. It uses F-Droid's `gradlew-fdroid` launcher to resolve the SDK's existing Gradle 8.11.1 wrapper properties, then passes the installed cached executable to the helper. The app includes a text-only `gradle/wrapper/gradle-wrapper.properties` pin for Gradle 8.13 and its official SHA-256; no wrapper JAR is included. These choices remove implicit version selection but still need the full F-Droid runner check.

Use a disposable SDK checkout and a separate empty work directory:

```bash
git clone https://gitlab.linphone.org/BC/public/linphone-sdk.git /absolute/linphone-sdk
git -C /absolute/linphone-sdk checkout ce38159e55897c695b7095019b5a1ff612bb181f
git -C /absolute/linphone-sdk submodule update --init --recursive

bash fdroid/build-linphone.sh prepare /absolute/linphone-sdk /absolute/linphone-work

LINPHONE_GRADLE=/installed/gradle-8.11.1/bin/gradle \
ANDROID_NDK_HOME=/installed/ndk/27.x \
ANDROID_HOME=/installed/android-sdk \
bash fdroid/build-linphone.sh build /absolute/linphone-sdk /absolute/linphone-work
```

Preparation removes the Firebase sources, import, initialization call and Gradle declaration using the same approach as the [official Linphone recipe](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/org.linphone.yml). It also replaces the SDK wrapper entry point with an adapter to the installed Gradle executable. It does not fetch a binary Linphone SDK.

The build uses upstream minimal feature flags, explicitly disables nonfree features, Tunnel and the EKT server plugin, and checks ARM32/ARM64/x86_64 output. A successful build writes `provenance.json` with source revisions, patch hashes, tools and AAR SHA-256. This provenance establishes one SDK build; it is not proof of reproducibility or F-Droid approval.

`LINPHONE_JOBS` defaults to 2 (range 1–8). Gradle uses `--offline` by default. Set `FDROID_GRADLE_OFFLINE=0` only in a runner stage where dependency resolution is permitted. Network isolation belongs to the build runner.

## Building the app

Before F-Droid scans a disposable app checkout, remove its unused website Maven declaration:

```bash
python3 fdroid/prepare-source.py
gradle assembleRelease lintRelease \
  -Pfdroid=true \
  -PlinphoneAar=/absolute/linphone-work/build/linphone-sdk/bin/outputs/aar/linphone-sdk-android-release.aar
```

`prepare-source.py` removes only the exact marked repository declaration, is idempotent and fails on unexpected source. The app still has its normal Google Maven/Maven Central dependencies. There is no scanner exclusion for concealing the custom Maven repository.

Do not sign an F-Droid candidate with the website's private key as a substitute for the repository build. Unless a reproducible-build signing arrangement is established, F-Droid uses its own certificate; an existing website installation cannot be assumed to accept that APK as an update. Keep existing installation data when evaluating migration.

## Review materials and remaining work

- [App privacy information](../PRIVACY.md).
- [Validation record](F-DROID-VALIDATION.md) and [submission draft](../fdroid/SUBMISSION.md).
- English and Russian text under `fastlane/metadata/android/`, including changelog 21.
- Listing icon rendered from the existing Android vector by `tools/render_listing_icon.py`.
- [Remaining artwork inventory](F-DROID-ARTWORK.md); original code's AGPL license does not document those raster assets' provenance.
- The offline Open Charge Map subset includes source revision, modifications and CC BY 4.0 attribution in `src/main/assets/charging-license.txt`.
- Candidate metadata is disabled and pins immutable packaging revision `a11262bb457db0c35d580789aaf5ee3ab312122c`. Automatic update checks remain disabled during packaging work.

Before submitting an enabled recipe, run `fdroid lint`, the source scanner, the complete SDK/app build and APK permission/binary checks. Confirm the optional proprietary links' AntiFeatures with reviewers. Test SIM/SIP behavior on devices before relying on a new SDK build; do not represent source or UI tests as a physical-call test. Do not mark an unchecked requirement as passed.

The official route is a metadata merge request in [fdroiddata](https://gitlab.com/fdroid/fdroiddata), or a [request for packaging](https://gitlab.com/fdroid/rfp). Submission requires a GitLab account. Maintainers review the source and build recipe, then their build/publish process makes the app available; preparation or submission alone does not establish publication. See the [official quick start](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).
