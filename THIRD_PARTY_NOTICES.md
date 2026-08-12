# Third-Party Notices

This document summarizes the main third-party components used by Lyra Code. It is provided for release compliance review and does not replace the original license files or notices shipped by each component.

## Runtime / Application Dependencies

| Component | License | Notes |
| --- | --- | --- |
| Android Gradle Plugin | Apache License 2.0 | Build tool. |
| Kotlin / Kotlin Gradle tooling | Apache License 2.0 | Language and build tooling. |
| AndroidX Core KTX | Apache License 2.0 | Runtime Android support library. |
| AndroidX Activity Compose | Apache License 2.0 | Compose Activity integration. |
| Jetpack Compose UI / Material 3 / Icons Extended | Apache License 2.0 | UI framework and icons. |
| AndroidX DocumentFile | Apache License 2.0 | Storage Access Framework helper. |
| AndroidX Security Crypto | Apache License 2.0 | Encrypted preferences. |
| Kotlinx Coroutines Android | Apache License 2.0 | Coroutine runtime. |
| OkHttp | Apache License 2.0 | HTTP client. |
| Android Mail / Jakarta Mail API for Android 1.6.7 (`com.sun.mail:android-mail`) | Eclipse Public License 2.0 | Controls Lyra Code's IMAP message reading, folder and draft management, SMTP sending, and MIME processing. The packaged JAR retains its `META-INF/LICENSE.md` and `META-INF/NOTICE.md`. |
| Jakarta Activation fork for Android (`com.sun.mail:android-activation`) | Eclipse Public License 2.0 | MIME data handlers used by mail attachments. The packaged JAR retains its license and notice resources. |
| mwiede JSch | BSD/ISC-style licenses; includes bundled license files | SSH client. Check upstream `LICENSE.txt`, `LICENSE.JZlib.txt`, and `LICENSE.jBCrypt.txt`. |
| JetBrains Markdown / RikkaHub fork | Apache License 2.0 | Markdown parser/rendering support. |
| Sora Editor (`io.github.rosemoe:editor`, `language-textmate`) | GNU LGPL 2.1 or later | Android code editor, line numbers, search, wrapping, and TextMate integration. Preserve the LGPL notice and allow replacement/relinking of the library as required by the license. |
| Sora Editor demo TextMate themes and language bundles | Upstream component licenses, primarily MIT-style; distributed by the Sora Editor project | Syntax/theme assets for Java, Kotlin, Python, JavaScript, HTML, XML, Markdown, and Lua were copied from the official Sora Editor demo assets. Retain upstream notices when redistributing source assets. |
| JLaTeXMath Android / local fork | GPL v2 or later with a special linking exception; distributed under GPL v3 for this combination | LaTeX rendering. Its independent-module linking exception and GPL v2-or-later grant permit combination with Lyra Code under AGPL v3. Keep `third_party/jlatexmath/LICENSE`, `COPYING`, `src/main/assets/org/scilab/forge/jlatexmath/licences/GPL-3.0.txt`, and the font licenses with distributions. |
| Bundled JLaTeXMath core fonts | SIL OFL 1.1, Knuth/Computer Modern terms, permissive terms, or Public Domain, depending on the font | The optional upstream `jlatexmath-font-greek` module and its GPL-2.0-only fonts are excluded from this repository and every build. See `third_party/jlatexmath/LICENSE` and `third_party/jlatexmath/src/main/assets/org/scilab/forge/jlatexmath/fonts/licences/`. |

## Test-Only Dependencies

| Component | License | Notes |
| --- | --- | --- |
| JUnit | Eclipse Public License 1.0 | Test dependency. |
| AndroidX Test / Espresso | Apache License 2.0 | Android instrumentation tests. |
| org.json JSON-java | JSON License | Declared as `testImplementation`; not intended to be packaged in the Android app. The JSON License contains the well-known "Good, not Evil" use restriction, so avoid shipping it in AGPL builds unless reviewed separately. |

## Compliance Notes

- Apache License 2.0 dependencies can generally be combined into GPLv3/AGPLv3 works, but keep required notices and license texts.
- The local JLaTeXMath module is the main copyleft-sensitive component. Its local `LICENSE` says GPL v2 or later and includes a special linking exception for independent modules; Lyra Code distributes the library under GPL v3 when combining it with the AGPL-v3 application. Preserve the upstream notices and exception when modifying it.
- Do not add the optional upstream `jlatexmath-font-greek` module or any of `jlm_fcmbipg.ttf`, `jlm_fcmripg.ttf`, `jlm_fcmrpg.ttf`, `jlm_fcsropg.ttf`, `jlm_fcmbpg.ttf`, `jlm_fcsbpg.ttf`, `jlm_fctrpg.ttf`, or `jlm_fcsrpg.ttf`. Those fonts are GPL-2.0-only and are rejected by the JLaTeXMath pre-build verification task.
- Lyra Code original code is licensed only under AGPL-3.0. Distributors must also comply with all applicable third-party licenses or replace those components with separately licensed alternatives.
