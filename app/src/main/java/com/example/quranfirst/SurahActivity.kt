package com.example.quranfirst

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.io.InputStream

data class Surah(
    val id: Int,
    val nameEn: String,
    val nameAr: String,
    val revelation: String,
    val versesCount: Int,
    val startPage: Int,
    val endPage: Int
)

class SurahActivity : AppCompatActivity() {

    private lateinit var rvSurah: RecyclerView
    private val surahList = mutableListOf<Surah>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surah)

        rvSurah = findViewById(R.id.rv_surah)
        rvSurah.layoutManager = LinearLayoutManager(this)

        loadSurahData()
        rvSurah.adapter = SurahAdapter(surahList)
    }

    private fun loadSurahData() {
        try {
            val inputStream: InputStream = assets.open("quran/index.json")
            val json = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(json)
            val chapters = jsonObject.getJSONArray("chapters")

            for (i in 0 until chapters.length()) {
                val chapter = chapters.getJSONObject(i)
                val pagesArray = chapter.getJSONArray("pages")
                val startPage = pagesArray.getInt(0)
                val endPage = pagesArray.optInt(1, startPage)
                surahList.add(
                    Surah(
                        id = chapter.getInt("id"),
                        nameEn = chapter.getString("name"),
                        nameAr = chapter.getString("name_arabic"),
                        revelation = chapter.getString("revelation_place"),
                        versesCount = chapter.getInt("verses_count"),
                        startPage = startPage,
                        endPage = endPage
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    class SurahAdapter(private val list: List<Surah>) :
        RecyclerView.Adapter<SurahAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val number: TextView = view.findViewById(R.id.surah_number)
            val nameEn: TextView = view.findViewById(R.id.surah_name_en)
            val nameAr: TextView = view.findViewById(R.id.surah_name_ar)
            val info: TextView = view.findViewById(R.id.surah_info)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_surah, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val surah = list[position]
            holder.number.text = surah.id.toString()
            holder.nameEn.text = surah.nameEn
            holder.nameAr.text = surah.nameAr
            holder.info.text =
                "${surah.revelation.replaceFirstChar { it.uppercase() }} - ${surah.versesCount} Verses"

            holder.itemView.setOnClickListener {
                val intent = Intent(holder.itemView.context, ReadQuranActivity::class.java).apply {
                    putExtra("SURAH_ID", surah.id)
                    putExtra("SURAH_NAME_AR", surah.nameAr)
                    putExtra("START_PAGE", surah.startPage)
                    putExtra("END_PAGE", surah.endPage)
                }
                holder.itemView.context.startActivity(intent)
            }
        }

        override fun getItemCount() = list.size
    }
}
