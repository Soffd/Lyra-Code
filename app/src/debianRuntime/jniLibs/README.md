# Bundled PRoot runtime

This directory is the repository-local source for the ARM64 PRoot binaries packaged in every APK. Android builds must not download these files from RikkaHub or another remote host.

## Current binary snapshot

The current files were copied from the RikkaHub workspace snapshot at commit `693c2ce53fe28d4eb03517edffd7824f9f99f682`:

- `arm64-v8a/libproot_exec.so`: `d4ffbd19e20614c908be774af5dcd9da306094482f556713db037563c353219c`
- `arm64-v8a/libproot_loader.so`: `44ef39c1e1a18c09f6e4c4b5d6f8bba82d30596598bd155ec162d05c5122ff04`

`app/build.gradle.kts` verifies both SHA-256 values before every build. The `.so` suffix makes Android package and extract these native ELF files; `libproot_exec.so` is launched as an executable and `libproot_loader.so` is supplied through `PROOT_LOADER`.

## Rebuilding from the local fork

The developer checkout keeps the maintained `Soffd/proot` fork at the repository-root path `proot/`. That checkout is intentionally ignored by the Lyra Code repository because it has its own Git history. The PRoot GNUmakefile builds the `proot` executable and the unbundled `loader/loader` artifact. An Android replacement must be cross-compiled for AArch64 with the Android NDK and retain the Termux/Android patches required by this app.

Before replacing the packaged files:

1. Record the exact `Soffd/proot` source commit, Android NDK version, API level, compiler flags, dependencies, and reproducible build command.
2. Build and test the AArch64 `proot` executable with an unbundled ARM64 loader. Do not substitute a host-Linux build.
3. Copy/rename the executable to `arm64-v8a/libproot_exec.so` and the unbundled loader to `arm64-v8a/libproot_loader.so`.
4. Update the two expected hashes in `app/build.gradle.kts`, `LicenseTexts.kt`, `THIRD_PARTY_NOTICES.md`, and this file.
5. Run `./gradlew verifyBundledProotRuntime testDebugUnitTest assembleDebug`, inspect the APK entries, and test rootfs startup, command execution, links, bind mounts, package installation, and terminal sessions on an ARM64 Android device.

Keep the corresponding source and build material available to binary recipients under the selected GPLv3 terms.
