package com.yuki.yukihub.metadata.match
import com.yuki.yukihub.metadata.model.CoverSourceMode

/** NextMoe is anonymous and first; only Hikarinagi requires a connected account. */
object CoverSourcePriority {
    @JvmStatic
    fun searchOrder(connected: (CoverSourceMode) -> Boolean = { true }): List<CoverSourceMode> =
        buildList {
            add(CoverSourceMode.NEXTMOE)
            add(CoverSourceMode.BANGUMI)
            if (connected(CoverSourceMode.HIKARINAGI)) add(CoverSourceMode.HIKARINAGI)
            add(CoverSourceMode.VNDB)
        }

    fun savedOrder(context: android.content.Context): List<CoverSourceMode> {
        val defaults = searchOrder()
        val keys =
            context.getSharedPreferences("yukihub_prefs", android.content.Context.MODE_PRIVATE)
                .getString("cover_source_order", "").orEmpty().split(",")
        return (keys.mapNotNull { key -> defaults.firstOrNull { it.key == key } } + defaults).distinct()
    }

    fun saveOrder(
        context: android.content.Context,
        order: List<CoverSourceMode>
    ) {
        context.getSharedPreferences("yukihub_prefs", android.content.Context.MODE_PRIVATE).edit()
            .putString("cover_source_order", order.joinToString(",") { it.key }).apply()
    }

    /** Rank within [searchOrder]; unknown sources sink to the end. */
    @JvmStatic
    fun rank(
        source: CoverSourceMode,
        connected: (CoverSourceMode) -> Boolean = { true },
    ): Int = searchOrder(connected).indexOf(source).let { if (it < 0) Int.MAX_VALUE else it }
}
