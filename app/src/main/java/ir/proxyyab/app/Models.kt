package ir.proxyyab.app

enum class Kind { TELEGRAM, VMESS, VLESS, TROJAN, SHADOWSOCKS, UNKNOWN }

data class Candidate(
    val uri: String,
    val kind: Kind,
    val host: String?,
    val port: Int?,
    val source: String,
    val latencyMs: Long? = null,
    val reachable: Boolean = false,
    val checkedAt: Long = 0
) {
    val id: String get() = uri.hashCode().toString()
}
