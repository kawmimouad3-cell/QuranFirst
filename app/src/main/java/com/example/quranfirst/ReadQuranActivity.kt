package com.example.quranfirst

import android.content.res.AssetManager
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.io.InputStream

data class QuranLine(
    val text: String,
    val fontName: String,
    val type: String
)

data class QuranPage(
    val pageNumber: Int,
    val lines: List<QuranLine>
)

class ReadQuranActivity : AppCompatActivity() {

    private lateinit var rvPages: RecyclerView
    private val snapHelper = PagerSnapHelper()
    private val pages = mutableListOf<QuranPage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_quran)

        rvPages = findViewById(R.id.rv_pages)
        // Use standard LTR and reverse populate to simulate an Arabic book layout
        rvPages.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        rvPages.layoutDirection = View.LAYOUT_DIRECTION_LTR
        rvPages.overScrollMode = View.OVER_SCROLL_NEVER
        snapHelper.attachToRecyclerView(rvPages)

        val startPage = intent.getIntExtra("START_PAGE", 1).coerceIn(1, 604)
        val endPage = intent.getIntExtra("END_PAGE", startPage).coerceIn(startPage, 604)

        // Load pages in reverse order so swiping right (finger moving left-to-right) goes to next Arabic page
        for (pageNumber in endPage downTo startPage) {
            pages.add(loadPageData(pageNumber))
        }

        rvPages.adapter = QuranPageAdapter(pages)
        // Scroll to the end of the list where the start page is located
        rvPages.scrollToPosition(pages.size - 1)
    }

    private fun loadPageData(pageNumber: Int): QuranPage {
        val pageLines = mutableListOf<QuranLine>()

        try {
            val fileName = String.format("quran/pages/%03d.json", pageNumber)
            val inputStream: InputStream = assets.open(fileName)
            val json = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(json)
            val pageFontName = jsonObject.getString("font")
            val linesArray = jsonObject.getJSONArray("lines")

            for (i in 0 until linesArray.length()) {
                val lineObj = linesArray.getJSONObject(i)
                val wordsArray = lineObj.getJSONArray("words")
                if (wordsArray.length() == 0) continue

                val firstWord = wordsArray.getJSONObject(0)
                val fontName = firstWord.optString("font", pageFontName)
                val lineType = firstWord.optString("type", "word")
                val lineText = StringBuilder()

                // Reverse the word order since Android treats PUA characters as LTR
                for (j in wordsArray.length() - 1 downTo 0) {
                    val word = wordsArray.getJSONObject(j)
                    lineText.append(word.getString("char"))
                }

                pageLines.add(QuranLine(lineText.toString(), fontName, lineType))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return QuranPage(pageNumber, pageLines)
    }

    class QuranPageAdapter(private val list: List<QuranPage>) :
        RecyclerView.Adapter<QuranPageAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val linesContainer: LinearLayout = view.findViewById(R.id.page_lines)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_quran_page, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val page = list[position]
            holder.linesContainer.removeAllViews()

            for (line in page.lines) {
                holder.linesContainer.addView(createLineView(holder.linesContainer, line))
            }
        }

        override fun getItemCount() = list.size

        private fun createLineView(parent: LinearLayout, line: QuranLine): TextView {
            val maxTextSize = when (line.type) {
                "surah_header" -> 31
                "bismillah" -> 29
                "quarter" -> 23
                else -> 28
            }

            return TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    lineWeight(line.type)
                )
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                maxLines = 1
                text = line.text
                textDirection = View.TEXT_DIRECTION_RTL
                textSize = maxTextSize.toFloat()
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                typeface = QuranTypefaceCache.get(context.assets, line.fontName) ?: Typeface.DEFAULT
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this,
                    15,
                    maxTextSize,
                    1,
                    TypedValue.COMPLEX_UNIT_SP
                )
            }
        }

        private fun lineWeight(type: String): Float {
            return when (type) {
                "surah_header" -> 1.25f
                "bismillah" -> 1.15f
                else -> 1f
            }
        }
    }

    private object QuranTypefaceCache {
        private val cache = mutableMapOf<String, Typeface?>()

        fun get(assets: AssetManager, fontName: String): Typeface? {
            return cache.getOrPut(fontName) {
                val candidates = if (fontName == "QCF4_QBSML") {
                    listOf("fonts/$fontName.ttf")
                } else {
                    listOf("fonts/$fontName.ttf", "fonts/${fontName}_W.ttf")
                }

                candidates.firstNotNullOfOrNull { path ->
                    try {
                        Typeface.createFromAsset(assets, path)
                    } catch (e: RuntimeException) {
                        null
                    }
                }
            }
        }
    }
}
