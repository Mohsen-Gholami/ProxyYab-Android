package ir.proxyyab.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class Repository(private val context: Context) {
    private val prefs = context.getSharedPreferences("proxy_yab", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).followRedirects(true).build()

    fun sources(): List<String> = prefs.getString("sources", "")!!.lines().map { it.trim() }.filter { it.startsWith("http") }
    fun saveSources(value: String) = prefs.edit().putString("sources", value).apply()

    suspend fun refresh(): List<Candidate> = withContext(Dispatchers.IO) {
        val all = coroutineScope {
            sources().map { original ->
                async {
                    val url = normalizeSourceUrl(original)
                    fetch(url)?.let { Parser.extract(it, original) }.orEmpty()
                }
            }.awaitAll().flatten()
        }.distinctBy { it.uri }
        val gate = Semaphore(32)
        val checked = coroutineScope { all.take(500).map { async { gate.withPermit { check(it) } } }.awaitAll() }
            .sortedWith(compareByDescending<Candidate> { it.reachable }.thenBy { it.latencyMs ?: Long.MAX_VALUE })
        saveCache(checked)
        checked
    }

    private fun fetch(url: String): String? = try {
        client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 ProxyYab/1.0").build()).execute().use {
            if (it.isSuccessful) it.body?.string() else null
        }
    } catch (_: Exception) { null }

    /** Telegram's normal channel URL is only a landing page. /s/ exposes public post previews. */
    private fun normalizeSourceUrl(input: String): String {
        val clean = input.trim().removeSuffix("/")
        val match = Regex("(?i)^https?://(?:www\\.)?t\\.me/([A-Za-z0-9_]{4,})$").matchEntire(clean)
        return match?.groupValues?.get(1)?.let { "https://t.me/s/$it" } ?: clean
    }

    private fun check(c: Candidate): Candidate {
        val start = System.nanoTime()
        val ok = try { Socket().use { it.connect(InetSocketAddress(c.host, c.port!!), 3500); true } } catch (_: Exception) { false }
        return c.copy(reachable = ok, latencyMs = if (ok) (System.nanoTime() - start) / 1_000_000 else null, checkedAt = System.currentTimeMillis())
    }

    private fun saveCache(items: List<Candidate>) {
        val a = JSONArray()
        items.forEach { c -> a.put(JSONObject().put("u",c.uri).put("k",c.kind.name).put("h",c.host).put("p",c.port).put("s",c.source).put("l",c.latencyMs).put("r",c.reachable).put("t",c.checkedAt)) }
        prefs.edit().putString("cache", a.toString()).apply()
    }

    fun cached(): List<Candidate> = try {
        val a = JSONArray(prefs.getString("cache", "[]")); (0 until a.length()).map { i ->
            val o=a.getJSONObject(i); Candidate(o.getString("u"),Kind.valueOf(o.getString("k")),o.optString("h").ifBlank{null},o.optInt("p").takeIf{it>0},o.optString("s"),o.optLong("l").takeIf{it>0},o.optBoolean("r"),o.optLong("t"))
        }
    } catch (_: Exception) { emptyList() }
}
