package com.yuki.yukihub.metadata.match
import com.yuki.yukihub.metadata.model.CoverSourceMode

/**
 * Dynamic, availability-aware ordering of cover sources.
 *
 * `CoverSourceMode.active` is a fixed, persisted order (settings and tests depend on it), so it
 * must not change. This is a separate, purely derived order used for card ordering, representative
 * selection and the automatic-match probe sequence:
 *
 * 1. **NextMoe** when logged in — fast, multi-cover, and its `titles`/`refs` blocks carry the
 *    cross-language aliases and the cross-source id bridge.
 * 2. **Bangumi** — always available anonymously (official host falls back to a community mirror).
 * 3. **Hikarinagi** when logged in — slower (per-item detail calls).
 * 4. **VNDB** — anonymous and the broadest catalog, but slow/unreliable on mainland networks, so
 *    it sits last. It is deliberately *never dropped*: with no account connected, Bangumi + VNDB
 *    are the only anonymous sources, and VNDB is the widest of the two.
 */
object CoverSourcePriority {
    @JvmStatic
    fun searchOrder(connected: (CoverSourceMode) -> Boolean = { true }): List<CoverSourceMode> =
        buildList {
            if (connected(CoverSourceMode.NEXTMOE)) add(CoverSourceMode.NEXTMOE)
            add(CoverSourceMode.BANGUMI)
            if (connected(CoverSourceMode.HIKARINAGI)) add(CoverSourceMode.HIKARINAGI)
            add(CoverSourceMode.VNDB)
        }

    /** Rank within [searchOrder]; unknown sources sink to the end. */
    @JvmStatic
    fun rank(
        source: CoverSourceMode,
        connected: (CoverSourceMode) -> Boolean = { true },
    ): Int = searchOrder(connected).indexOf(source).let { if (it < 0) Int.MAX_VALUE else it }
}
