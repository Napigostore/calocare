package com.calocare.app.data

import com.calocare.app.label
import java.text.Normalizer

/**
 * Matches a dish name written by the AI (or typed by the user) to a food the app already knows,
 * so calories come from the app's own table instead of a fresh guess each time.
 */
object FoodMatcher {
    private val marks = Regex("\\p{Mn}+")
    private val separators = Regex("[^a-z0-9]+")

    /** Lower-case, without Vietnamese diacritics or punctuation: "Phở Bò!" → "pho bo". */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase().replace('đ', 'd'), Normalizer.Form.NFD).replace(marks, "").replace(separators, " ").trim()

    /** 1 = same name; high when one name contains the other as whole words; otherwise shared-word overlap. */
    private fun similarity(query: String, name: String): Float {
        if (query == name) return 1f
        val (short, long) = if (query.length <= name.length) query to name else name to query
        if (" $long ".contains(" $short ")) return 0.7f + 0.3f * short.length / long.length
        val q = query.split(' ').toSet()
        val n = name.split(' ').toSet()
        val shared = q.count { it in n }
        return shared.toFloat() / (q.size + n.size - shared)
    }

    /** Foods that share anything with the query, best match first. */
    fun ranked(query: String, foods: List<Food>): List<Pair<Food, Float>> {
        val q = fold(query)
        if (q.isEmpty()) return emptyList()
        return foods.map { it to maxOf(similarity(q, fold(it.name)), similarity(q, fold(it.label))) }.filter { it.second > 0f }.sortedByDescending { it.second }
    }

    /** The food this name most likely means, or null when nothing is close enough to trust. */
    fun best(query: String, foods: List<Food>): Food? = ranked(query, foods).firstOrNull()?.takeIf { it.second >= 0.6f }?.first
}
