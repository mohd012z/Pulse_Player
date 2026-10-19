package com.pulseplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Responsive Cinema landing page. Honest empty states until licensed catalog is connected. */
class CinemaActivity : Activity() {
    private val bg = 0xFF0B0E18.toInt()
    private val panel = 0xFF20283A.toInt()
    private val accent = 0xFF8C5CF6.toInt()
    private lateinit var body: LinearLayout
    private var selected = "Trending"
    private fun dp(v:Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun tv() = (getSystemService(UI_MODE_SERVICE) as android.app.UiModeManager)
        .currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    private fun text(value:String,size:Float = 16f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.WHITE)
        setPadding(dp(10),dp(8),dp(10),dp(8))
    }
    private fun button(value:String, click:()->Unit) = Button(this).apply {
        text = value; isAllCaps = false; setTextColor(Color.WHITE)
        textSize = if(tv()) 17f else 13f
        minHeight = dp(if(tv()) 60 else 48)
        isFocusable = true; setOnClickListener { click() }
        background = GradientDrawable().apply {
            setColor(panel); cornerRadius = dp(12).toFloat()
            setStroke(dp(1),0xFF364055.toInt())
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        selected = savedInstanceState?.getString("category") ?: "Trending"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg)
            setPadding(dp(12),dp(12),dp(12),dp(12))
        }
        val title = text("▶  PULSE CINEMA",if(tv()) 27f else 21f)
        root.addView(title)
        val scroller = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroller.addView(body)
        root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Home","Movies","ShortTV","Music","Settings").forEach { label ->
            nav.addView(button(label) {
                when(label) {
                    "Music" -> finish()
                    "ShortTV" -> startActivity(Intent(this,StoryHubActivity::class.java))
                    "Settings" -> showSettings()
                    "Movies" -> { selected = "Movies"; render() }
                    else -> { selected = "Trending"; render() }
                }
            },LinearLayout.LayoutParams(0,dp(if(tv()) 64 else 52),1f).apply {
                marginStart=dp(2); marginEnd=dp(2)
            })
        }
        root.addView(nav)
        setContentView(root)
        render()
    }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("category",selected)
        super.onSaveInstanceState(outState)
    }
    private fun render() {
        body.removeAllViews()
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14),dp(18),dp(14),dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF48308A.toInt(),0xFF161C2B.toInt())).apply {
                cornerRadius = dp(18).toFloat()
            }
        }
        hero.addView(text("PULSE CINEMA",if(tv()) 34f else 26f))
        hero.addView(text("Movies · Series · Anime · Short Drama · Novels",14f))
        hero.addView(text("Discover content from approved providers. No licensed online movie catalog is connected yet.",13f))
        hero.addView(button("▶ Open my Short Drama episodes") {
            startActivity(Intent(this,StoryHubActivity::class.java))
        })
        body.addView(hero)
        body.addView(text("Explore  •  $selected",19f))
        val horizontal = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
        val chips = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        PulseCatalogNavigation.visibleCategories().forEach { category ->
            chips.addView(button(category.label) {
                selected=category.label
                render()
            },LinearLayout.LayoutParams(dp(if(tv()) 170 else 122),dp(if(tv()) 66 else 52)).apply {
                marginEnd=dp(6)
            })
        }
        horizontal.addView(chips)
        body.addView(horizontal)
        listOf("Continue Watching","Trending Drama","Movies & TV","Anime","Short Drama","Novels").forEach { section ->
            body.addView(text(section,if(tv()) 24f else 19f))
            val rowScroll=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            val message = if(section=="Continue Watching")
                "Your local episodes are available in Stories"
            else "Connect an authorized catalog to show titles"
            row.addView(button(message) {
                if(section=="Continue Watching" || section=="Short Drama" || section=="Novels")
                    startActivity(Intent(this,StoryHubActivity::class.java))
                else AlertDialog.Builder(this).setTitle(section)
                    .setMessage("This catalog has no authorized provider connected. No placeholder posters or fake Play links are displayed.")
                    .setPositiveButton("OK",null).show()
            },LinearLayout.LayoutParams(dp(if(tv()) 430 else 280),dp(if(tv()) 150 else 112)))
            rowScroll.addView(row)
            body.addView(rowScroll)
        }
    }
    private fun showSettings() {
        val modes = arrayOf("Standard (PG-13 / TV-14)","Family Friendly (PG / TV-PG)")
        val prefs=getSharedPreferences("pulse_catalog",MODE_PRIVATE)
        val selectedMode=if(prefs.getString("rating_mode","STANDARD")=="FAMILY_FRIENDLY") 1 else 0
        AlertDialog.Builder(this).setTitle("Content rating")
            .setSingleChoiceItems(modes,selectedMode) { dialog,index ->
                prefs.edit().putString("rating_mode",if(index==1) "FAMILY_FRIENDLY" else "STANDARD").apply()
                dialog.dismiss()
            }.setNegativeButton("Cancel",null).show()
    }
}
