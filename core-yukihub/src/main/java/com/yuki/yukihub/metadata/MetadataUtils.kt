package com.yuki.yukihub.metadata

internal object MetadataUtils {

    @JvmStatic
    @JvmName("cleanTitle")
    fun cleanTitle(s: String?): String {
        if (s == null) return ""
        val x = s.replace("[\\[\\]【】（）()].*".toRegex(), " ")
            .replace("(?i)complete|汉化|中文版|日文版|体验版|trial|patch".toRegex(), " ")
            .replace('_', ' ')
            .trim()
        return if (x.isEmpty()) s.trim() else x
    }

    @JvmStatic
    @JvmName("firstNonEmpty")
    fun firstNonEmpty(a: String?, b: String?): String {
        return if (a != null && a.isNotEmpty() && a != "null") a
        else if (b == null || b == "null") ""
        else b
    }

    @JvmStatic
    @JvmName("join")
    fun join(list: List<String?>, sep: String): String {
        return list.filter { !it.isNullOrEmpty() }.joinToString(sep)
    }

    @JvmStatic
    @JvmName("sleepBeforeRetry")
    @Throws(InterruptedException::class)
    fun sleepBeforeRetry(delayMs: Long) {
        try {
            Thread.sleep(maxOf(0L, delayMs))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }
    }
}
