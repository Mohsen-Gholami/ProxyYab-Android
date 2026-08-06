package ir.proxyyab.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var repo: Repository
    private lateinit var status: TextView
    private lateinit var sections: LinearLayout
    private var current = emptyList<Candidate>()

    private data class Group(val title: String, val subtitle: String, val icon: String, val accepts: (Kind) -> Boolean)
    private val groups = listOf(
        Group("پروکسی تلگرام", "MTProto و SOCKS", "✈") { it == Kind.TELEGRAM },
        Group("کانفیگ V2Ray", "VLESS، VMess، Trojan و Shadowsocks", "◆") { it in setOf(Kind.VMESS, Kind.VLESS, Kind.TROJAN, Kind.SHADOWSOCKS) },
        Group("NapsternetV", "فایل‌های NPVT", "N") { it == Kind.NPVT },
        Group("OpenVPN", "فایل‌های OVPN", "O") { it == Kind.OPENVPN },
        Group("WireGuard", "لینک یا فایل کانفیگ", "W") { it == Kind.WIREGUARD },
        Group("SlipNet", "فایل‌های SlipNet", "S") { it == Kind.SLIPNET },
        Group("سایر پروتکل‌ها", "Hysteria، TUIC و موارد دیگر", "+") { it in setOf(Kind.OTHER, Kind.UNKNOWN) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = Repository(this)
        setContentView(buildScreen())
        showResults(repo.cached())
        schedule()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(10))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(246, 249, 248))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply {
            text = "پروکسی‌یاب\nاتصال‌های سالم، مرتب و سریع"
            textSize = 22f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(18, 57, 50)); gravity = Gravity.RIGHT
        }
        val info = Button(this).apply { text = "درباره"; setOnClickListener { aboutDialog() } }
        top.addView(brand, LinearLayout.LayoutParams(0, dp(70), 1f)); top.addView(info, LinearLayout.LayoutParams(dp(92), dp(52)))
        status = TextView(this).apply { textSize = 13f; setTextColor(Color.DKGRAY); gravity = Gravity.RIGHT; setPadding(0, dp(6), 0, dp(12)) }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val refresh = Button(this).apply { text = "بررسی و به‌روزرسانی"; setOnClickListener { refresh() } }
        val sources = Button(this).apply { text = "مدیریت منابع"; setOnClickListener { sourceDialog() } }
        actions.addView(refresh, LinearLayout.LayoutParams(0, dp(52), 1f)); actions.addView(sources, LinearLayout.LayoutParams(0, dp(52), 1f))
        sections = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(20)) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(sections) }
        root.addView(top); root.addView(status); root.addView(actions); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun showResults(items: List<Candidate>) {
        current = items.filter { it.reachable }
        sections.removeAllViews()
        groups.forEach { group ->
            val results = current.filter { group.accepts(it.kind) }.take(30)
            sections.addView(section(group, results))
        }
        status.text = if (current.isEmpty()) "برای دریافت کانفیگ‌های سالم، «بررسی و به‌روزرسانی» را بزنید." else "${current.size} مورد قابل استفاده • حداکثر ۳۰ نتیجه در هر بخش"
    }

    private fun section(group: Group, items: List<Candidate>): View {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(Color.WHITE, 22f, Color.rgb(224, 232, 229)); layoutParams = marginParams() }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16)) }
        val arrow = TextView(this).apply { text = "⌄"; textSize = 24f; setTextColor(Color.rgb(20, 108, 91)) }
        val labels = TextView(this).apply { text = "${group.title}  (${items.size})\n${group.subtitle}"; textSize = 15f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.RIGHT; setTextColor(Color.rgb(25, 55, 49)) }
        val icon = TextView(this).apply { text = group.icon; gravity = Gravity.CENTER; textSize = 18f; setTextColor(Color.WHITE); background = rounded(Color.rgb(20, 108, 91), 16f) }
        header.addView(arrow, LinearLayout.LayoutParams(dp(38), dp(52))); header.addView(labels, LinearLayout.LayoutParams(0, dp(58), 1f)); header.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(dp(10), 0, dp(10), dp(12)) }
        if (items.isEmpty()) body.addView(TextView(this).apply { text = "هنوز موردی پیدا نشده است."; gravity = Gravity.CENTER; setPadding(dp(12)); setTextColor(Color.GRAY) })
        items.forEachIndexed { index, item -> body.addView(resultRow(index + 1, item)) }
        header.setOnClickListener { val open = body.visibility != View.VISIBLE; body.visibility = if (open) View.VISIBLE else View.GONE; arrow.text = if (open) "⌃" else "⌄" }
        card.addView(header); card.addView(body)
        return card
    }

    private fun resultRow(number: Int, item: Candidate): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(11), dp(14), dp(11)); background = rounded(Color.rgb(247, 250, 249), 15f); layoutParams = marginParams(4)
        val kindName = when (item.kind) { Kind.TELEGRAM -> "Telegram"; Kind.NPVT -> "NPVT"; Kind.OPENVPN -> "OVPN"; Kind.WIREGUARD -> "WireGuard"; Kind.SLIPNET -> "SlipNet"; else -> item.kind.name }
        addView(TextView(this@MainActivity).apply { text = "$number.  ● سالم  •  $kindName"; textSize = 14f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(17, 113, 82)); gravity = Gravity.RIGHT })
        addView(TextView(this@MainActivity).apply { text = item.host?.let { "$it:${item.port}  •  ${item.latencyMs ?: "—"} ms" } ?: "برای بازکردن یا ایمپورت لمس کنید"; textSize = 12f; gravity = Gravity.RIGHT; setTextColor(Color.DKGRAY })
        setOnClickListener { open(item) }
    }

    private fun refresh() {
        status.text = "در حال دریافت و سنجش منابع…"
        lifecycleScope.launch {
            try { showResults(repo.refresh()) }
            catch (_: Exception) { status.text = "به‌روزرسانی ناموفق بود؛ نتایج قبلی حفظ شد." }
        }
    }

    private fun sourceDialog() {
        val input = EditText(this).apply { minLines = 8; gravity = Gravity.TOP or Gravity.RIGHT; hint = "هر خط یک نشانی عمومی"; setText(repo.sources().joinToString("\n")) }
        AlertDialog.Builder(this).setTitle("مدیریت منابع").setMessage("منابع پیش‌فرض و منابع دستی در کنار هم بررسی می‌شوند.").setView(input)
            .setPositiveButton("ذخیره و بررسی") { _, _ -> repo.saveSources(input.text.toString()); refresh() }.setNegativeButton("انصراف", null).show()
    }

    private fun aboutDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22)) }
        box.addView(TextView(this).apply { text = "پروکسی‌یاب\nنسخه ۱.۱.۰\n\nطراح و توسعه‌دهنده: محسن غلامی\nوب‌سایت: taminit.com"; textSize = 16f; gravity = Gravity.RIGHT; setTextColor(Color.rgb(22, 54, 48)) })
        box.addView(Button(this).apply { text = "بازکردن وب‌سایت"; setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://taminit.com"))) } })
        box.addView(Button(this).apply { text = "ارسال تیکت پشتیبانی"; setOnClickListener { ticketDialog() } })
        AlertDialog.Builder(this).setTitle("درباره برنامه").setView(box).setPositiveButton("بستن", null).show()
    }

    private fun ticketDialog() {
        val code = Random.nextInt(100, 1000).toString()
        val name = EditText(this).apply { hint = "نام و نام خانوادگی" }
        val subject = EditText(this).apply { hint = "موضوع تیکت" }
        val message = EditText(this).apply { hint = "شرح درخواست"; minLines = 4; gravity = Gravity.TOP or Gravity.RIGHT }
        val captcha = EditText(this).apply { hint = "کد امنیتی $code را وارد کنید"; inputType = InputType.TYPE_CLASS_NUMBER }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18)); addView(name); addView(subject); addView(message); addView(captcha) }
        val dialog = AlertDialog.Builder(this).setTitle("پشتیبانی").setMessage("پس از تأیید کد، برنامه ایمیل گوشی برای ارسال تیکت باز می‌شود.").setView(box).setPositiveButton("ادامه", null).setNegativeButton("انصراف", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (name.text.isBlank() || subject.text.isBlank() || message.text.isBlank()) { Toast.makeText(this, "همه فیلدها را تکمیل کنید.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (captcha.text.toString() != code) { captcha.error = "کد امنیتی صحیح نیست"; return@setOnClickListener }
            val body = "نام: ${name.text}\nنسخه برنامه: 1.1.0\n\n${message.text}"
            val uri = Uri.parse("mailto:gholami.m@gmail.com?subject=${Uri.encode("تیکت پروکسی‌یاب: ${subject.text}")}&body=${Uri.encode(body)}")
            try { startActivity(Intent(Intent.ACTION_SENDTO, uri)); dialog.dismiss() } catch (_: ActivityNotFoundException) { Toast.makeText(this, "برنامه ایمیل روی گوشی پیدا نشد.", Toast.LENGTH_LONG).show() }
        } }
        dialog.show()
    }

    private fun open(c: Candidate) {
        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(c.uri)), "باز کردن با…")) }
        catch (_: ActivityNotFoundException) {
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("config", c.uri))
            Toast.makeText(this, "برنامه سازگار پیدا نشد؛ لینک کپی شد.", Toast.LENGTH_LONG).show()
        }
    }

    private fun schedule() {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("refresh", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private fun rounded(color: Int, radius: Float, stroke: Int? = null) = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = dp(radius.toInt()).toFloat(); stroke?.let { setStroke(dp(1), it) } }
    private fun marginParams(vertical: Int = 7) = ViewGroup.MarginLayoutParams(-1, -2).apply { setMargins(0, dp(vertical), 0, dp(vertical)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
