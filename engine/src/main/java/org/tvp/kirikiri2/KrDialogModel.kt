package org.tvp.kirikiri2

/**
 * 弹窗数据载体 — 由 ShowMessageBox/ShowInputBox 填充，
 * 由 ShowMessageBoxRunnable/ShowInputBoxRunnable 消费。
 */
class KrDialogModel {
    @JvmField var title: String? = null
    @JvmField var message: String? = null
    @JvmField var buttons: Array<String>? = null
}
