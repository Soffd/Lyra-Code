# App-internal PRoot Linux environments

Lyra Code ships one APK and keeps the external-Termux `run_command` integration unchanged. Every APK includes only the small arm64 PRoot engine; Linux root filesystems are optional user-managed data.

## Installation and coexistence

From **Settings > PRoot Linux**, users can:

- download the pinned Debian Trixie arm64 seed over HTTPS with SHA-256 verification;
- import compatible arm64 `tar.gz`, `tgz`, or uncompressed tar rootfs archives containing `/bin/sh` or `/bin/bash`;
- keep multiple Debian, Ubuntu, Alpine, or other PRoot-compatible installations side by side;
- enable or disable an environment without deleting it;
- permanently delete an environment after an explicit warning.

Imported installations live under `files/proot-linux/instances/<linux-id>/rootfs`. The display name is converted into a stable, unique Linux ID and stored in `metadata.json`. The earlier Debian test runtime at `files/debian-runtime/rootfs` is automatically exposed as Linux ID `debian` without moving or rewriting it.

Archive extraction rejects traversal outside the staging directory. An import must contain a shell, and ELF shells are checked for AArch64 compatibility. Archives with one wrapping top-level directory are normalized automatically.

## Finding compatible rootfs archives

An ISO is boot or installation media and normally contains a kernel, bootloader, installer, and sometimes a compressed filesystem image. It is not a directly importable PRoot rootfs.

- Alpine Linux: use the official Downloads page and choose **Mini root filesystem → aarch64 → `.tar.gz`**. Do not choose Standard, Extended, Virtual, or other ISO variants: <https://www.alpinelinux.org/downloads/>
- Ubuntu: use the official Ubuntu Base releases and choose a file named like `ubuntu-base-<version>-base-arm64.tar.gz`: <https://cdimage.ubuntu.com/ubuntu-base/releases/>
- Arch Linux ARM: use its official downloads page and select an ARMv8/AArch64 generic root filesystem tarball: <https://archlinuxarm.org/about/downloads>
- Termux PRoot Distro is a useful compatibility and image-reference guide, but not every artifact or OCI image it supports is directly accepted by Lyra Code's simpler tar importer: <https://github.com/termux/proot-distro>

Architecture labels `arm64` and `aarch64` mean the same CPU family for this importer. `amd64` and `x86_64` are not compatible because Lyra Code does not bundle QEMU user-mode emulation.

## User-data ownership

Each rootfs is mutable user data. Application updates never replace it based on seed versions or metadata. Disabling only hides the environment from the terminal and Agent. Deletion removes the complete rootfs, including installed packages, user files, and system configuration, and therefore requires a destructive-action confirmation.

Android app uninstall or clear-data still removes every app-private rootfs. Rootfs directories are excluded from Android cloud backup and device transfer because they can grow to several gigabytes; portable environments need a future explicit export/import archive flow.

## PRoot source and license

The bundled ARM64 PRoot executable and loader are GPL-2.0-or-later. Lyra Code exercises the later-version option and distributes them under GPLv3 alongside the AGPL-v3 application. Corresponding source is maintained at <https://github.com/Soffd/proot>; its upstream chain is `Soffd/proot` → `termux/proot` → `proot-me/proot`. The binary distribution snapshot and both SHA-256 values are recorded in the in-app **Open Source Licenses** page and in `THIRD_PARTY_NOTICES.md`, together with the complete GPLv3 terms.

The two verified ARM64 ELF files are stored in `app/src/debianRuntime/jniLibs/arm64-v8a` and are consumed directly by the Android build. Builds do not contact RikkaHub and fail if either local file is missing or its fixed SHA-256 value changes. See the adjacent `jniLibs/README.md` for provenance and the checklist for replacing them with a cross-compiled build from the local `Soffd/proot` checkout.

## Agent and terminal behavior

The Agent receives `proot_command` only while at least one environment is enabled. Every call specifies `linux_id`, and the Agent prompt lists the currently enabled IDs and display names. When a directly accessible workspace is selected it is mounted at `/workspace`; without a workspace, commands remain available and start in `/root`. The command runs through the selected environment's `/bin/bash -lc` or `/bin/sh -lc`.

When Android's All files access permission is granted, Android shared storage is mounted read/write under `/storage`, and primary storage is also available through `/sdcard`. `workDir` accepts paths in those mounts, absolute paths inside the Linux rootfs, and workspace-relative paths when a workspace exists. Without All files access, PRoot exposes only the selected workspace and Linux-internal files. Existing terminal processes must reconnect after the permission changes because bind mounts are fixed when PRoot starts. Android app-UID permissions and SELinux still apply, so this does not grant root access or access to other apps' private data.

The terminal uses the same transport-neutral screen for SSH and local Linux. Each enabled PRoot environment appears as a separate target and owns an app-long session keyed by Linux ID. Deleting or disabling an environment first closes its terminal session.

## Limitations

- The packaged PRoot engine currently supports `arm64-v8a` only; PRoot does not emulate another CPU architecture.
- XZ/Zstandard archives are not currently accepted; decompress them to tar first or provide a gzip tarball.
- A document-provider-only SAF workspace cannot currently be bind-mounted by PRoot.
- PRoot is compatibility tooling, not a VM, Android sandbox, or security boundary. Processes run with Lyra Code's Android permissions.

See `THIRD_PARTY_NOTICES.md` before distributing builds.
