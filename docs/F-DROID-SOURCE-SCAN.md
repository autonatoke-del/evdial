# F-Droid source scan: application and supplemental SDK audit

Audit date: 27 November 2026 (Moscow time).

The prepared EVDial application source passed a direct **fdroidserver 2.4.6 source
scan with zero findings**. A separate scan of the complete prepared Linphone SDK
reported binary-file and dependency-policy findings, mostly in upstream test
fixtures. The SDK scan was an additional audit, outside the application directory
that the standard F-Droid build scans. It is not an official F-Droid rejection or
a finding that these files are proprietary or shipped in EVDial.

The cleanup manifest in this change is a **proposal only**. No proposed cleanup
was applied; no scanner exclusions, build helpers, application sources or
candidate build commands were changed.

## Inputs and method

| Input | Pinned value |
| --- | --- |
| Application packaging source | `a11262bb457db0c35d580789aaf5ee3ab312122c` |
| Linphone SDK | 5.4.128, `ce38159e55897c695b7095019b5a1ff612bb181f` |
| liblinphone submodule | `df95d846913d8f04d2b48ee05f9089e272dfc10f` |
| Scanner | fdroidserver 2.4.6 |
| Source-scan exclusions and deletion rules | `scanignore=[]`, `scandelete=[]` |

The application was checked out at the pinned revision and processed with its
existing `fdroid/prepare-source.py`, which removed the exact unused website
Linphone Maven declaration. The SDK had already been prepared by the existing
source-build helper, including its Firebase removal and installed-Gradle adapter.

The scanner API was invoked as
`scanner.scan_source(path, build=build, json_per_build=messages)`, using
`metadata.Build()`, `build.gradle=['yes']` and a `scanner.MessageStore()`. Global
scanner options and metadata arguments were initialized through fdroidserver's
own parser. Signature refresh was enabled, with a dedicated scanner cache.
Runtime configuration used fdroidserver defaults and that cache location; the
official configuration assets used for the earlier metadata lint are a separate
check. No `fdroid build` runner result is inferred from this API invocation.

Scans ran on disposable copies. The SDK copy excluded only VCS directories that
the scanner itself skips (`.git`, `.hg`, `.svn`, `.bzr`). Its file bytes were
inventoried before scanning, and distinct inodes were verified so scanner writes
could not propagate through hard links. The original prepared SDK's file hashes
were rechecked afterward and remained unchanged.

## Results and their scope

| Scope | Fatal scanner findings | Warnings | Informational automatic removals |
| --- | ---: | ---: | ---: |
| Prepared application source | **0** | **0** | **0** |
| Complete prepared SDK, supplemental audit | **2,289** | **29** | **34** |

“Fatal” here is the scanner's classification and return-count behavior. For
example, it classifies an opaque fuzz input as a binary file; that classification
does not establish the input's license, whether it contains executable code, or
whether the Android build uses it. The combined audit process returned a
nonzero exit status because of the SDK result; the application result was zero.

Of the SDK findings, **2,242** are under `external/srtp/fuzzer/corpus`. The other
**47** have exact paths, byte sizes, hashes and original scanner finding text in
[the proposed cleanup manifest](../fdroid/audit/sdk-unused-binary-cleanup-proposal.json).

| Remaining fatal findings | Files |
| --- | ---: |
| WebRTC test data, test utility and example JAR | 15 |
| MbedTLS test and fuzz inputs | 13 |
| Windows codec2 tools | 3 |
| Xerces documentation JARs | 3 |
| JPEG JavaDoc index archives | 3 |
| OpenH264 iOS test tools | 2 |
| mediastreamer H.265 fixtures | 2 |
| iOS SILK static archive | 1 |
| Windows NuGet executable | 1 |
| LIME Node test-server manifest without a lockfile | 1 |
| ORTP H.265 fixture | 1 |
| Android tester Gradle declaration of a custom Maven repository | 1 |
| OpenLDAP Windows message resource | 1 |
| **Total outside the SRTP corpus** | **47** |

The 29 warnings comprise 12 executable-mode SILK PDFs, nine belle-sip fuzz
fixtures, seven ZXing fixtures and `external/codec2/win32/codec2.dll`. They are
preserved separately in the proposal and are **not** included in its deletion
set. Any later decision to remove the DLL requires obtaining and validating its
exact hash; it must not silently enlarge the reviewed set.

The 34 informational removals affected only the disposable copy. The scanner
automatically removes `gradlew`, `gradlew.bat`, wrapper JARs and APK files even
with empty `scandelete`. The prepared SDK's Gradle adapter was among those removed
in this supplemental scan. That is a reason to use a copy, not evidence that the
standard application scan would delete an external SDK adapter.

In fdroidserver 2.4.6, `build.py` passes the application `build_dir` to
`scan_source()`. The candidate's SDK is an external srclib. Scanning the complete
SDK separately therefore has a wider scope than that standard source-scan step.

## Proposed cleanup and build-use evidence

The unapplied proposal contains two explicit sets:

| Proposed set | Files | Bytes |
| --- | ---: | ---: |
| Complete `external/srtp/fuzzer/corpus` directory | 2,250 | 894,723 |
| Individually listed paths | 47 | 8,728,432 |

The corpus includes eight fixtures that did not trigger the scanner. Removing
that entire test-data directory is an explicit proposed choice, not an assumption
that the directory has only 2,242 files. Its complete sorted inventory is pinned
by an aggregate hash, count and size. Each of the other 47 files is pinned by its
individual SHA-256 and size. The manifest retains the original finding text,
including messages localized by the scanner environment.

The observed top-level, ARM64 and ARMv7 CMake caches set the following options to
`OFF`: `ENABLE_UNIT_TESTS`, `ENABLE_TESTS_COMPONENT`, `ENABLE_TOOLS`,
`ENABLE_SILK`, `ENABLE_CODEC2`, `ENABLE_VIDEO`, `ENABLE_CSHARP_WRAPPER` and
`ENABLE_NON_FREE_FEATURES`. The pinned `android-common` preset also disables the
test and tool targets. `cmake/BCProjects.cmake` gates `mssilk` on `ENABLE_SILK`;
liblinphone and mediastreamer gate their tester directories on
`ENABLE_UNIT_TESTS`.

A path-reference check of **1,550 generated CMake, make and link files** found
zero references to the 47 individual paths or the SRTP corpus directory. The
x86_64 configuration was not yet present at that observation. These results
support the proposed scope for the minimal Android target; they do not replace
a fresh all-architecture build after cleanup.

If this proposal is implemented in a later change:

1. Use a fresh checkout and separate work directory at the pinned SDK commit.
2. Validate **every** proposed input before deleting any file. Reject symlinks,
   nonregular files, paths outside the SDK, unexpected corpus files, count or size
   differences, and all hash mismatches.
3. Record the cleanup-manifest hash and removed-file hashes in preparation and
   final provenance, alongside the existing Firebase-removal record. Require
   those records when checking the prepared source state.
4. Re-scan a disposable prepared copy, build all target ABIs from the cleaned
   source, and verify the final application binary and manifest.

No blanket `scanignore` is proposed. This audit does not enable the disabled
candidate or establish native build completion, a clean final APK scan,
reproducibility, artwork redistribution rights or official F-Droid acceptance.
Those checks belong in their own validation records.

## Evidence digests

The complete captured reports are identified by their SHA-256 digests. This
document and the small proposed manifest preserve the reviewable conclusions;
the large SDK inventory and full scanner reports are not included in this change.

| Evidence | SHA-256 |
| --- | --- |
| Full source-scan report | `fbd4afebddda3416c3c188679434e920dca9c696d04d5c472a8450ebf4c8ede4` |
| SDK snapshot inventory before scanning | `25d1362442ac55ae343d87b22146b456ace41087b6201958d199a24ee5c51a9d` |
| Exact flagged-file hashes | `83ef8f628824e904476005bff85ba06261cbfcfd42ce6cf98983e32f22779d7c` |
| Proposed cleanup manifest in this change | `1dd64a0d9415a94153411b9ee25b259f9a28a3ab2208d69192030411ab62c7f7` |
| Canonical SRTP corpus inventory | `3ebdced5bf0dcdf99829e1ec2241022018ea352985d1e5757a261f14de42680d` |

The corpus hash is calculated from a compact UTF-8 JSON array sorted by relative
path. Each record has `path`, `sha256` and `bytes` fields, with keys sorted and
compact separators. The manifest specifies the same method. No symlinks are
permitted.

## Primary source references

- [fdroidserver 2.4.6 scanner implementation](https://github.com/f-droid/fdroidserver/blob/2.4.6/fdroidserver/scanner.py): source classifications, VCS exclusions and automatic removals.
- [fdroidserver 2.4.6 build implementation](https://github.com/f-droid/fdroidserver/blob/2.4.6/fdroidserver/build.py): application-directory source-scan scope.
- [fdroidserver 2.4.6 common configuration](https://github.com/f-droid/fdroidserver/blob/2.4.6/fdroidserver/common.py): default configuration and option initialization.
- [Pinned SDK Android presets](https://github.com/BelledonneCommunications/linphone-sdk/blob/ce38159e55897c695b7095019b5a1ff612bb181f/CMakePresets.json).
- [Pinned SDK feature options](https://github.com/BelledonneCommunications/linphone-sdk/blob/ce38159e55897c695b7095019b5a1ff612bb181f/cmake/Options.cmake).
- [Pinned SDK project selection](https://github.com/BelledonneCommunications/linphone-sdk/blob/ce38159e55897c695b7095019b5a1ff612bb181f/cmake/BCProjects.cmake).
