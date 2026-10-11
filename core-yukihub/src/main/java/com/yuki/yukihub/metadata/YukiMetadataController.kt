package com.yuki.yukihub.metadata
import android.content.Context
import com.yuki.yukihub.data.MetadataRepository
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata

/**
 * Compose-safe domain adapter for the YukiHub metadata controller.
 *
 * The old YukiHub controller mixed Android Views with source selection. KireiBox
 * keeps its source keys, labels and cache partitioning but exposes a small
 * context/repository API that can be collected by Compose without importing the
 * legacy XML UI.
 */
class YukiMetadataController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val repository = MetadataRepository(appContext)

    fun source(): CoverSourceMode =
        CoverSourceMode.selected(
            prefs.getString(KEY_METADATA_SOURCE, CoverSourceMode.BANGUMI.key),
        )

    fun setSource(source: CoverSourceMode) {
        prefs.edit().putString(KEY_METADATA_SOURCE, source.key).apply()
    }

    fun bangumiToken(): String = com.yuki.yukihub.auth.CoverAccounts.get(appContext).accessToken(CoverSourceMode.BANGUMI)

    fun visibleSource(gameId: Long): CoverSourceMode? {
        if (gameId <= 0) return null
        val raw = prefs.getString(KEY_VISIBLE_METADATA_SOURCE_PREFIX + gameId, null)
        return raw?.takeIf { it.isNotBlank() }?.let(CoverSourceMode::fromStored)
    }

    fun setVisibleSource(
        gameId: Long,
        source: CoverSourceMode?,
    ) {
        if (gameId <= 0) return
        val editor = prefs.edit()
        if (source == null) {
            editor.remove(KEY_VISIBLE_METADATA_SOURCE_PREFIX + gameId)
        } else {
            editor.putString(KEY_VISIBLE_METADATA_SOURCE_PREFIX + gameId, source.key)
        }
        editor.apply()
    }

    fun metadata(
        gameId: Long,
        source: CoverSourceMode = visibleSource(gameId) ?: this.source(),
    ): VnMetadata? =
        when (source) {
            CoverSourceMode.NEXTMOE -> repository.get(gameId, source)
            CoverSourceMode.VNDB -> repository.getVndb(gameId)
            CoverSourceMode.BANGUMI -> repository.getBangumi(gameId)
            CoverSourceMode.BANGUMI_MIRROR -> repository.getBangumiMirror(gameId)
            CoverSourceMode.YMGAL -> repository.getYmgal(gameId)
            CoverSourceMode.HIKARINAGI -> repository.getHikarinagi(gameId)
        }

    fun saveMetadata(
        gameId: Long,
        source: CoverSourceMode,
        metadata: VnMetadata?,
    ) {
        when (source) {
            CoverSourceMode.NEXTMOE -> repository.saveNextmoe(gameId, metadata)
            CoverSourceMode.VNDB -> repository.saveVndb(gameId, metadata)
            CoverSourceMode.BANGUMI -> repository.saveBangumi(gameId, metadata)
            CoverSourceMode.BANGUMI_MIRROR -> repository.saveBangumiMirror(gameId, metadata)
            CoverSourceMode.YMGAL -> repository.saveYmgal(gameId, metadata)
            CoverSourceMode.HIKARINAGI -> repository.saveHikarinagi(gameId, metadata)
        }
        if (metadata != null) setVisibleSource(gameId, source)
    }

    companion object {
        // Keep the exact YukiHub preference file so the Compose UI and native
        // engine/settings bridges read one authoritative set of options.
        const val PREFS_NAME = "yukihub_prefs"
        const val KEY_METADATA_SOURCE = "metadata_source"
        const val KEY_VISIBLE_METADATA_SOURCE_PREFIX = "visible_metadata_source_"
        const val KEY_BANGUMI_TOKEN = "bangumi_token"
    }
}
