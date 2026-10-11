package com.kireibox.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import com.yuki.yukihub.settings.MotionMode

/**
 * The app's resolved motion mode (AUTO already decided), provided once by KireiBoxApp and read by
 * design-system components and pages alike. It lives in the design system so components never
 * depend on the ui package. Default REDUCED: a host that forgets to provide it gets calm, snapping
 * transitions rather than motion the user may have turned off.
 */
internal val LocalResolvedMotionMode = staticCompositionLocalOf { MotionMode.REDUCED }
