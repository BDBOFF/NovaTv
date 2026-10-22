package com.nova.tv.data

/** Genre TMDB, avec son identifiant côté films et côté séries. */
data class Genre(val key: String, val name: String, val movieId: Int?, val tvId: Int?, val match: Regex)

object Explore {
    val genres = listOf(
        Genre("action", "Action", 28, 10759, Regex("(?i)action")),
        Genre("aventure", "Aventure", 12, 10759, Regex("(?i)aventure|adventure")),
        Genre("comedie", "Comédie", 35, 35, Regex("(?i)com[ée]die|comedy")),
        Genre("drame", "Drame", 18, 18, Regex("(?i)drame|drama")),
        Genre("thriller", "Thriller", 53, 80, Regex("(?i)thriller|suspense")),
        Genre("horreur", "Horreur", 27, 9648, Regex("(?i)horreur|horror|épouvante")),
        Genre("sf", "Science-Fiction", 878, 10765, Regex("(?i)science|sci-?fi|fantas")),
        Genre("animation", "Animation", 16, 16, Regex("(?i)animation|anim[ée]")),
        Genre("famille", "Famille", 10751, 10762, Regex("(?i)famil|enfant|kids")),
        Genre("crime", "Crime", 80, 80, Regex("(?i)crime|policier|criminel")),
        Genre("romance", "Romance", 10749, null, Regex("(?i)romance|romantique")),
        Genre("fantastique", "Fantastique", 14, 10765, Regex("(?i)fantastique|fantasy")),
        Genre("guerre", "Guerre", 10752, 10768, Regex("(?i)guerre|war")),
        Genre("histoire", "Histoire", 36, null, Regex("(?i)histoire|history|historique")),
        Genre("documentaire", "Documentaire", 99, 99, Regex("(?i)documentaire|documentary")),
        Genre("western", "Western", 37, 37, Regex("(?i)western")),
    )

    /** Sagas recherchées sur TMDB puis croisées avec le catalogue. */
    val sagaQueries = listOf(
        "Avengers", "Iron Man", "Captain America", "Thor", "Les Gardiens de la Galaxie", "Spider-Man",
        "Black Panther", "Deadpool", "X-Men", "The Dark Knight", "Star Wars", "Harry Potter",
        "Les Animaux fantastiques", "Le Seigneur des anneaux", "Le Hobbit", "Fast & Furious",
        "Mission impossible", "James Bond", "John Wick", "Jurassic Park", "Transformers",
        "Pirates des Caraïbes", "Matrix", "Hunger Games", "Twilight", "Indiana Jones",
        "Retour vers le futur", "Rocky", "Creed", "Terminator", "Alien", "Die Hard", "Expendables",
        "Toy Story", "Shrek", "Moi, moche et méchant", "L'Âge de glace", "Kung Fu Panda",
        "Madagascar", "Dragons", "Cars", "La Reine des neiges", "Taxi", "Astérix", "Les Visiteurs",
        "Saw", "Scream", "Conjuring", "Destination finale", "Insidious", "La Planète des singes",
        "Dune", "Avatar", "Godzilla", "Jason Bourne", "Equalizer", "Bad Boys", "Men in Black",
        "Ocean's", "Sherlock Holmes", "Kingsman", "Venom", "Halloween", "Le Labyrinthe",
        "Divergente", "Bienvenue chez les Ch'tis", "OSS 117", "Les Tuche", "Babysitting",
    )

    private val sportCat = Regex(
        "(?i)sport|foot|ligue|dazn|bein|eurosport|rmc|rugby|nba|\\bf1\\b|golf|tennis|uefa|premier league|liga|serie a|bundesliga|equipe"
    )
    private val matchTitle = Regex(
        "(?i)( / | - | vs\\.? | v\\. |\\bmatch\\b|ligue 1|ligue 2|ligue des champions|champions league|europa|conference league|" +
            "premier league|liga|serie a|bundesliga|top 14|pro d2|six nations|coupe|nba|nfl|nhl|grand prix|formule 1|\\bf1\\b|" +
            "motogp|roland|wimbledon|\\bopen\\b|ufc|boxe|rugby|football|basket|handball|cyclisme|tour de france|biathlon|ski)"
    )
    private val notMatch = Regex(
        "(?i)(zap|journal|magazine|talk|late|before|after|replay|r[ée]sum[ée]|best of|l'[ée]quipe du soir|club|" +
            "t[ée]l[ée]achat|infos|jt\\b|mag\\b|documentaire|programme|fin des programmes|intégrale|rediffusion)"
    )

    fun isSportCategory(name: String) = sportCat.containsMatchIn(name)

    fun looksLikeMatch(title: String): Boolean =
        matchTitle.containsMatchIn(" $title ") && !notMatch.containsMatchIn(title)
}
