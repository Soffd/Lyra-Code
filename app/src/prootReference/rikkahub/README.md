# RikkaHub PRoot recovery binaries

These files are retained as an unpackaged recovery reference. Gradle does not
include this source-set directory in Lyra Code APKs.

PRoot copyright remains with STMicroelectronics and its contributors. These
object-code files are GPL-2.0-or-later; Lyra's distribution selects GPLv3. The
corresponding-source chain is Soffd/proot, termux/proot, and proot-me/proot.

Source snapshot:
https://github.com/rikkahub/rikkahub/tree/693c2ce53fe28d4eb03517edffd7824f9f99f682/workspace/src/main/jniLibs/arm64-v8a

| File | SHA-256 |
| --- | --- |
| `arm64-v8a/libproot_exec.so` | `d4ffbd19e20614c908be774af5dcd9da306094482f556713db037563c353219c` |
| `arm64-v8a/libproot_loader.so` | `44ef39c1e1a18c09f6e4c4b5d6f8bba82d30596598bd155ec162d05c5122ff04` |

They must not be copied back into `debianRuntime/jniLibs` without also restoring
the matching hashes and provenance notice.
