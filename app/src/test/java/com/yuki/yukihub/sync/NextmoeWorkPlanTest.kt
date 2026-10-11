package com.yuki.yukihub.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextmoeWorkPlanTest {
    private fun binding(
        visible: String?,
        mode: String,
        nextmoe: String? = null,
        vndb: String? = null,
        bangumi: String? = null,
    ) = GameBinding(1, visible, mode, nextmoe, vndb, bangumi)

    @Test
    fun aNextmoeCoverTheUserPickedBindsWithoutAnyRequest() {
        val manual = binding("nextmoe", "MANUAL", nextmoe = "100", vndb = "v1")
        assertEquals("100", NextmoeWorkPlan.manualWork(manual))
        assertEquals(WorkDecision.Bound("100"), NextmoeWorkPlan.decide(manual, emptyMap()))
        // The same id picked automatically is only evidence.
        assertNull(NextmoeWorkPlan.manualWork(binding("nextmoe", "AUTO", nextmoe = "100")))
        assertEquals(WorkDecision.Candidate("100"), NextmoeWorkPlan.decide(binding("nextmoe", "AUTO", nextmoe = "100"), emptyMap()))
    }

    @Test
    fun aManualVndbOrBangumiCoverBindsThroughItsOwnRefAlone() {
        val vndb = binding("vndb", "MANUAL", vndb = "v17", bangumi = "302")
        assertEquals("vndb:v17", NextmoeWorkPlan.manualRef(vndb))
        assertEquals(WorkDecision.Bound("200"), NextmoeWorkPlan.decide(vndb, mapOf("vndb:v17" to "200", "bangumi:302" to "999")))
        assertEquals(WorkDecision.Unmatched, NextmoeWorkPlan.decide(vndb, emptyMap()))
        assertEquals("bangumi:302", NextmoeWorkPlan.manualRef(binding("bangumi_mirror", "MANUAL", bangumi = "302")))
    }

    @Test
    fun automaticEvidenceBindsOnlyWhenTwoSourcesAgree() {
        val auto = binding("vndb", "AUTO", vndb = "v17", bangumi = "302")
        assertEquals(listOf("vndb:v17", "bangumi:302"), NextmoeWorkPlan.refs(auto))
        assertEquals(WorkDecision.Bound("200"), NextmoeWorkPlan.decide(auto, mapOf("vndb:v17" to "200", "bangumi:302" to "200")))
        assertEquals(WorkDecision.Candidate("200"), NextmoeWorkPlan.decide(auto, mapOf("vndb:v17" to "200", "bangumi:302" to "201")))
        assertEquals("one resolved ref is not enough", WorkDecision.Candidate("200"), NextmoeWorkPlan.decide(auto, mapOf("vndb:v17" to "200")))
        // An automatically cached NextMoe id counts as the second piece of evidence.
        val withNextmoe = binding("bangumi", "AUTO", nextmoe = "200", vndb = "v17")
        assertEquals(WorkDecision.Bound("200"), NextmoeWorkPlan.decide(withNextmoe, mapOf("vndb:v17" to "200")))
        assertEquals(WorkDecision.Unmatched, NextmoeWorkPlan.decide(binding(null, "NONE"), emptyMap()))
    }
}
