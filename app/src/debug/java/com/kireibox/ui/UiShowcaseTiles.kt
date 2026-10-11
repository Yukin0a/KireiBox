package com.kireibox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing

/**
 * Debug-only tile palette comparison (`--es page tiles --es tiles legacy|solid|tonal`): the settings
 * root rows drawn with the alpha.17 tiles, OKLCH solid tiles (white glyph) or OKLCH tonal tiles
 * (tinted container + colored glyph), so the styles can be judged side by side on a device.
 */
internal enum class ShowcaseTileStyle { LEGACY, SOLID, TONAL, CURRENT }

/** One candidate tile: fill and glyph for the dark and the light theme. */
private class ShowcaseTile(
    val darkFill: Color,
    val darkGlyph: Color,
    val lightFill: Color,
    val lightGlyph: Color,
)

private val White = Color(0xFFFFFFFF)

private fun solid(color: Long) = ShowcaseTile(Color(color), White, Color(color), White)

private fun tonal(
    darkFill: Long,
    darkGlyph: Long,
    lightFill: Long,
    lightGlyph: Long,
) = ShowcaseTile(Color(darkFill), Color(darkGlyph), Color(lightFill), Color(lightGlyph))

private val LegacyTiles =
    mapOf(
        "PINK" to solid(0xFFFF5E8A),
        "PURPLE" to solid(0xFFAF52DE),
        "INDIGO" to solid(0xFF5E6AD2),
        "BLUE" to solid(0xFF3D8BFD),
        "TEAL" to solid(0xFF14B8A6),
        "GREEN" to solid(0xFF30C46C),
        "ORANGE" to solid(0xFFFF9F0A),
        "RED" to solid(0xFFFF5A5F),
        "SLATE" to solid(0xFF64748B),
        "GRAY" to solid(0xFF8E8E93),
    )

/** OKLCH L .62 C .13 (teal .106 to stay in sRGB, slate .036, gray .010). */
private val SolidTiles =
    mapOf(
        "PINK" to solid(0xFFBF6291),
        "PURPLE" to solid(0xFF9970C4),
        "INDIGO" to solid(0xFF707ED4),
        "BLUE" to solid(0xFF348DCF),
        "TEAL" to solid(0xFF079A94),
        "GREEN" to solid(0xFF429C5A),
        "ORANGE" to solid(0xFFC16E2D),
        "RED" to solid(0xFFC8635D),
        "SLATE" to solid(0xFF78889C),
        "GRAY" to solid(0xFF85858C),
    )

/** Dark: container L .31 C .05 / glyph L .82 C .10. Light: container L .945 C .03 / glyph L .53 C .13. */
private val TonalTiles =
    mapOf(
        "PINK" to tonal(0xFF432534, 0xFFF6A9CD, 0xFFFEE5F0, 0xFFA14876),
        "PURPLE" to tonal(0xFF362A45, 0xFFD3B4F9, 0xFFF1E8FE, 0xFF7E55A7),
        "INDIGO" to tonal(0xFF292E4A, 0xFFB3C0FF, 0xFFE7ECFF, 0xFF5763B6),
        "BLUE" to tonal(0xFF193348, 0xFF8ECBFF, 0xFFDEEFFF, 0xFF0A71B1),
        "TEAL" to tonal(0xFF063836, 0xFF6ED9D2, 0xFFD7F4F1, 0xFF087C77),
        "GREEN" to tonal(0xFF1C3723, 0xFF95D7A2, 0xFFDFF3E2, 0xFF218041),
        "ORANGE" to tonal(0xFF442916, 0xFFF7B385, 0xFFFEE8DA, 0xFFA35303),
        "RED" to tonal(0xFF462623, 0xFFFFABA3, 0xFFFEE7E4, 0xFFAA4844),
        "SLATE" to tonal(0xFF2C3137, 0xFFB8C5D6, 0xFFE9EDF2, 0xFF5E6D81),
        "GRAY" to tonal(0xFF303032, 0xFFC3C3C9, 0xFFECECEE, 0xFF6B6B72),
    )

private class ShowcaseRootRow(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val tile: String,
)

private val ShowcaseRootGroups =
    listOf(
        "个性化" to
            listOf(
                ShowcaseRootRow(Icons.Rounded.Palette, "外观", "主题、强调色、显示与屏幕方向", "PINK"),
                ShowcaseRootRow(Icons.Rounded.Home, "首页", "首页样式、展示内容与视觉效果", "PURPLE"),
            ),
        "游戏" to
            listOf(
                ShowcaseRootRow(Icons.Rounded.GridView, "游戏库", "管理扫描目录与排序", "BLUE"),
                ShowcaseRootRow(Icons.Rounded.CollectionsBookmark, "封面与元数据", "选择来源并管理游戏封面", "TEAL"),
                ShowcaseRootRow(Icons.Rounded.Gamepad, "KRKR 引擎", "版本、兼容和启动选项", "ORANGE"),
            ),
        "系统" to
            listOf(
                ShowcaseRootRow(Icons.Rounded.Folder, "存储与权限", "扫描授权和原生引擎权限", "GREEN"),
                ShowcaseRootRow(Icons.Rounded.Terminal, "日志与诊断", "记录、导出和复制日志", "SLATE"),
            ),
        "关于" to
            listOf(
                ShowcaseRootRow(Icons.Rounded.Info, "关于 KireiBox", "开源地址与第三方声明", "GRAY"),
                ShowcaseRootRow(Icons.Rounded.Update, "检查更新", "当前版本 0.5.0-alpha.17", "INDIGO"),
            ),
    )

@Composable
internal fun ShowcaseTilesPage(
    colors: KireiColors,
    style: ShowcaseTileStyle,
) {
    val palette =
        when (style) {
            ShowcaseTileStyle.LEGACY -> LegacyTiles
            ShowcaseTileStyle.SOLID -> SolidTiles
            ShowcaseTileStyle.TONAL -> TonalTiles
            ShowcaseTileStyle.CURRENT -> null
        }
    val metrics = resolveSettingsCategoryRowMetrics(HomeOrientation.PORTRAIT)
    Column(
        Modifier.fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = AdaptiveLayoutConstants.PAGE_GUTTER_DP.dp),
    ) {
        Text("图标块：${style.name.lowercase()}", color = colors.text, style = AppleTypography.headlineMedium, modifier = Modifier.padding(vertical = KireiSpacing.md))
        ShowcaseRootGroups.forEach { (title, rows) ->
            KireiListGroup(colors, Modifier.padding(bottom = KireiSpacing.md), title = title) {
                rows.forEach { row ->
                    item {
                        val spec =
                            if (palette == null) {
                                KireiListRowSpec(row.title, row.subtitle, icon = row.icon, tile = KireiTileColor.valueOf(row.tile), trailing = KireiRowTrailing.Chevron)
                            } else {
                                val tile = palette.getValue(row.tile)
                                KireiListRowSpec(
                                    row.title,
                                    row.subtitle,
                                    icon = row.icon,
                                    trailing = KireiRowTrailing.Chevron,
                                    leading = { ShowcaseTileBox(row.icon, tile, colors.isDark, metrics.iconSizeDp.dp) },
                                )
                            }
                        KireiListRow(spec.withCategoryMetrics(metrics), colors) {}
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(KireiSpacing.xl))
    }
}

@Composable
private fun ShowcaseTileBox(
    icon: ImageVector,
    tile: ShowcaseTile,
    dark: Boolean,
    size: Dp,
) {
    Box(Modifier.size(size).background(if (dark) tile.darkFill else tile.lightFill, KireiShapes.sm), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = if (dark) tile.darkGlyph else tile.lightGlyph, modifier = Modifier.size(size * .6f))
    }
}
