package com.kireibox.krkr

import android.content.Context
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.storage.StorageNode
import com.yuki.yukihub.storage.StorageNodes
import org.w3c.dom.Element
import java.util.LinkedHashMap
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class KrkrGameSettings(
    val renderer: String = "software",
    val memoryUsage: String = "unlimited",
    val renderThread: String = "auto",
    val fpsLimit: Int = 60,
    val menuOpacity: Float = .15f,
    val cursorScale: Float = .5f,
    val outputLog: Boolean = true,
    val showFps: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val textureCompression: Boolean = false,
    val forceDefaultFont: Boolean = false,
    val defaultFont: String = ""
)

/** Original YukiHub Kirikiroid2Preference.xml semantics, isolated from Config.tjs. */
class KrkrGameSettingsStore(private val context: Context) {
    fun load(game: Game): KrkrGameSettings {
        val file = preferenceFile(game, create = false) ?: return KrkrGameSettings()
        return runCatching {
            val values = LinkedHashMap<String, String>()
            file.openInputStream(context)?.use { input ->
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
                val items = doc.getElementsByTagName("Item")
                for (i in 0 until items.length) {
                    val element = items.item(i) as? Element ?: continue
                    values[element.getAttribute("Key")] = element.getAttribute("Value")
                }
            }
            KrkrGameSettings(
                renderer = values["renderer"] ?: "software",
                memoryUsage = values["memusage"] ?: "unlimited",
                renderThread = values["render_thread"] ?: "auto",
                fpsLimit = values["fps_limit"]?.toIntOrNull()?.coerceIn(15, 240) ?: 60,
                menuOpacity = values["menu_handler_opa"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: .15f,
                cursorScale = values["vcursor_scale"]?.toFloatOrNull()?.coerceIn(.25f, 2f) ?: .5f,
                outputLog = values["outputlog"] != "0",
                showFps = values["showfps"] == "1",
                keepScreenAwake = values["keep_screen_alive"] != "0",
                textureCompression = values["texture_compress"] == "1",
                forceDefaultFont = values["force_default_font"] == "1",
                defaultFont = values["default_font"].orEmpty()
            )
        }.getOrDefault(KrkrGameSettings())
    }

    fun save(game: Game, settings: KrkrGameSettings): Result<Unit> = runCatching {
        val file = preferenceFile(game, create = true) ?: error("无法访问游戏目录")
        val values = linkedMapOf(
            "menu_handler_opa" to settings.menuOpacity.toString(),
            "vcursor_scale" to settings.cursorScale.toString(),
            "renderer" to settings.renderer,
            "memusage" to settings.memoryUsage,
            "render_thread" to settings.renderThread,
            "fps_limit" to settings.fpsLimit.toString(),
            "outputlog" to if (settings.outputLog) "1" else "0",
            "showfps" to if (settings.showFps) "1" else "0",
            "keep_screen_alive" to if (settings.keepScreenAwake) "1" else "0",
            "texture_compress" to if (settings.textureCompression) "1" else "0",
            "force_default_font" to if (settings.forceDefaultFont) "1" else "0",
            "default_font" to settings.defaultFont
        )
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = builder.newDocument()
        val root = doc.createElement("GlobalPreference")
        doc.appendChild(root)
        for ((key, value) in values) {
            val item = doc.createElement("Item")
            item.setAttribute("Key", key)
            item.setAttribute("Value", value)
            root.appendChild(item)
        }
        file.openOutputStream(context)?.use { output ->
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(OutputKeys.INDENT, "yes")
            }.transform(DOMSource(doc), StreamResult(output))
        } ?: error("无法写入 KRKR 设置")
    }

    private fun preferenceFile(game: Game, create: Boolean): StorageNode? {
        val rootUri = game.rootUri?.takeIf(String::isNotBlank) ?: return null
        val root = runCatching { StorageNodes.resolve(context, rootUri) }.getOrNull() ?: return null
        return root.findChild(FILE_NAME) ?: if (create) {
            root.createFile(context, "text/xml", FILE_NAME)
        } else null
    }

    companion object { private const val FILE_NAME = "Kirikiroid2Preference.xml" }
}
