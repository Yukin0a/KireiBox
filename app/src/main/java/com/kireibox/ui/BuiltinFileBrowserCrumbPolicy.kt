package com.kireibox.ui

internal object BuiltinFileBrowserCrumbPolicy {
    fun shouldHideRow(crumbCount: Int): Boolean = crumbCount <= 1
}
