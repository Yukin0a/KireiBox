package com.yuki.yukihub.metadata

/**
 * 元数据来源相关常量定义。
 * 原有的 Delegate 接口和实例方法已随 MainActivity 一并移除，
 * 元数据获取流程改由 apps 端通过 LauncherMetadataBridge 调用各 Client 完成。
 */
object MetadataController {

    const val SOURCE_VNDB = "vndb"
    const val SOURCE_BANGUMI = "bangumi"
    const val SOURCE_BANGUMI_MIRROR = "bangumi_mirror"
    const val SOURCE_YMGAL = "ymgal"
    const val SOURCE_HIKARINAGI = "hikarinagi"

    const val KEY_METADATA_SOURCE = "metadata_source"
    const val KEY_VISIBLE_METADATA_SOURCE_PREFIX = "visible_metadata_source_"
    const val KEY_BANGUMI_TOKEN = "bangumi_token"
    const val KEY_SIDE_TRANSLATED_PREFIX = "side_translated_"
}
