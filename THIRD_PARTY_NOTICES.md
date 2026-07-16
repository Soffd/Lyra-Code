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
| mwiede JSch | BSD/ISC-style licenses; includes bundled license files | SSH client. Check upstream `LICENSE.txt`, `LICENSE.JZlib.txt`, and `LICENSE.jBCrypt.txt`. |
| JetBrains Markdown / RikkaHub fork | Apache License 2.0 | Markdown parser/rendering support. |
| Sora Editor (`io.github.rosemoe:editor`, `language-textmate`) | GNU LGPL 2.1 or later | Android code editor, line numbers, search, wrapping, and TextMate integration. Preserve the LGPL notice and allow replacement/relinking of the library as required by the license. |
| Sora Editor demo TextMate themes and language bundles | Upstream component licenses, primarily MIT-style; distributed by the Sora Editor project | Syntax/theme assets for Java, Kotlin, Python, JavaScript, HTML, XML, Markdown, and Lua were copied from the official Sora Editor demo assets. Retain upstream notices when redistributing source assets. |
| JLaTeXMath Android / local fork | GPL v2 or later with a special linking exception; bundled fonts have separate licenses | LaTeX rendering. Keep `third_party/jlatexmath/LICENSE`, `COPYING`, and font licenses with source distributions. |

## Test-Only Dependencies

| Component | License | Notes |
| --- | --- | --- |
| JUnit | Eclipse Public License 1.0 | Test dependency. |
| AndroidX Test / Espresso | Apache License 2.0 | Android instrumentation tests. |
| org.json JSON-java | JSON License | Declared as `testImplementation`; not intended to be packaged in the Android app. The JSON License contains the well-known "Good, not Evil" use restriction, so avoid shipping it in AGPL builds unless reviewed separately. |

## Compliance Notes

- Apache License 2.0 dependencies can generally be combined into GPLv3/AGPLv3 works, but keep required notices and license texts.
- The local JLaTeXMath module is the main copyleft-sensitive component. Its local `LICENSE` says it is GPL v2 or later and includes a special linking exception for independent modules. If you modify that library, preserve its license terms and review whether the exception still applies.
- Commercial licensing of Lyra Code original code does not relicense third-party components. Commercial distributors must still comply with third-party licenses or replace those components with separately licensed alternatives.
