package com.idguard.tasks.res

import com.idguard.utils.MappingOutputHelper
import com.idguard.utils.RandomNameHelper
import com.idguard.utils.findDirsInRes
import com.idguard.utils.getExtensionName
import com.idguard.utils.isAndroidProject
import com.idguard.utils.replaceWords
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

/**
 * @Description
 * @author yanz
 * @date 2024/3/20 16:55
 */
open class ColorResGuardTask @Inject constructor(
    private val variantName: String,
) : BaseResGuardTask(variantName) {
    private val colorNameMap = mutableMapOf<String, String>()
    private val colorRegex = Regex("<color name=\"\\w+\">")
    private val needReplaceFileExtensionName = listOf(".xml", ".java", ".kt")

    @TaskAction
    fun execute() {
        colorObfuscate()
        MappingOutputHelper.appendNewLan(project, mappingName, "colors mapping")
        MappingOutputHelper.write(project, mappingName, colorNameMap)
    }

    private fun colorObfuscate() {
        val needSearchFileTree = project.files(findColorsFiles()).asFileTree
        val namesSet = mutableSetOf<String>()
        needSearchFileTree.forEach {
            namesSet.addAll(findColorsName(it.readText()))
        }
        val obfuscateNames = RandomNameHelper.genNames(namesSet.size, Pair(8, 12), allLetter = false)
        colorNameMap.putAll(namesSet.mapIndexed { index: Int, name: String ->
            name to obfuscateNames[index]
        })
        val needReplaceFiles = findNeedReplaceFiles(
            "drawable",
            "values",
            "layout",
        )
        project.rootProject.subprojects {
            if (!it.isAndroidProject()) {
                return@subprojects
            }
            it.files(needReplaceFiles).asFileTree.forEach { file: File ->
                if (!needReplaceFileExtensionName.contains(file.getExtensionName())) {
                    //如果不加这个剔除功能 可能会对某些文件有影响
                    return@forEach
                }
                var text = file.readText()

                colorNameMap.forEach { (raw, obfuscate) ->
                    text = text
                        .replaceWords("<color name=\"$raw\">", "<color name=\"$obfuscate\">")
                        .replaceWords("R.color.$raw", "R.color.$obfuscate")
                        .replaceWords("@color/$raw", "@color/$obfuscate")
                }
                file.writeText(text)
            }
        }
    }

    private fun findColorsFiles(): List<File> {
        val dirs = mutableListOf<File>()
        project.rootProject.subprojects {
            if (it.isAndroidProject()) {
                dirs.addAll(
                    it.findDirsInRes(
                        variantName,
                        "values",
                    )
                )
            }
        }
        return dirs.toList()
    }

    private fun findColorsName(stringText: String): List<String> {
        val result = colorRegex.findAll(stringText)
        return result.map { it.value.removePrefix("<").removeSuffix(">").split("\"")[1] }.toList()
    }
}