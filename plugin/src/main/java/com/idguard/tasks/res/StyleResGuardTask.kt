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
 * @date 2024/3/20 20:42
 */
open class StyleResGuardTask @Inject constructor(
    private val variantName: String,
) : BaseResGuardTask(variantName) {

    private val nameMap = mutableMapOf<String, String>()
    private val needReplaceFileExtensionName = listOf(".xml", ".java", ".kt")
    private val mappingName = "style_guard_mapping.text"
    private val styleRegex = Regex("<style name=\"(.*?)\"( parent=\"(.*?)\")?>")

    @TaskAction
    fun execute() {
        colorObfuscate()
        MappingOutputHelper.appendNewLan(project, mappingName, "anim mapping")
        MappingOutputHelper.write(project, mappingName, nameMap)
    }

    private fun colorObfuscate() {
        val needSearchFileTree = project.files(findNeedSearchFiles()).asFileTree
        val namesSet = mutableSetOf<String>()
        needSearchFileTree.forEach {
            namesSet.addAll(findName(it.readText()))
        }
        val obfuscateNames = RandomNameHelper.genNames(namesSet.size, Pair(8, 12), allLetter = false, isFirstLetter = true)
        nameMap.putAll(namesSet.mapIndexed { index: Int, name: String ->
            name to obfuscateNames[index]
        })
        val needReplaceFiles = findNeedReplaceFiles(
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

                nameMap.forEach { (raw, obfuscate) ->
                    println("$raw, $obfuscate")

                    text = text
                        .replaceWords("<style name=\"$raw\"", "<style name=\"$obfuscate\"")
                        .replaceWords("@style/$raw", "@style/$obfuscate")
                        .replaceStyleInCodeWords(raw, obfuscate)
                }
                file.writeText(text)
            }
        }
    }

    private fun findName(stringText: String): List<String> {
        val result = styleRegex.findAll(stringText)
        val styles = result.map {
            it.groupValues[1] to it.groupValues[3]
        }.toList()
        return result.map {
            println("findName ${it.value}")
            it.value.removePrefix("<").removeSuffix(">").split("\"")[1]
        }.toList()
    }

    private fun findNeedSearchFiles(): List<File> {
        val resDirs = mutableListOf<File>()
        project.rootProject.subprojects {
            if (it.isAndroidProject()) {
                resDirs.addAll(it.findDirsInRes(variantName, "values"))
            }
        }
        return resDirs.toList()
    }


    private fun String.replaceStyleInCodeWords(raw: String, obfuscate: String): String {
        val codeRaw = raw.replace(".", "_")
        val codeObfuscate = obfuscate.replace(".", "_")
        return replaceWords("R.style.$codeRaw", "R.style.$codeObfuscate")
    }
}