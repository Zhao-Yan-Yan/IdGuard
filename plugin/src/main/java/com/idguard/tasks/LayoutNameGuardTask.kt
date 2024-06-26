package com.idguard.tasks

import com.idguard.utils.MappingOutputHelper
import com.idguard.utils.RandomNameHelper
import com.idguard.utils.findLayoutDirs
import com.idguard.utils.findLayoutUsagesInRes
import com.idguard.utils.findPackageName
import com.idguard.utils.getFileName
import com.idguard.utils.isAndroidProject
import com.idguard.utils.javaDirs
import com.idguard.utils.replaceWords
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

open class LayoutNameGuardTask @Inject constructor(
    private val variantName: String,
) : DefaultTask() {

    init {
        group = "guard"
    }

    //key raw ,value obfuscate
    private val layoutROMap = mutableMapOf<String, String>()

    private val mappingName = "layout_guard_mapping.text"

    @TaskAction
    fun execute() {
        val layoutDirs = mutableListOf<File>()
        project.rootProject.subprojects {
            if (it.isAndroidProject()) {
                layoutDirs.addAll(it.findLayoutDirs(variantName))
            }
        }
        //println(layoutDirs)

        val layoutDirFileTree = project.rootProject.files(layoutDirs).asFileTree
        val allObsNames = RandomNameHelper.genNames(layoutDirFileTree.files.size, allLetter = true)
        //混淆map生成
        layoutDirFileTree.forEachIndexed { index, file ->
            //println(file)
            val layoutFileName = file.name
            //println("file name $layoutFileName")
            val fileParentPath = file.parentFile.absolutePath
            val obfuscateName = allObsNames[index]
            layoutROMap[file.absolutePath] =
                fileParentPath + File.separator + obfuscateName + ".xml"
        }
        //println(layoutROMap)

        //混淆layout xml里的layout引用
        val needReplaceResFile = mutableListOf<File>()
        project.rootProject.subprojects {
            if (it.isAndroidProject()) {
                needReplaceResFile.addAll(it.findLayoutUsagesInRes(variantName))
            }
        }
        project.rootProject.subprojects { project ->
            if (!project.isAndroidProject()) {
                return@subprojects
            }
            val needReplaceResFileTree = project.files(needReplaceResFile).asFileTree
            needReplaceResFileTree.forEach { file ->
                var fileText = file.readText()
                layoutROMap.forEach { (raw, obfuscate) ->
                    val rawName = raw.getFileName()
                    val obfuscateName = obfuscate.getFileName()
                    //@layout/activity_main
                    fileText = fileText.replaceWords("@layout/$rawName", "@layout/$obfuscateName")
                }
                file.writeText(fileText)
            }
        }
        //混淆layout名称
        val layoutFiles = layoutDirFileTree.files.toSet()
        layoutFiles.forEach { file ->
            val obfuscateFilePath = layoutROMap[file.absolutePath]
                ?: throw RuntimeException("layout dir has changed !!!")
            val obfuscateFile = File(obfuscateFilePath)
            obfuscateFile.writeText(file.readText())
            file.delete()
        }
        val packageName = project.findPackageName()
        project.rootProject.subprojects { project ->
            if (!project.isAndroidProject()) {
                return@subprojects
            }
            //混淆java 或者 kotlin文件对layout引用
            val javaFileTree = project.javaDirs(variantName)
            project.files(javaFileTree).asFileTree.forEach { javaFile ->
                var javaFileText = javaFile.readText()

                layoutROMap.forEach { (raw, obfuscate) ->
                    val rawName = raw.getFileName()
                    val obfuscateName = obfuscate.getFileName()
                    javaFileText =
                        javaFileText.replaceWords("R.layout.$rawName", "R.layout.$obfuscateName")
                            .replaceBindingWords(packageName, rawName, obfuscateName)
                }
                javaFile.writeText(javaFileText)
            }
        }

        val readableMap = layoutROMap.map {
            "R.layout.${it.key.getFileName()}" to "R.layout.${it.value.getFileName()}"
        }.toMap()

        MappingOutputHelper.write(project, mappingName, readableMap)
    }

    private fun String.replaceBindingWords(packageName: String, rawName: String, obfuscateName: String): String {
        val rawBinding = rawName.layoutAsBinding()
        val obfuscateBinding = obfuscateName.layoutAsBinding()
        val importBindingRegex = Regex("import $packageName.(databinding|viewbinding).*Binding")
        return this.replace(importBindingRegex) {
            it.value.replace(rawBinding, obfuscateBinding)
        }.replaceWords(rawBinding, obfuscateBinding)
    }

    private fun String.layoutAsBinding(): String {
        return split("_").joinToString("") {
            it.replaceFirstChar { char -> char.titlecase() }
        }.plus("Binding")
    }
}