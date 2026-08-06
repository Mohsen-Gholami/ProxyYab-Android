package ir.proxyyab.app

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.net.URI

object Parser {
    private val schemeRegex = Regex("(?i)(?:https?://t\\.me/(?:proxy|socks)\\?[^\\s<>\"']+|tg://(?:proxy|socks)\\?[^\\s<>\"']+|(?:vmess|vless|trojan|ss|wireguard|wg|hysteria2?|hy2|tuic)://[^\\s<>\"']+)")
    private val documentRegex = Regex("""<a\b(?=[^>]*document_wrap)(?=[^>]*href="([^"]+)")[^>]*>.*?</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val fileRegex = Regex("""(?i)https?://[^\s<>"']+\.(?:npvt|ovpn|conf|slip|snet)(?:\?[^\s<>"']*)?""")

    fun extract(text: String, source: String): List<Candidate> {
        val expanded = maybeSubscription(text)
        val configs = schemeRegex.findAll(text + "\n" + expanded)
            .mapNotNull { parse(it.value.replace("&amp;", "&"), source) }
            .toList()
        val documentFiles = documentRegex.findAll(text)
            .filter { match -> listOf(".npvt", ".ovpn", ".conf", ".slip", ".snet").any { match.value.contains(it, true) } }
            .map { it.groupValues[1].replace("&amp;", "&") }
        val directFiles = fileRegex.findAll(text).map { it.value.replace("&amp;", "&") }
        val files = (documentFiles + directFiles).map { fileCandidate(it, source) }.toList()
        return (configs + files).distinctBy { it.uri }
    }

    private fun fileCandidate(uri: String, source: String): Candidate = Candidate(
        uri = uri,
        kind = when {
            uri.contains(".npvt", true) -> Kind.NPVT
            uri.contains(".ovpn", true) -> Kind.OPENVPN
            uri.contains(".slip", true) || uri.contains(".snet", true) -> Kind.SLIPNET
            uri.contains(".conf", true) -> Kind.WIREGUARD
            else -> Kind.OTHER
        },
        host = null, port = null, source = source, reachable = true
    )

    private fun maybeSubscription(input: String): String = try {
        val compact = input.trim().replace("\n", "")
        if (compact.length > 24 && compact.matches(Regex("[A-Za-z0-9_+/=-]+"))) {
            String(Base64.decode(compact.replace('-', '+').replace('_', '/'), Base64.DEFAULT))
        } else ""
    } catch (_: Exception) { "" }

    fun parse(raw: String, source: String): Candidate? = try {
        when {
            raw.startsWith("tg://", true) || raw.startsWith("http://t.me/proxy", true) || raw.startsWith("https://t.me/proxy", true) || raw.startsWith("http://t.me/socks", true) || raw.startsWith("https://t.me/socks", true) -> {
                val u = Uri.parse(raw)
                val host = u.getQueryParameter("server")
                val port = u.getQueryParameter("port")?.toIntOrNull()
                val isMtproto = raw.contains("/proxy?", true) || raw.contains("://proxy?", true)
                val credentialOk = if (isMtproto) !u.getQueryParameter("secret").isNullOrBlank() else true
                if (host.isNullOrBlank() || port == null || !credentialOk) null else Candidate(raw, Kind.TELEGRAM, host, port, source)
            }
            raw.startsWith("vmess://", true) -> parseVmess(raw, source)
            raw.startsWith("wireguard://", true) || raw.startsWith("wg://", true) -> Candidate(raw, Kind.WIREGUARD, null, null, source, reachable = true)
            raw.startsWith("hysteria://", true) || raw.startsWith("hysteria2://", true) || raw.startsWith("hy2://", true) || raw.startsWith("tuic://", true) -> Candidate(raw, Kind.OTHER, null, null, source, reachable = true)
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
