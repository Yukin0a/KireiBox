package com.kireibox.ui
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.yuki.yukihub.data.PlayTrendPoint
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.TrendMetric
import com.yuki.yukihub.settings.TrendRange
import com.yuki.yukihub.settings.TrendVisualStyle
import dev.chrisbanes.haze.HazeState

@Composable
internal fun TrendConfigSheet(
    spec: DashboardWidgetSpec,
    points: List<PlayTrendPoint>,
    colors: KireiColors,
    haze: HazeState?,
    onDismiss: () -> Unit,
    onComplete: (TrendConfigDraft) -> Unit,
) {
    var draft by remember(spec.instanceId, spec.trendMetric, spec.trendRange, spec.trendVisualStyle) {
        mutableStateOf(TrendConfigDraft(spec.trendMetric, spec.trendRange, spec.trendVisualStyle))
    }
    KireiActionSheet("趋势图设置", colors, onDismiss, expanded = true) {
        TrendConfigSection(
            "指标",
            colors,
            listOf(
                TrendMetric.PLAY_TIME to "游玩时长",
                TrendMetric.GAME_OPENS to "打开游戏数",
            ),
            draft.metric
        ) { draft = draft.copy(metric = it) }
        TrendConfigSection(
            "范围",
            colors,
            listOf(
                TrendRange.DAYS_7 to "7 天",
                TrendRange.DAYS_30 to "30 天",
            ),
            draft.range
        ) { draft = draft.copy(range = it) }
        TrendConfigSection(
            "外观",
            colors,
            listOf(
                TrendVisualStyle.LINE to "折线",
                TrendVisualStyle.AREA to "面积折线",
            ),
            draft.visualStyle
        ) { draft = draft.copy(visualStyle = it) }
        Text("预览", color = colors.secondary, style = AppleTypography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        DashboardTrendWidget(
            points = points,
            spec = spec.copy(trendMetric = draft.metric, trendRange = draft.range, trendVisualStyle = draft.visualStyle),
            colors = colors,
            haze = haze,
            interactive = false,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("取消", color = colors.secondary) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onComplete(draft) }) { Text("完成", color = colors.accent) }
        }
    }
}

@Composable
private fun <T> TrendConfigSection(
    title: String,
    colors: KireiColors,
    values: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Text(title, color = colors.secondary, style = AppleTypography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    values.forEach { (value, label) ->
        Row(
            Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(horizontal = 24.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = colors.text, style = AppleTypography.bodyLargeEmphasis, modifier = Modifier.weight(1f))
            if (selected == value) Icon(Icons.Rounded.Check, "已选择", tint = colors.accent, modifier = Modifier.size(20.dp))
        }
    }
}
