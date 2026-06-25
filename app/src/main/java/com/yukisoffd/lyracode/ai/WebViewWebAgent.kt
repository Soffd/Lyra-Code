package com.yukisoffd.lyracode.ai

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.yukisoffd.lyracode.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String = "",
    val score: Int = 0,
    val reason: String = "",
)

data class WebPageResult(
    val title: String,
    val url: String,
    val text: String,
)

class WebViewWebAgent(
    context: Context,
    private val settings: AppSettings,
) {
    private val context = context
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 6): String {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotBlank()) { context.getString(com.yukisoffd.lyracode.R.string.error_search_keyword_empty) }
        val maxResults = limit.coerceIn(1, 10)
        val engines = listOf(
            SearchEngine("Google", "https://www.google.com/search?hl=zh-CN&q=${Uri.encode(cleanQuery)}"),
            SearchEngine("Bing", "https://www.bing.com/search?q=${Uri.encode(cleanQuery)}"),
            SearchEngine(context.getString(com.yukisoffd.lyracode.R.string.search_engine_bing), "https://cn.bing.com/search?q=${Uri.encode(cleanQuery)}"),
            SearchEngine(context.getString(com.yukisoffd.lyracode.R.string.search_engine_baidu), "https://www.baidu.com/s?wd=${Uri.encode(cleanQuery)}"),
        )
        var lastError = ""
        val results = (withTimeoutOrNull(18_000L) { httpSearch(engines, cleanQuery, maxResults * 3) } ?: emptyList())
            .takeIf { it.isNotEmpty() }
            ?: engines.firstNotNullOfOrNull { engine ->
                runCatching {
                    val json = loadAndEvaluate(url = engine.url, script = searchScript(maxResults), timeoutMs = 4_000L)
                    rankSearchResults(cleanQuery, parseSearchResults(json), maxResults).takeIf { it.isNotEmpty() }
                }.onFailure {
                    lastError = "${engine.name}: ${it::class.java.simpleName}: ${it.message.orEmpty()}"
                    Log.w(TAG, "WebView search failed: ${engine.url}", it)
                }.getOrNull()
            }.orEmpty()
        val blockedHosts = settings.webSearchBlockedHosts()
        if (results.isEmpty()) {
            val blockedNote = if (blockedHosts.isNotEmpty()) context.getString(com.yukisoffd.lyracode.R.string.notice_blocked_domains, blockedHosts.joinToString(", ")) else ""
            return "${context.getString(com.yukisoffd.lyracode.R.string.search_no_results)}$blockedNote lastError=$lastError"
        }
        return buildString {
            appendLine("WEB_SEARCH_RESULTS schema=lyra_web_search_v2")
            appendLine("query: $cleanQuery")
            if (blockedHosts.isNotEmpty()) appendLine("blocked_hosts: ${blockedHosts.joinToString(", ")}")
            appendLine("guidance: ${context.getString(com.yukisoffd.lyracode.R.string.search_guidance)}")
            results.take(maxResults).forEachIndexed { index, result ->
                appendLine()
                appendLine("result_${index + 1}:")
                appendLine("title: ${result.title}")
                appendLine("url: ${result.url}")
                appendLine("source: ${result.source}")
                appendLine("score: ${result.score}")
                appendLine("reason: ${result.reason}")
                appendLine("snippet: ${result.snippet.take(500)}")
            }
        }.trim()
    }

    private suspend fun httpSearch(engines: List<SearchEngine>, query: String, limit: Int): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<WebSearchResult>()
        for (engine in engines) {
            runCatching {
                val results = when (engine.name) {
                    "Bing" -> parseBingRss(httpGet("https://www.bing.com/search?q=${Uri.encode(query)}&format=rss"), limit)
                        .ifEmpty { parseLinksFromHtml(httpGet(engine.url), limit) }
                    context.getString(com.yukisoffd.lyracode.R.string.search_engine_bing) -> parseBingRss(httpGet("https://cn.bing.com/search?q=${Uri.encode(query)}&format=rss"), limit)
                        .ifEmpty { parseLinksFromHtml(httpGet(engine.url), limit) }
                    else -> parseLinksFromHtml(httpGet(engine.url), limit)
                }
                collected += results.map { it.copy(source = engine.name) }
            }.onFailure {
                Log.w(TAG, "HTTP search failed: ${engine.name}", it)
            }
            if (collected.size >= limit) break
        }
        rankSearchResults(query, collected, limit)
    }

    suspend fun readPage(url: String): String {
        val cleanUrl = url.trim()
        require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) { context.getString(com.yukisoffd.lyracode.R.string.error_only_http_url) }
        blockedHostFor(cleanUrl)?.let { blocked ->
            return "WEB_PAGE_READ_RESULT schema=lyra_web_page_v2\nstatus: blocked_by_user\ntitle: \nurl: $cleanUrl\nnote: ${context.getString(com.yukisoffd.lyracode.R.string.web_page_blocked, cleanUrl, blocked)}\n\n页面未读取。"
        }
        var page = runCatching {
            val json = loadAndEvaluate(url = cleanUrl, script = pageScript(), timeoutMs = 10_000L)
            parsePage(json)
        }.onFailure {
            Log.w(TAG, "WebView read page failed: $cleanUrl", it)
        }.getOrElse {
            withTimeoutOrNull(8_000L) { httpReadFallback(cleanUrl) }
                ?: WebPageResult("", cleanUrl, context.getString(com.yukisoffd.lyracode.R.string.web_page_timeout))
        }
        if (pageReadStatus(page.text) != "readable") {
            val fallback = withTimeoutOrNull(8_000L) { runCatching { httpReadFallback(cleanUrl) }.getOrNull() }
            if (fallback != null && pageReadStatus(fallback.text) == "readable") page = fallback
        }
        val status = pageReadStatus(page.text)
        val note = when (status) {
            "blocked_or_dynamic" -> "note: ${context.getString(com.yukisoffd.lyracode.R.string.web_page_blocked_dynamic)}"
            "limited" -> "note: ${context.getString(com.yukisoffd.lyracode.R.string.web_page_limited)}"
            else -> "note: ${context.getString(com.yukisoffd.lyracode.R.string.web_page_ok)}"
        }
        return "WEB_PAGE_READ_RESULT schema=lyra_web_page_v2\nstatus: $status\ntitle: ${page.title}\nurl: ${page.url}\n$note\n\n${page.text.ifBlank { context.getString(com.yukisoffd.lyracode.R.string.web_page_no_text) }}"
    }

    private fun rankSearchResults(query: String, results: List<WebSearchResult>, limit: Int): List<WebSearchResult> {
        val tokens = queryTokens(query)
        val seen = mutableSetOf<String>()
        return results.asSequence()
            .mapNotNull { result ->
                val url = canonicalSearchUrl(result.url)
                if (url.isBlank() || isSearchEngineUrl(url)) return@mapNotNull null
                if (isBlockedUrl(url)) return@mapNotNull null
                if (!seen.add(url)) return@mapNotNull null
                val title = result.title.trim().take(200)
                if (title.length < 2) return@mapNotNull null
                val scored = scoreSearchResult(tokens, result.copy(title = title, url = url))
                result.copy(title = title, url = url, score = scored.first, reason = scored.second)
            }
            .sortedWith(compareByDescending<WebSearchResult> { it.score }.thenBy { it.title.length })
            .take(limit)
            .toList()
    }

    private fun scoreSearchResult(tokens: List<String>, result: WebSearchResult): Pair<Int, String> {
        val host = runCatching { Uri.parse(result.url).host.orEmpty().lowercase() }.getOrDefault("")
        val path = runCatching { Uri.parse(result.url).path.orEmpty().lowercase() }.getOrDefault("")
        val haystack = "${result.title} ${result.snippet} $host $path".lowercase()
        val matched = tokens.count { token -> token.length >= 2 && haystack.contains(token) }
        var score = 20 + matched * 12
        val reasons = mutableListOf<String>()
        if (matched > 0) reasons += context.getString(com.yukisoffd.lyracode.R.string.search_keyword_match, matched, tokens.size.coerceAtLeast(1))
        if (hostQualityBonus(host, path) > 0) {
            val bonus = hostQualityBonus(host, path)
            score += bonus
            reasons += context.getString(com.yukisoffd.lyracode.R.string.search_source_trusted, bonus)
        }
        val penalty = lowQualityPenalty(host, result.title, result.snippet)
        if (penalty > 0) {
            score -= penalty
            reasons += context.getString(com.yukisoffd.lyracode.R.string.search_low_quality, penalty)
        }
        if (tokens.isNotEmpty() && matched == 0) {
            score -= 20
            reasons += "标题摘要与关键词弱相关"
        }
        return score to reasons.ifEmpty { listOf("普通候选结果") }.joinToString("；")
    }

    private fun hostQualityBonus(host: String, path: String): Int {
        return when {
            host.endsWith(".gov") || host.contains(".gov.") -> 35
            host.endsWith(".edu") || host.contains(".edu.") -> 25
            host.contains("github.com") || host.contains("gitlab.com") -> 20
            host.contains("developer.") || host.contains("docs.") || path.contains("/docs") || path.contains("/documentation") -> 20
            host.contains("wikipedia.org") -> 12
            else -> 0
        }
    }

    private fun lowQualityPenalty(host: String, title: String, snippet: String): Int {
        val text = "$title $snippet".lowercase()
        var penalty = 0
        val noisyHosts = listOf(
            "baijiahao.baidu.com",
            "m.sm.cn",
            "so.com",
            "pinterest.",
            "facebook.com",
            "instagram.com",
            "tiktok.com",
            "x.com",
            "twitter.com",
        )
        if (noisyHosts.any { host.contains(it) }) penalty += 35
        if (listOf("广告", "推广", "最新地址", "转载", "采集", "seo", "站长").any { text.contains(it) }) penalty += 18
        if (title.length > 90 && snippet.isBlank()) penalty += 10
        return penalty
    }

    private fun queryTokens(query: String): List<String> {
        val clean = query.lowercase()
        val tokens = Regex("""[a-z0-9][a-z0-9_.+-]{1,}|[\u4E00-\u9FFF]{2,}""")
            .findAll(clean)
            .map { it.value.trim() }
            .filter { it.length >= 2 }
            .distinct()
            .toMutableList()
        clean.split(Regex("""[\s,，。；;:：/\\|"'“”‘’()（）\[\]{}<>《》]+"""))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .forEach { if (it !in tokens) tokens += it }
        Regex("""[\u4E00-\u9FFF]{4,}""").findAll(clean).forEach { match ->
            val value = match.value
            value.windowed(2, 1).take(8).forEach { if (it !in tokens) tokens += it }
        }
        return tokens.take(12)
    }

    private fun canonicalSearchUrl(url: String): String {
        val parsed = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return ""
        val scheme = parsed.scheme.orEmpty().lowercase()
        if (scheme != "http" && scheme != "https") return ""
        val host = parsed.host.orEmpty().lowercase()
        if (host.isBlank()) return ""
        val path = parsed.path.orEmpty().ifBlank { "/" }
        val query = parsed.queryParameterNames
            .filterNot { it.lowercase() in TRACKING_QUERY_PARAMS }
            .sorted()
            .joinToString("&") { name ->
                val value = parsed.getQueryParameter(name).orEmpty()
                "${Uri.encode(name)}=${Uri.encode(value)}"
            }
        return buildString {
            append(scheme).append("://").append(host).append(path)
            if (query.isNotBlank()) append("?").append(query)
        }.trimEnd('/')
    }

    private fun pageReadStatus(text: String): String {
        val clean = text.trim()
        val lower = clean.lowercase()
        val blockedSignals = listOf(
            "access denied",
            "forbidden",
            "403",
            "cloudflare",
            "just a moment",
            "verify you are human",
            "enable javascript",
            "请完成安全验证",
            "人机验证",
            "登录后查看",
            "网页读取超时",
        )
        return when {
            blockedSignals.any { lower.contains(it) } -> "blocked_or_dynamic"
            clean.length < 600 -> "limited"
            else -> "readable"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadAndEvaluate(url: String, script: String, timeoutMs: Long): String = withContext(Dispatchers.Main) {
        withTimeout(timeoutMs) {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.loadsImagesAutomatically = false
            webView.settings.blockNetworkImage = true
            try {
                suspendCancellableCoroutine { continuation ->
                    var evaluated = false
                    fun evaluate(view: WebView) {
                        if (evaluated || !continuation.isActive) return
                        evaluated = true
                        view.postDelayed({
                            view.evaluateJavascript(script) { value ->
                                Log.d(TAG, "Loaded $url, resultChars=${value?.length ?: 0}")
                                if (continuation.isActive) continuation.resume(value.orEmpty())
                            }
                        }, 400L)
                    }
                    continuation.invokeOnCancellation { webView.destroy() }
                    webView.webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            if (newProgress >= 100) evaluate(view)
                        }
                    }
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

                        override fun onPageFinished(view: WebView, loadedUrl: String) {
                            evaluate(view)
                        }
                    }
                    Log.d(TAG, "Loading $url")
                    webView.loadUrl(url)
                }.also {
                    webView.destroy()
                }
            } catch (error: Throwable) {
                webView.destroy()
                throw error
            }
        }
    }

    private fun searchScript(limit: Int): String = """
        (function() {
            const anchors = Array.from(document.querySelectorAll('a'));
            const rows = [];
            const seen = new Set();
            for (const a of anchors) {
                let href = a.href || '';
                let title = (a.innerText || a.textContent || '').replace(/\s+/g, ' ').trim();
                if (!href || !title || title.length < 2) continue;
                try {
                    const parsed = new URL(href);
                    const uddg = parsed.searchParams.get('uddg');
                    if (uddg) href = decodeURIComponent(uddg);
                    const googleTarget = parsed.searchParams.get('q');
                    if (parsed.hostname.includes('google.') && googleTarget && /^https?:\/\//i.test(googleTarget)) href = googleTarget;
                    const baiduTarget = parsed.searchParams.get('url');
                    if (parsed.hostname.includes('baidu.') && baiduTarget && /^https?:\/\//i.test(baiduTarget)) href = baiduTarget;
                } catch (error) {}
                if (!/^https?:\/\//i.test(href)) continue;
                if (isSearchEngineUrl(href)) continue;
                if (seen.has(href)) continue;
                seen.add(href);
                let parent = a.closest('div, article, li') || a.parentElement;
                let snippet = parent ? (parent.innerText || '').replace(/\s+/g, ' ').trim() : '';
                if (snippet.startsWith(title)) snippet = snippet.slice(title.length).trim();
                rows.push({ title, url: href, snippet });
                if (rows.length >= $limit) break;
            }
            function isSearchEngineUrl(url) {
                try {
                    const parsed = new URL(url);
                    const host = parsed.hostname;
                    const path = parsed.pathname;
                    return (host.includes('google.') && path.startsWith('/search')) ||
                        (host.includes('bing.com') && path.startsWith('/search')) ||
                        (host.includes('baidu.com') && (path === '/s' || path.startsWith('/s?')));
                } catch (error) {
                    return false;
                }
            }
            return rows;
        })()
    """.trimIndent()

    private fun pageScript(): String = """
        (function() {
            const blocked = ['script', 'style', 'noscript', 'svg', 'canvas', 'iframe', 'nav', 'footer'];
            const clone = document.body ? document.body.cloneNode(true) : document.documentElement.cloneNode(true);
            for (const tag of blocked) {
                clone.querySelectorAll(tag).forEach(function(node) { node.remove(); });
            }
            const text = (clone.innerText || clone.textContent || '').replace(/\n{3,}/g, '\n\n').replace(/[ \t]{2,}/g, ' ').trim();
            return {
                title: document.title || '',
                url: location.href,
                text: text.slice(0, 20000)
            };
        })()
    """.trimIndent()

    private suspend fun httpReadFallback(url: String): WebPageResult = withContext(Dispatchers.IO) {
        val html = httpGet(url)
        val title = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.htmlToText()
            .orEmpty()
        WebPageResult(title, url, htmlToPlainText(html).take(20_000))
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    private fun parseLinksFromHtml(html: String, limit: Int): List<WebSearchResult> {
        val seen = mutableSetOf<String>()
        val anchorRegex = Regex("""<a\b[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return anchorRegex.findAll(html).mapNotNull { match ->
            var url = match.groupValues[1].htmlToText()
            val title = match.groupValues[2].htmlToText()
            if (url.startsWith("/l/?")) {
                Uri.parse("https://duckduckgo.com$url").getQueryParameter("uddg")?.let { url = it }
            }
            Uri.parse(url).getQueryParameter("q")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { url = it }
            Uri.parse(url).getQueryParameter("url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { url = it }
            if (url.startsWith("/url?")) {
                Uri.parse("https://www.google.com$url").getQueryParameter("q")?.let { url = it }
            }
            if (url.startsWith("/link?")) {
                Uri.parse("https://www.baidu.com$url").getQueryParameter("url")?.let { url = it }
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) return@mapNotNull null
            if (isSearchEngineUrl(url)) return@mapNotNull null
            if (isBlockedUrl(url)) return@mapNotNull null
            if (title.length < 2 || !seen.add(url)) return@mapNotNull null
            WebSearchResult(title = title.take(200), url = url, snippet = "")
        }.take(limit).toList()
    }

    private fun parseBingRss(xml: String, limit: Int): List<WebSearchResult> {
        val itemRegex = Regex("""<item>(.*?)</item>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return itemRegex.findAll(xml).mapNotNull { match ->
            val item = match.groupValues[1]
            val title = item.xmlTag("title").htmlToText()
            val link = item.xmlTag("link").htmlToText()
            val snippet = item.xmlTag("description").htmlToText()
            if (title.isBlank() || !link.startsWith("http")) return@mapNotNull null
            if (isBlockedUrl(link)) return@mapNotNull null
            WebSearchResult(title = title.take(200), url = link, snippet = snippet.take(500))
        }.take(limit).toList()
    }

    private fun isBlockedUrl(url: String): Boolean = blockedHostFor(url) != null

    private fun blockedHostFor(url: String): String? {
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
            .lowercase()
            .trim('.')
        if (host.isBlank()) return null
        return settings.webSearchBlockedHosts().firstOrNull { blocked ->
            if (blocked.startsWith("*.")) {
                val suffix = blocked.removePrefix("*.").trim('.')
                suffix.isNotBlank() && host != suffix && host.endsWith(".$suffix")
            } else {
                host == blocked
            }
        }
    }

    private fun isSearchEngineUrl(url: String): Boolean {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = parsed.host.orEmpty()
        val path = parsed.path.orEmpty()
        return (host.contains("google.") && path.startsWith("/search")) ||
            (host.contains("bing.com") && path.startsWith("/search")) ||
            (host.contains("baidu.com") && path == "/s")
    }

    private fun parseSearchResults(raw: String): List<WebSearchResult> {
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    WebSearchResult(
                        title = item.optString("title"),
                        url = item.optString("url"),
                        snippet = item.optString("snippet"),
                    ),
                )
            }
        }
    }

    private fun parsePage(raw: String): WebPageResult {
        val root = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        return WebPageResult(
            title = root.optString("title"),
            url = root.optString("url"),
            text = root.optString("text"),
        )
    }

    private fun htmlToPlainText(html: String): String {
        return html
            .replace(Regex("""<script\b[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<style\b[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<br\s*/?>|</p>|</div>|</li>|</h[1-6]>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), " ")
            .htmlToText()
            .replace(Regex("[ \t]{2,}"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun String.htmlToText(): String {
        return replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.xmlTag(name: String): String {
        return Regex("""<$name[^>]*>(.*?)</$name>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .removePrefix("<![CDATA[")
            .removeSuffix("]]>")
    }

    companion object {
        private const val TAG = "LyraWebAgent"
        private val TRACKING_QUERY_PARAMS = setOf(
            "utm_source",
            "utm_medium",
            "utm_campaign",
            "utm_term",
            "utm_content",
            "spm",
            "from",
            "fbclid",
            "gclid",
        )
    }
}

private data class SearchEngine(
    val name: String,
    val url: String,
)
