package com.yuki.yukihub.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * 播放时长与日期格式化工具。
 */
object TimeFormatUtil {
    @JvmStatic
    fun playTime(millis: Long): String {
        if (millis <= 0) return "0s"
        if (millis < 60_000L) {
            val seconds = maxOf(1L, (millis / 1000.0).roundToLong())
            return "${seconds}s"
        }
        val hours = millis / 3_600_000.0
        if (hours < 1.0) {
            val minutes = maxOf(1L, (millis / 60_000.0).roundToLong())
            return "${minutes}m"
        }
        return DecimalFormat("0.0").format(hours) + "h"
    }

    @JvmStatic
    fun date(time: Long): String {
        if (time <= 0) return "从未游玩"
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
    }
}
