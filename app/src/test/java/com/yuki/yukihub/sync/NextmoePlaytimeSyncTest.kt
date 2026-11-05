package com.yuki.yukihub.sync

import android.content.SharedPreferences
import com.yuki.yukihub.auth.CoverAccountStatus
import com.yuki.yukihub.metadata.model.VnMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextmoePlaytimeSyncTest {
    private class FakeStore : NextmoePlaytimeStore {
        val minutes = linkedMapOf<Long, Int>()
        val names = mutableMapOf<Long, String>()
        val imported = mutableMapOf<Long, Long>()
        var clouds = emptyList<CloudPlaytimeWork>()
        override fun cloud(account: String) = clouds
        override fun snapshot(account: String, works: List<CloudPlaytimeWork>) { clouds = works }
        override fun undoImport(gameId: Long) { minutes[gameId] = (minutes[gameId] ?: 0) - ((imported.remove(gameId) ?: 0) / 60000).toInt() }
        val bindings = mutableMapOf<Long, GameBinding>()
        val rows = mutableMapOf<Pair<String, Long>, PlaytimeLedgerRow>()

        override fun games(account: String) = libraryGames(account).filter { it.localMinutes >= 1 }

        override fun libraryGames(account: String) = minutes.map { (id, local) -> PlaytimeGame(id, names[id] ?: "game $id", local, rows[account to id], imported[id] ?: 0) }

        override fun raiseLocal(
            gameId: Long,
            minutes: Int,
        ) {
            imported[gameId] = (imported[gameId] ?: 0) + (minutes - (this.minutes[gameId] ?: 0)).coerceAtLeast(0) * 60000L
            this.minutes[gameId] = maxOf(this.minutes[gameId] ?: 0, minutes)
        }

        override fun binding(gameId: Long) = bindings[gameId] ?: GameBinding(gameId, null, "NONE", null, null, null)

        override fun save(
            gameId: Long,
            account: String,
            row: PlaytimeLedgerRow,
        ) {
            rows[account to gameId] = row
        }
    }

    private class FakeRemote : NextmoePlaytimeRemote {
        val remote = mutableMapOf<String, Int>()
        val catalogWorks = mutableListOf<VnMetadata>()
        val resolvedWorks = mutableListOf<VnMetadata>()
        val puts = mutableListOf<Pair<String, Int>>()
        var otherAppMinutes = 0
        override fun catalog(workIds: List<String>) = catalogWorks.filter { it.id in workIds }
        override fun put(workId: String, minutes: Int): PlaytimeWriteResult {
            puts += workId to minutes
            remote[workId] = maxOf(minutes, otherAppMinutes)
            return PlaytimeWriteResult(workId, true)
        }
        val writes = mutableListOf<List<Pair<String, Int>>>()
        val deletes = mutableListOf<String>()
        val refs = mutableMapOf<String, String>()
        val problems = mutableMapOf<String, PlaytimeWriteResult>()
        var failWith: Exception? = null
        var calls = 0

        override fun playtimes(workIds: List<String>): Map<String, Int> {
            calls++
            failWith?.let { throw it }
            return workIds.mapNotNull { id -> remote[id]?.let { id to it } }.toMap()
        }

        override fun allPlaytimes(): Map<String, Int> {
            calls++
            failWith?.let { throw it }
            return remote.toMap()
        }

        override fun write(items: List<Pair<String, Int>>): List<PlaytimeWriteResult> {
            calls++
            writes += items
            return items.map { (work, minutes) ->
                problems[work] ?: PlaytimeWriteResult(work, true).also { remote[work] = minutes }
            }
        }

        override fun delete(workId: String) {
            calls++
            deletes += workId
            remote.remove(workId)
        }

        override fun resolveRefs(refs: List<String>): List<VnMetadata> {
            calls++
            return resolvedWorks.filter { work -> work.externalRefs.split(',').any { it in refs } } + refs.mapNotNull { ref -> this.refs[ref]?.let { work -> VnMetadata(id = work, externalRefs = ref, chineseTitle = "work $work") } }
        }
    }

    private val store = FakeStore()
    private val remote = FakeRemote()
    private var account: CoverAccountStatus? = CoverAccountStatus(connected = true, name = "yuki", id = "sub-1")
    private var now = 1_000L
    private val sync = NextmoePlaytimeSync({ account }, store, remote, MemoryPrefs(), { now })

    private fun manualNextmoe(
        gameId: Long,
        work: String,
    ) {
        store.bindings[gameId] = GameBinding(gameId, "nextmoe", "MANUAL", work, null, null)
    }

    private fun row(gameId: Long) = store.rows["sub-1" to gameId]


    @Test fun aUniqueIdAndExactLocalizedAliasBindWithoutIndividualConfirmation() = runBlocking {
        store.minutes[1]=60; store.names[1]="[v17]【汉化版】Riddle Joker (KRKR)"
        store.bindings[1]=GameBinding(1,"vndb","AUTO",null,"v17",null)
        remote.resolvedWorks += VnMetadata(id="200", externalRefs="vndb:v17", chineseTitle="千变万化", titleAliases=arrayListOf("Riddle Joker"))
        sync.runOnce()
        assertEquals("200", row(1)?.workId)
        assertEquals(listOf(listOf("200" to 60)), remote.writes)
    }

    @Test fun conflictingRefResponsesNeverLastWinEvenWhenTitlesAgree() = runBlocking {
        store.minutes[1]=60; store.names[1]="Exact Title"
        store.bindings[1]=GameBinding(1,"vndb","AUTO",null,"v17","302")
        remote.resolvedWorks += listOf(VnMetadata(id="200",externalRefs="vndb:v17,bangumi:302",originalTitle="Exact Title"), VnMetadata(id="201",externalRefs="vndb:v17",originalTitle="Other Title"))
        sync.runOnce()
        assertNull(row(1)?.workId)
        assertTrue(remote.writes.isEmpty())
    }
    @Test
    fun aSmallerLocalTotalNeverLowersNextmoe() =
        runBlocking {
            // A reinstall or a second device: 30 local minutes, 500 already on NextMoe.
            store.minutes[1] = 30
            manualNextmoe(1, "100")
            remote.remote["100"] = 500
            assertNull(sync.runOnce())
            assertTrue(remote.writes.isEmpty())
            assertEquals(500, row(1)?.uploadedMinutes)
            assertEquals("NextMoe's larger total is brought onto this device", 500, store.minutes[1])
            assertEquals(1, sync.status.value.pulled)
            // Playing on to 520 writes the new total.
            store.minutes[1] = 520
            sync.runOnce()
            assertEquals(listOf(listOf("100" to 520)), remote.writes)
        }

    @Test
    fun aLargerLocalTotalIsWrittenOnceAndOnlyWhenItGrows() =
        runBlocking {
            store.minutes[1] = 600
            manualNextmoe(1, "100")
            remote.remote["100"] = 500
            sync.runOnce()
            assertEquals(listOf(listOf("100" to 600)), remote.writes)
            assertEquals("new uploads appear immediately", 600, store.clouds.single().minutes)
            remote.calls = 0
            sync.runOnce()
            assertEquals("nothing changed: only the list read, nothing written", 1, remote.calls)
            assertEquals(0, sync.status.value.pulled)
            assertEquals(1, sync.status.value.summary.synced)
        }

    @Test
    fun twoLibraryEntriesOfOneWorkWriteTheLargerTotalOnce() =
        runBlocking {
            store.minutes[1] = 40
            store.minutes[2] = 90
            manualNextmoe(1, "100")
            manualNextmoe(2, "100")
            sync.runOnce()
            assertEquals(listOf(listOf("100" to 90)), remote.writes)
        }

    @Test
    fun aLoneAutomaticMatchWaitsForTheUserAndUploadsOnceConfirmed() =
        runBlocking {
            store.minutes[1] = 60
            store.bindings[1] = GameBinding(1, "vndb", "AUTO", null, "v17", null)
            remote.refs["vndb:v17"] = "200"
            sync.runOnce()
            assertTrue("one automatic source is not enough to write", remote.writes.isEmpty())
            assertEquals("200", row(1)?.candidateId)
            assertEquals("work 200", row(1)?.candidateTitle)
            assertEquals(1, sync.status.value.summary.needsConfirm)
            assertEquals(listOf(1L), sync.attention().map { it.gameId })

            store.save(1, "sub-1", NextmoePlaytimeSync.rebind(row(1)!!, "200"))
            sync.runOnce()
            assertEquals(listOf(listOf("200" to 60)), remote.writes)
        }

    @Test
    fun twoAgreeingAutomaticSourcesBindWithoutAsking() =
        runBlocking {
            store.minutes[1] = 60
            store.bindings[1] = GameBinding(1, "vndb", "AUTO", null, "v17", "302")
            remote.refs["vndb:v17"] = "200"
            remote.refs["bangumi:302"] = "200"
            sync.runOnce()
            assertEquals(listOf(listOf("200" to 60)), remote.writes)
        }

    @Test
    fun pickingAnotherWorkRemovesThisAppsRowFromTheOldOne() =
        runBlocking {
            store.minutes[1] = 60
            manualNextmoe(1, "100")
            sync.runOnce()
            manualNextmoe(1, "300")
            sync.runOnce()
            assertEquals(listOf("100"), remote.deletes)
            assertEquals(listOf(listOf("100" to 60), listOf("300" to 60)), remote.writes)
            assertNull(row(1)?.staleWorkId)
        }

    @Test
    fun aMergedWorkMovesToItsCurrentIdAndARefusalIsNotRetried() =
        runBlocking {
            store.minutes[1] = 60
            store.minutes[2] = 70
            manualNextmoe(1, "100")
            manualNextmoe(2, "101")
            remote.problems["100"] = PlaytimeWriteResult("100", false, "ENTITY_MERGED", "900")
            remote.problems["101"] = PlaytimeWriteResult("101", false, "NOT_FOUND")
            sync.runOnce()
            assertEquals("900", row(1)?.workId)
            assertEquals("NOT_FOUND", row(2)?.rejected)
            remote.writes.clear()
            // The cover still names the merged id 100: that is the same pick, so no rebind back to it.
            sync.runOnce()
            assertEquals(listOf(listOf("900" to 60)), remote.writes)
            remote.writes.clear()
            sync.runOnce()
            assertTrue("no ping-pong between the merged ids", remote.writes.isEmpty())
            assertTrue(remote.deletes.isEmpty())
            assertEquals(1, sync.status.value.summary.rejected)
            assertTrue("a refused pick is not retried", remote.writes.isEmpty())
        }

    @Test
    fun nothingIsSentWithoutAConnectedAccountOrDuringTheDailyQuota() =
        runBlocking {
            store.minutes[1] = 60
            manualNextmoe(1, "100")
            account = CoverAccountStatus()
            assertEquals(PlaytimeSyncProblem.NOT_CONNECTED, sync.runOnce())
            account = CoverAccountStatus(connected = true, id = "sub-1", needsReauth = true)
            assertEquals(PlaytimeSyncProblem.NEEDS_LOGIN, sync.runOnce())
            assertEquals(0, remote.calls)

            account = CoverAccountStatus(connected = true, id = "sub-1")
            remote.failWith = NextmoeLimitException(dailyQuota = true, untilMillis = 5_000L)
            assertEquals(PlaytimeSyncProblem.QUOTA, sync.runOnce())
            remote.failWith = null
            remote.calls = 0
            assertEquals(PlaytimeSyncProblem.QUOTA, sync.runOnce())
            assertEquals("no request until the quota resets", 0, remote.calls)
            now = 5_000L
            assertNull(sync.runOnce())
            assertEquals(listOf(listOf("100" to 60)), remote.writes)
        }

    @Test
    fun anotherAccountStartsFromScratch() =
        runBlocking {
            store.minutes[1] = 60
            manualNextmoe(1, "100")
            sync.runOnce()
            account = CoverAccountStatus(connected = true, id = "sub-2")
            remote.remote.clear()
            sync.runOnce()
            assertEquals(listOf(listOf("100" to 60), listOf("100" to 60)), remote.writes)
        }

    @Test
    fun newDeviceImportsSingleRefAndTracksUndo() = runBlocking {
        store.minutes[1] = 0
        store.bindings[1] = GameBinding(1, "vndb", "AUTO", null, "v17", null)
        remote.catalogWorks += VnMetadata(id = "200", externalRefs = "vndb:v17")
        remote.remote["200"] = 300
        sync.runOnce()
        assertEquals(300, store.minutes[1])
        assertEquals(18_000_000L, store.imported[1])
        assertEquals("BOUND", store.clouds.single().match)
        sync.undo(1)
        assertEquals(0, store.minutes[1])
        sync.runOnce()
        assertEquals("undo survives another sync", 0, store.minutes[1])
        assertTrue(remote.deletes.isEmpty())
    }

    @Test
    fun cloudWorksAreVisibleWithoutALibraryAndTitleNeedsConfirmation() = runBlocking {
        remote.remote["200"] = 300
        remote.catalogWorks += VnMetadata(id = "200", chineseTitle = "ＧＡＭＥ １")
        sync.runOnce()
        assertEquals("NONE", store.clouds.single().match)
        store.minutes[1] = 0
        sync.runOnce()
        assertEquals(0, store.minutes[1])
        assertEquals("200", row(1)?.candidateId)
        assertEquals("SUGGESTED", store.clouds.single().match)
        assertEquals(1, sync.attention().size)
        store.save(1, "sub-1", NextmoePlaytimeSync.rebind(row(1)!!, "200"))
        sync.runOnce()
        assertEquals(300, store.minutes[1])
    }

    @Test
    fun temporaryFailuresRetryAfterBackoff() = runBlocking {
        store.minutes[1] = 60; manualNextmoe(1, "100")
        remote.problems["100"] = PlaytimeWriteResult("100", false, "HTTP_500")
        sync.runOnce()
        assertNull(row(1)?.rejected)
        assertEquals(1, row(1)?.retryCount)
        remote.problems.clear(); remote.writes.clear()
        sync.runOnce()
        assertTrue(remote.writes.isEmpty())
        now = 40_000L
        sync.runOnce()
        assertEquals(listOf(listOf("100" to 60)), remote.writes)
    }

    @Test
    fun lowerTotalUsesPutAndOtherAppsCannotRaiseItBack() = runBlocking {
        store.minutes[1] = 300; manualNextmoe(1, "100")
        remote.remote["100"] = 300
        sync.runOnce()
        store.minutes[1] = 20
        store.save(1, "sub-1", row(1)!!.copy(forceWrite = true))
        remote.otherAppMinutes = 300
        sync.runOnce()
        assertEquals(listOf("100" to 20), remote.puts)
        assertEquals(20, store.minutes[1])
        assertEquals("SKIPPED_REMOTE_HIGHER", row(1)?.lastResult)
        sync.runOnce()
        assertEquals(20, store.minutes[1])
        remote.remote["100"] = 400
        sync.runOnce()
        assertEquals("a newer website increase can import again", 400, store.minutes[1])
    }

    @Test
    fun conflictingCloudRefsNeverAutoBind() = runBlocking {
        store.minutes[1] = 0
        store.bindings[1] = GameBinding(1, "vndb", "AUTO", null, "v17", null)
        remote.remote.putAll(mapOf("1" to 20, "2" to 30))
        remote.catalogWorks += listOf(VnMetadata(id="1", externalRefs="vndb:v17"), VnMetadata(id="2", externalRefs="vndb:v17"))
        sync.runOnce()
        assertNull(row(1)?.workId)
        assertEquals(0, store.minutes[1])
    }

    @Test
    fun theWriteResultParserReadsPlaytimesAndProblems() {
        assertTrue(NextmoeUserApi.parseWrite("1", org.json.JSONObject("""{"status":200,"object":"playtime","work_id":"1","minutes":5}""")).ok)
        assertTrue(NextmoeUserApi.parseWrite("1", org.json.JSONObject("""{"object":"playtime","work_id":"1","minutes":5}""")).ok)
        val merged = NextmoeUserApi.parseWrite("1", org.json.JSONObject("""{"status":404,"problem":{"code":"ENTITY_MERGED","current_id":"9"}}"""))
        assertEquals(PlaytimeWriteResult("1", false, "ENTITY_MERGED", "9"), merged)
        for(status in listOf(404, 429, 500)) assertEquals("HTTP_$status", NextmoeUserApi.parseWrite("1", org.json.JSONObject("""{"status":$status,"problem":{}}""")).code)
        assertEquals("MISSING", NextmoeUserApi.parseWrite("1", null).code)
        val quota = NextmoeUserApi.limit("""{"code":"QUOTA_EXCEEDED"}""", "30", now = 0L)
        assertTrue(quota.dailyQuota)
        assertEquals(86_400_000L, quota.untilMillis)
        assertEquals(30_000L, NextmoeUserApi.limit("""{"code":"RATE_LIMITED"}""", "30", now = 0L).untilMillis)
    }

    @Test
    fun aRateLimitedItemCanRetryAndDoesNotBecomePermanent() = runBlocking {
        store.minutes[1] = 60; manualNextmoe(1, "100")
        remote.problems["100"] = PlaytimeWriteResult("100", false, "RATE_LIMITED")
        sync.runOnce()
        assertNull(row(1)?.rejected)
        remote.problems.clear(); remote.writes.clear(); now = 40_000L
        sync.runOnce()
        assertEquals(listOf(listOf("100" to 60)), remote.writes)
    }

    @Test
    fun ambiguousCloudEvidenceAlsoStopsStrictAutomaticBinding() = runBlocking {
        store.minutes[1] = 0
        store.bindings[1] = GameBinding(1, "vndb", "AUTO", null, "v17", "302")
        remote.refs.putAll(mapOf("vndb:v17" to "1", "bangumi:302" to "1"))
        remote.remote.putAll(mapOf("1" to 20, "2" to 30))
        remote.catalogWorks += listOf(VnMetadata(id="1", externalRefs="vndb:v17,bangumi:302"), VnMetadata(id="2", externalRefs="vndb:v17"))
        sync.runOnce()
        assertNull(row(1)?.workId)
        assertEquals(0, store.minutes[1])
    }

    @Test
    fun duplicateCloudRowsKeepLargestValue() {
        assertEquals(mapOf("1" to 300), NextmoeUserApi.maxPlaytimes(org.json.JSONArray("""[{"work_id":"1","minutes":300},{"work_id":"1","minutes":20}]""")))
    }

    @Test
    fun localMinutesAreFlooredAndCappedAtNextmoesLimit() {
        assertEquals(1, NextmoePlaytimeLedger.localMinutes(119_999L))
        assertEquals(NEXTMOE_MAX_MINUTES, NextmoePlaytimeLedger.localMinutes(Long.MAX_VALUE / 2))
    }
}

/** The SharedPreferences slice the sync uses, in memory. */
private class MemoryPrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(
        key: String?,
        defValue: String?,
    ) = values[key] as? String ?: defValue

    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ) = defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ) = values[key] as? Int ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ) = values[key] as? Long ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ) = values[key] as? Float ?: defValue

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ) = values[key] as? Boolean ?: defValue

    override fun contains(key: String?) = key in values

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor =
        object : SharedPreferences.Editor {
            override fun putString(
                key: String?,
                value: String?,
            ) = apply { values[key!!] = value }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ) = this

            override fun putInt(
                key: String?,
                value: Int,
            ) = apply { values[key!!] = value }

            override fun putLong(
                key: String?,
                value: Long,
            ) = apply { values[key!!] = value }

            override fun putFloat(
                key: String?,
                value: Float,
            ) = apply { values[key!!] = value }

            override fun putBoolean(
                key: String?,
                value: Boolean,
            ) = apply { values[key!!] = value }

            override fun remove(key: String?) = apply { values.remove(key) }

            override fun clear() = apply { values.clear() }

            override fun commit() = true

            override fun apply() = Unit
        }
}
