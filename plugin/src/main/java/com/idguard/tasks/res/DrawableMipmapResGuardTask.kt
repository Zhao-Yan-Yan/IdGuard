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
 * @date 2024/3/20 16:56
 */
open class DrawableMipmapResGuardTask @Inject constructor(
    private val variantName: String,
) : BaseResGuardTask(variantName) {
    private val drawableNameMap = mutableMapOf<String, String>()
    private val needReplaceFileExtensionName = listOf(".xml", ".java", ".kt")
    private val mappingName = "drawable_mipmap_guard_mapping.text"

    @TaskAction
    fun execute() {
        drawableObfuscate()
        MappingOutputHelper.appendNewLan(project, mappingName, "drawable mapping")
        MappingOutputHelper.write(project, mappingName, drawableNameMap)
    }

    private fun drawableObfuscate() {
        val drawableFileTree = project.files(findNeedSearchFiles()).asFileTree
        val drawableNameSet = mutableSetOf<String>()
        drawableFileTree.forEach {
            val rawName = it.getRealName()
            drawableNameSet.add(rawName)
        }
        val drawableObfuscateNames =
            RandomNameHelper.genNames(drawableNameSet.size, Pair(6, 10), allLetter = true)
        drawableNameMap.putAll(drawableNameSet.mapIndexed { index: Int, name: String ->
            name to drawableObfuscateNames[index]
        })
        val needReplaceFiles = findNeedReplaceFiles(
            "drawable",
            "mipmap",
            "values",
            "layout",
            "menu"
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
                drawableNameMap.forEach { (raw, obfuscate) ->
                    text = text.replaceWords("R.drawable.$raw", "R.drawable.$obfuscate")
                        .replaceWords("@drawable/$raw", "@drawable/$obfuscate")
                        .replaceWords("R.mipmap.$raw", "R.mipmap.$obfuscate")
                        .replaceWords("@mipmap/$raw", "@mipmap/$obfuscate")
                }
                file.writeText(text)
            }
        }
        drawableFileTree.forEach {
            val obfuscateFilePath =
                it.parent + File.separator + drawableNameMap[it.getRealName()]!! + it.getExtensionName()
            it.renameTo(File(obfuscateFilePath))
        }
    }

    private fun findNeedSearchFiles(): List<File> {
        val resDirs = mutableListOf<File>()
        project.rootProject.subprojects {
            if (it.isAndroidProject()) {
                resDirs.addAll(it.findDirsInRes(variantName, "drawable", "mipmap"))
            }
        }
        return resDirs.toList()
    }

}