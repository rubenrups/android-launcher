package com.rubenrups.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLearningTest {
    private fun picks(vararg steps: Pair<String, String>): Map<String, Map<String, Int>> =
        steps.fold(emptyMap()) { acc, (query, app) -> SearchLearning.record(acc, query, app) }

    @Test
    fun mostChosenAppRanksFirstForThatQuery() {
        val p = picks("mail" to "gmail", "mail" to "gmail", "mail" to "outlook")
        val ranked = SearchLearning.rank(listOf("outlook", "gmail", "proton"), { it }, p, "mail")
        assertEquals(listOf("gmail", "outlook", "proton"), ranked)
    }

    @Test
    fun queriesAreCaseAndSpaceInsensitive() {
        val p = picks("  Mail " to "gmail")
        assertTrue(SearchLearning.score(p, "MAIL", "gmail") > 0)
    }

    @Test
    fun otherQueriesDoNotAffectTheRanking() {
        val p = picks("music" to "spotify")
        assertEquals(0, SearchLearning.score(p, "mail", "spotify"))
    }

    @Test
    fun aLongerRememberedQueryHelpsAShorterPrefixLessThanAnExactOne() {
        val p = picks("spotify" to "spotify")
        val exactPick = picks("spo" to "other")
        assertTrue(SearchLearning.score(p, "spo", "spotify") > 0)
        assertTrue(SearchLearning.score(exactPick, "spo", "other") > SearchLearning.score(p, "spo", "spotify"))
    }

    @Test
    fun ties_keepTheirOriginalOrder() {
        val ranked = SearchLearning.rank(listOf("b", "a", "c"), { it }, picks("x" to "z"), "x")
        assertEquals(listOf("b", "a", "c"), ranked)
    }

    @Test
    fun theTableStaysBounded() {
        var p: Map<String, Map<String, Int>> = emptyMap()
        repeat(SearchLearning.MAX_QUERIES + 50) { p = SearchLearning.record(p, "q$it", "app") }
        assertEquals(SearchLearning.MAX_QUERIES, p.size)
        assertTrue("q0" !in p)
        repeat(SearchLearning.MAX_APPS_PER_QUERY + 5) { p = SearchLearning.record(p, "many", "app$it") }
        assertEquals(SearchLearning.MAX_APPS_PER_QUERY, p.getValue("many").size)
    }
}
