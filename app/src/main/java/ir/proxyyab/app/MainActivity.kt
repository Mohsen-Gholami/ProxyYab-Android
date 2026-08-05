package ir.proxyyab.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var repo: Repository; private lateinit var adapter: ProxyAdapter; private lateinit var status: TextView
    override fun onCreate(b: Bundle?) { super.onCreate(b); repo=Repository(this)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,24,20,10); layoutDirection=android.view.View.LAYOUT_DIRECTION_RTL }
        val title=TextView(this).apply { text="پروکسی‌یاب"; textSize=28f; gravity=Gravity.RIGHT }
        status=TextView(this).apply { text="نتایج ذخیره‌شده"; gravity=Gravity.RIGHT; setPadding(0,8,0,12) }
        val bar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val refresh=Button(this).apply { text="بررسی الآن"; setOnClickListener { refresh() } }
        val sources=Button(this).apply { text="مدیریت منابع"; setOnClickListener { sourceDialog() } }
        bar.addView(refresh,LinearLayout.LayoutParams(0,-2,1f)); bar.addView(sources,LinearLayout.LayoutParams(0,-2,1f))
        val rv=RecyclerView(this).apply { layoutManager=LinearLayoutManager(this@MainActivity) }
        adapter=ProxyAdapter(::open); rv.adapter=adapter
        root.addView(title); root.addView(status); root.addView(bar); root.addView(rv,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
        adapter.submit(repo.cached()); schedule()
        if(repo.sources().isEmpty()) sourceDialog()
    }
    private fun refresh(){ status.text="در حال دریافت و سنجش…"; lifecycleScope.launch { try { val x=repo.refresh(); adapter.submit(x.filter{it.reachable}); status.text="${x.count{it.reachable}} مورد قابل‌دسترسی از ${x.size} کانفیگ" } catch (_: Exception) { status.text="دریافت ناموفق بود؛ نتایج ذخیره‌شده باقی ماند" } } }
    private fun sourceDialog(){ val input=EditText(this).apply { minLines=8; gravity=Gravity.TOP or Gravity.RIGHT; hint="هر خط یک نشانی عمومی: صفحه t.me/s/...، فایل Raw گیت‌هاب یا Subscription"; setText(repo.sources().joinToString("\n")) }
        AlertDialog.Builder(this).setTitle("منابع عمومی").setMessage("فقط منابع قابل‌اعتماد خودتان را اضافه کنید. محتوای کانفیگ می‌تواند توسط صاحب سرور مشاهده یا دستکاری شود.").setView(input).setPositiveButton("ذخیره"){_,_->repo.saveSources(input.text.toString());refresh()}.setNegativeButton("انصراف",null).show() }
    private fun open(c:Candidate){ try { val uri=if(c.kind==Kind.TELEGRAM && c.uri.startsWith("https://t.me")) c.uri else c.uri; startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(uri)),"باز کردن با…")) } catch(_:ActivityNotFoundException){ Toast.makeText(this,"برنامه سازگار نصب نیست؛ لینک در کلیپ‌بورد کپی شد",Toast.LENGTH_LONG).show(); (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("config",c.uri)) } }
    private fun schedule(){ val req=PeriodicWorkRequestBuilder<RefreshWorker>(6,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build(); WorkManager.getInstance(this).enqueueUniquePeriodicWork("refresh",ExistingPeriodicWorkPolicy.UPDATE,req) }
}
