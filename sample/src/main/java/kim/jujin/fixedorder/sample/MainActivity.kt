package kim.jujin.fixedorder.sample

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kim.jujin.fixedorder.ColumnPinningStrategy
import kim.jujin.fixedorder.FixedOrderStaggeredGridLayoutManager
import kim.jujin.fixedorder.SpanSizeLookup

class MainActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var layoutManager: FixedOrderStaggeredGridLayoutManager
    private var useThree = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar = findViewById(R.id.toolbar)
        recycler = findViewById(R.id.recycler)

        layoutManager = FixedOrderStaggeredGridLayoutManager(this, 2)
        recycler.layoutManager = layoutManager
        recycler.adapter = SampleAdapter(buildItems())

        layoutManager.setSpanSizeLookup(object : SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when {
                    // Every 9th item is full-span
                    position % 9 == 6 -> layoutManager.getSpanCount()
                    // Every 7th item is double-span when possible
                    position % 7 == 3 -> minOf(2, layoutManager.getSpanCount())
                    else -> 1
                }
            }
        })

        layoutManager.setColumnPinningStrategy(ColumnPinningStrategy { pos ->
            // Pin a few items to column 0 for demo
            if (pos % 11 == 4) 0 else null
        })

        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_toggle_span) {
                useThree = !useThree
                layoutManager.setSpanCount(if (useThree) 3 else 2)
                true
            } else false
        }
    }

    private fun buildItems(): List<Int> {
        return List(120) { i -> 80 + (i % 10) * 25 + if (i % 13 == 0) 120 else 0 }
    }

    private inner class SampleAdapter(private val heights: List<Int>) : RecyclerView.Adapter<VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(8, 8, 8, 8)
                setBackgroundColor(0xFF3F51B5.toInt())
            }
            return VH(tv)
        }
        override fun getItemCount(): Int = heights.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(position, heights[position])
        }
    }

    private inner class VH(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
        fun bind(position: Int, heightPx: Int) {
            tv.text = "#${position}\n${heightPx}px"
            tv.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx,
            ).apply { setMargins(8, 8, 8, 8) }
        }
    }
}

