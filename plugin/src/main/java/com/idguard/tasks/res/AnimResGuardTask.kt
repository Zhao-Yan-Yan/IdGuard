package com.idguard.tasks.res

import com.idguard.utils.MappingOutputHelper
import com.idguard.utils.RandomNameHelper
import com.idguard.utils.findDirsInRes
import com.idguard.utils.getExtensionName
import com.idguard.utils.getRealName
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
open class AnimResGuardTask @Inject constructor(
    private val variantName: String,
) : BaseResGuardTask(variantName) {

    private val nameMap = mutableMapOf<String, String>()
    private val needReplaceFileExtensionName = listOf(".xml", ".java", ".kt")
    private val mappingName = "anim_guard_mapping.text"

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
            val rawName = it.getRealName()
            namesSet.add(rawName)
        }
        val obfuscateNames = RandomNameHelper.genNames(namesSet.size, Pair(8, 12), allLetter = true)
        nameMap.putAll(namesSet.mapIndexed { index: Int, name: String ->
            name to obfuscateNames[index]
        })
        val needReplaceFiles = findNeedReplaceFiles(
            "anim",
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

                nameMap.forEach { (raw, obfuscate) ->
                    text = text
                        .replaceWords("R.anim.$raw", "R.anim.$obfuscate")
                        .replaceWords("@anim/$raw", "@anim/$obfuscate")
                }
                file.writeText(text)
            }
        }

        needSearchFileTree.forEach {
            val obfuscateFilePath =
                it.parent + File.separator + nameMap[it.getRealName()]!! + it.getExtensionName()
            it.renameTo(File(obfuscateFilePath))
        }
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
}