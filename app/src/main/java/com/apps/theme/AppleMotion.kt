package com.apps.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

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
        /** 默认 UI 弹簧：无 overshoot，平滑 settle */
        val Default = spring<Float>(
            dampingRatio = 1.0f,
            stiffness = 300f   // 近似 response 0.33s
        )

        /** 位移/重组（PiP、卡片移动） */
        val Move = spring<Float>(
            dampingRatio = 1.0f,
            stiffness = 250f   // 近似 response 0.4s
        )

        /** 旋转/抽屉/底部弹层：轻微 bounce */
        val Sheet = spring<Float>(
            dampingRatio = 0.8f,
            stiffness = 280f   // 近似 response 0.3s
        )

        /** 按压反馈：快速、无 overshoot */
        val Press = spring<Float>(
            dampingRatio = 1.0f,
            stiffness = 600f   // 极快响应
        )

        /** 列表 item 入场：轻弹 */
        val ListEnter = spring<Float>(
            dampingRatio = 0.85f,
            stiffness = 320f
        )
    }

    // 持续时间（仅用于非手势驱动的淡入淡出）
    object Duration {
        const val Fade = 200
        const val CrossFade = 300
        const val PressFeedback = 100
    }

    // 手势投影（skill §6：momentum projection）
    fun projectMomentum(initialVelocity: Float, decelerationRate: Float = 0.998f): Float {
        return (initialVelocity / 1000f) * decelerationRate / (1f - decelerationRate)
    }

    // 橡皮筋阻尼（skill §9：rubber-banding）
    fun rubberband(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float {
        return (overshoot * dimension * constant) / (dimension + constant * kotlin.math.abs(overshoot))
    }

    // 速度归一化（skill §5：velocity handoff）
    fun normalizeVelocity(gestureVelocity: Float, targetValue: Float, currentValue: Float): Float {
        val distance = targetValue - currentValue
        return if (distance != 0f) gestureVelocity / distance else 0f
    }
}

/**
 * 按压反馈状态：pointer-down 即时缩放
 */
class PressFeedbackState {
    val scale = Animatable(1f)
    val alpha = Animatable(1f)

    suspend fun onPress() {
        scale.animateTo(0.97f, AppleMotion.Spring.Press)
    }

    suspend fun onRelease() {
        scale.animateTo(1f, AppleMotion.Spring.Default)
    }

    suspend fun onCancel() {
        scale.animateTo(1f, AppleMotion.Spring.Default)
    }
}

@Composable
fun rememberPressFeedbackState(): PressFeedbackState {
    return remember { PressFeedbackState() }
}
