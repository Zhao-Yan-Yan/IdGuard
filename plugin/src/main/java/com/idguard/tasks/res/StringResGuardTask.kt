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
open class StringResGuardTask @Inject constructor(
    private val variantName: String,
) : BaseResGuardTask(variantName) {
    private val stringNameMap = mutableMapOf<String, String>()
    private val strRegex = Regex("<(string|string-array) name=\"\\w+\">")
    private val needReplaceFileExtensionName = listOf(".xml", ".java", ".kt")
    private val mappingName = "string_guard_mapping.text"

    @TaskAction
    fun execute() {
        stringObfuscate()
        MappingOutputHelper.appendNewLan(project, mappingName, "string mapping")
        MappingOutputHelper.write(project, mappingName, stringNameMap)
    }

    private fun stringObfuscate() {
        val needSearchFileTree = project.files(findStringsFiles()).asFileTree
        val namesSet = mutableSetOf<String>()
        needSearchFileTree.forEach {
            namesSet.addAll(findStringsName(it.readText()))
        }
        val obfuscateNames =
            RandomNameHelper.genNames(namesSet.size, Pair(8, 12), allLetter = false)
        stringNameMap.putAll(namesSet.mapIndexed { index: Int, name: String ->
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
                stringNameMap.forEach { (raw, obfuscate) ->
                    text = text.replaceWords("R.string.$raw", "R.string.$obfuscate")
                        .replaceWords("@string/$raw", "@string/$obfuscate")
                        .replaceWords("R.array.$raw", "R.array.$obfuscate")
                        .replaceWords("<string name=\"$raw\">", "<string name=\"$obfuscate\">")
                        .replaceWords(
                            "<string-array name=\"$raw\">",
                            "<string-array name=\"$obfuscate\">"
                        )
                }
                file.writeText(text)
            }
        }
    }

    private fun findStringsFiles(): List<File> {
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

    private fun findStringsName(stringText: String): List<String> {
        val result = strRegex.findAll(stringText)
        return result.map { it.value.removePrefix("<").removeSuffix(">").split("\"")[1] }.toList()
    }
}