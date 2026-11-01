package com.pulseplayer.app

/** Search hints, not a certification of ownership or licensing. */
object MovieSearchPolicy {
    private val genres = listOf("", "action", "comedy", "horror", "romance", "drama", "family", "documentary")

    fun query(provider: String, genreIndex: Int, yearIndex: Int, officialOnly: Boolean, currentYear: Int): String {
        require(provider == "Movie Melayu" || provider == "Movie Indonesia")
        require(genreIndex in genres.indices)
        require(yearIndex in 0..5)
        require(currentYear in 2000..9999)
        val words = mutableListOf(
            if (provider == "Movie Melayu") "filem melayu" else "film indonesia",
            if (provider == "Movie Melayu") "filem penuh" else "film full movie"
        )
        if (genres[genreIndex].isNotEmpty()) words.add(genres[genreIndex])
        when (yearIndex) {
            1 -> words.add(currentYear.toString())
            2 -> words.add((currentYear - 1).toString())
            3 -> words.add("2020 2021 2022 2023 2024")
            4 -> words.add("2010 2011 2012 2013 2014 2015 2016 2017 2018 2019")
            5 -> words.add("classic old")
        }
        if (officialOnly) words.add(if (provider == "Movie Melayu") "saluran rasmi official" else "channel resmi official")
        return words.joinToString(" ")
    }
}
