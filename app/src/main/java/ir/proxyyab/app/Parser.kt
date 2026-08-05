package ir.proxyyab.app

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.net.URI

object Parser {
    private val schemeRegex = Regex("(?i)(?:https?://t\\.me/proxy\\?[^\\s<>\"']+|tg://proxy\\?[^\\s<>\"']+|(?:vmess|vless|trojan|ss)://[^\\s<>\"']+)")

    fun extract(text: String, source: String): List<Candidate> {
        val expanded = maybeSubscription(text)
        return schemeRegex.findAll(text + "\n" + expanded)
            .mapNotNull { parse(it.value.replace("&amp;", "&"), source) }
            .distinctBy { it.uri }
            .toList()
    }

    private fun maybeSubscription(input: String): String = try {
        val compact = input.trim().replace("\n", "")
        if (compact.length > 24 && compact.matches(Regex("[A-Za-z0-9_+/=-]+"))) {
            String(Base64.decode(compact.replace('-', '+').replace('_', '/'), Base64.DEFAULT))
        } else ""
    } catch (_: Exception) { "" }

    fun parse(raw: String, source: String): Candidate? = try {
        when {
            raw.startsWith("tg://", true) || raw.startsWith("http://t.me/proxy", true) || raw.startsWith("https://t.me/proxy", true) -> {
                val u = Uri.parse(raw)
                val host = u.getQueryParameter("server")
                val port = u.getQueryParameter("port")?.toIntOrNull()
                if (host.isNullOrBlank() || port == null || u.getQueryParameter("secret").isNullOrBlank()) null
                else Candidate(raw, Kind.TELEGRAM, host, port, source)
            }
            raw.startsWith("vmess://", true) -> parseVmess(raw, source)
            else -> {
                val u = URI(raw)
                val kind = when (u.scheme.lowercase()) {
                    "vless" -> Kind.VLESS; "trojan" -> Kind.TROJAN; "ss" -> Kind.SHADOWSOCKS; else -> Kind.UNKNOWN
                }
                val authority = requireNotNull(u.rawAuthority).substringAfterLast('@')
                val host = authority.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
                val port = authority.substringAfterLast(':').toIntOrNull()
                if (host.isBlank() || port == null) null else Candidate(raw, kind, host, port, source)
            }
        }
    } catch (_: Exception) { null }

    private fun parseVmess(raw: String, source: String): Candidate? = try {
        val b64 = raw.substringAfter("vmess://").substringBefore('#')
        val json = String(Base64.decode(b64.replace('-', '+').replace('_', '/'), Base64.DEFAULT))
        val o = JSONObject(json)
        val host = o.optString("add")
        val port = o.optString("port").toIntOrNull() ?: o.optInt("port").takeIf { it > 0 }
        if (host.isBlank() || port == null) null else Candidate(raw, Kind.VMESS, host, port, source)
    } catch (_: Exception) { null }
}
