package ir.proxyyab.app

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProxyAdapter(private val open: (Candidate) -> Unit) : RecyclerView.Adapter<ProxyAdapter.H>() {
    private var data = listOf<Candidate>()
    fun submit(items: List<Candidate>) { data = items; notifyDataSetChanged() }
    class H(val root: LinearLayout, val title: TextView, val detail: TextView) : RecyclerView.ViewHolder(root)
    override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
        val c=p.context; val root=LinearLayout(c).apply { orientation=LinearLayout.VERTICAL; setPadding(32,24,32,24); gravity=Gravity.RIGHT; background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=24f; setStroke(1,Color.LTGRAY) } }
        val t=TextView(c).apply { textSize=16f; setTextColor(Color.rgb(25,45,40)); gravity=Gravity.RIGHT }
        val d=TextView(c).apply { textSize=12f; setTextColor(Color.DKGRAY); gravity=Gravity.RIGHT }
        root.addView(t); root.addView(d); root.layoutParams=ViewGroup.MarginLayoutParams(-1,-2).apply { setMargins(16,10,16,10) }
        return H(root,t,d)
    }
    override fun getItemCount()=data.size
    override fun onBindViewHolder(h:H,i:Int) { val x=data[i]; h.title.text=if(x.kind==Kind.NPVT) "فایل NPVT • بازکردن در تلگرام" else "${if(x.reachable) "● سالم" else "○ ناموفق"}  •  ${x.kind}"; h.detail.text=if(x.kind==Kind.NPVT) "منبع: ${x.source.substringAfterLast('/')}" else "${x.host}:${x.port}  |  ${x.latencyMs?.let{"$it ms"}?:"بدون پاسخ"}"; h.root.setOnClickListener { open(x) } }
}
