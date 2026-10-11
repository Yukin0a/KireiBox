package com.kireibox.designsystem

import androidx.compose.animation.core.spring

/**
 * Apple Design 动效系统
 * 严格遵循 apple-design skill 的弹簧物理规范：
 * - 默认 critically damped（damping 1.0, response 0.3–0.4）
 * - 动量交互用 under-damped（damping ~0.8）
 * - 所有手势驱动动画必须可打断、从当前值开始、速度连续
 */
object AppleMotion {
    // Apple 官方参数映射（skill 中的 Quick Reference）
    object Spring {
        /** 默认 UI 弹簧：无 overshoot，平滑 settle（spec §7：damping 1.0, stiffness 380） */
        val Default =
            spring<Float>(
                dampingRatio = 1.0f,
                stiffness = 380f
            )

        /** 强调弹簧：选中指示器滑动、Sheet（spec §7：damping .82, stiffness 300） */
        val Emphasized =
            spring<Float>(
                dampingRatio = 0.82f,
                stiffness = 300f
            )

        /** 位移/重组（PiP、卡片移动） */
        val Move =
            spring<Float>(
                dampingRatio = 1.0f,
                // 近似 response 0.4s
                stiffness = 250f
            )

        /** 旋转/抽屉/底部弹层：轻微 bounce */
        val Sheet =
            spring<Float>(
                dampingRatio = 0.8f,
                // 近似 response 0.3s
                stiffness = 280f
            )

        /** 按压反馈：快速、无 overshoot，配合 0.97 缩放（spec §7：damping 1.0, stiffness 700） */
        val Press =
            spring<Float>(
                dampingRatio = 1.0f,
                stiffness = 700f
            )

        /** 列表 item 入场：轻弹 */
        val ListEnter =
            spring<Float>(
                dampingRatio = 0.85f,
                stiffness = 320f
            )
    }

    // 持续时间（仅用于非手势驱动的淡入淡出）
    object Duration {
        /** spec §7：淡入淡出、颜色过渡的三档时长（ms）。 */
        const val SHORT = 120
        const val MEDIUM = 200
        const val LONG = 320

        const val FADE = 200
        const val CROSS_FADE = 300
        const val PRESS_FEEDBACK = 100
    }

    // 手势投影（skill §6：momentum projection）
    fun projectMomentum(
        initialVelocity: Float,
        decelerationRate: Float = 0.998f
    ): Float {
        return (initialVelocity / 1000f) * decelerationRate / (1f - decelerationRate)
    }

    // 橡皮筋阻尼（skill §9：rubber-banding）
    fun rubberband(
        overshoot: Float,
        dimension: Float,
        constant: Float = 0.55f
    ): Float {
        return (overshoot * dimension * constant) / (dimension + constant * kotlin.math.abs(overshoot))
    }

    // 速度归一化（skill §5：velocity handoff）
    fun normalizeVelocity(
        gestureVelocity: Float,
        targetValue: Float,
        currentValue: Float
    ): Float {
        val distance = targetValue - currentValue
        return if (distance != 0f) gestureVelocity / distance else 0f
    }
}
