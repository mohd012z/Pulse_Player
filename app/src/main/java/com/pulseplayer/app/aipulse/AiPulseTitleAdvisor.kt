package com.pulseplayer.app.aipulse

/** Original, offline, deterministic songwriting prompts; no model or voice synthesis is implied. */
object AiPulseTitleAdvisor {
    data class Advice(val lyrics: String, val vocalProfile: String, val style: String)

    fun suggest(title: String, idea: String, genre: String, language: String): Advice {
        val name = title.trim().take(80).ifBlank { "Our Story" }
        val theme = idea.trim().take(100).ifBlank { "a new beginning" }
        val vocal = when (genre.lowercase()) {
            "ballad" -> "Warm expressive mid-range vocal, intimate verses and sustained chorus"
            "r&b" -> "Smooth conversational vocal, rhythmic phrasing and layered harmonies"
            "edm" -> "Clear energetic lead vocal with short memorable hooks"
            "acoustic" -> "Natural unprocessed vocal, close-mic intimacy and gentle harmonies"
            else -> "Confident expressive rock-pop vocal, dynamic verses and an open chorus"
        }
        val english = """
            [Intro]
            A quiet spark, a brand-new day
            We find the words we meant to say

            [Verse 1]
            I kept a note inside my mind
            Of all the things we left behind
            Through every turn, through every mile
            I learned to face the truth and smile

            [Pre-Chorus]
            When the road is hard to see
            I remember what matters to me

            [Chorus]
            $name, I hear it in the rain
            A little hope that rises again
            We carry $theme in our hearts
            And make tomorrow a brand-new start

            [Verse 2]
            The city lights begin to glow
            There is so much we have yet to know
            I choose the steps that I can take
            And build a promise I won't break

            [Bridge]
            Let the doubts drift out of sight
            We can learn to make things right
            Every ending leaves a door
            To something worth believing for

            [Final Chorus]
            $name, I hear it in the rain
            A little hope that rises again
            We carry $theme in our hearts
            And make tomorrow a brand-new start

            [Outro]
            A quiet spark, a brand-new day
            We found the words we meant to say
        """.trimIndent()
        val malay = """
            [Intro]
            Pagi datang membawa cahaya
            Langkah baharu membuka cerita

            [Verse 1]
            Ada janji yang masih kuingat
            Ada harapan yang terus mendekat
            Walau jalan berubah arah
            Aku belajar menjadi tabah

            [Pre-Chorus]
            Bila malam terasa panjang
            Kupegang harapan yang datang

            [Chorus]
            $name, dengarlah suara hati
            Kita melangkah sekali lagi
            Tentang $theme, kita belajar
            Membina esok dengan sabar

            [Verse 2]
            Di hujung jalan ada cerita
            Tentang usaha dan cita-cita
            Setiap silap menjadi panduan
            Untuk menunaikan satu tujuan

            [Bridge]
            Biar waktu terus berlalu
            Kita tak perlu menjadi yang dulu
            Dengan jujur dan keberanian
            Kita temui satu harapan

            [Final Chorus]
            $name, dengarlah suara hati
            Kita melangkah sekali lagi
            Tentang $theme, kita belajar
            Membina esok dengan sabar

            [Outro]
            Pagi datang membawa cahaya
            Langkah baharu membuka cerita
        """.trimIndent()
        val lyrics = when (language) {
            "Bahasa Melayu" -> malay
            "BM + English" -> malay.substringBefore("[Verse 2]") + english.substring(english.indexOf("[Verse 2]"))
            else -> english
        }
        return Advice(lyrics, vocal, "$genre · original offline lyric draft; vocals are suggestions only")
    }
}
