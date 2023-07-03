package com.idguard

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

open class IdGuardTask @Inject constructor(
    private val variantName: String,
) : DefaultTask() {
    init {
        group = "guard"
    }

    //raw , obfuscate
    private val idNameMap = mutableMapOf<String, String>()
    private val regex = Regex("\"@\\+id/\\w+\"")

    @TaskAction
    fun execute() {
        val layoutDirs = mutableListOf<File>()
        project.rootProject.subprojects {
            if (it.isAndroidProject()) {
                layoutDirs.addAll(it.findLayoutDirs(variantName))
            }
        }
        val layoutDirFileTree = project.files(layoutDirs).asFileTree
        val nameSet = mutableSetOf<String>()
        layoutDirFileTree.forEach {
            nameSet.addAll(findXMLIds(it))
        }
        val obNames = RandomNameHelper.genNames(nameSet.size, Pair(6, 9))
        val map = nameSet.mapIndexed { index: Int, key: String ->
            key to obNames[index]
        }
        idNameMap.putAll(map.toMap())
        println(idNameMap)

        //替换xml中的id
        layoutDirFileTree.forEach {
            var fileText = it.readText()
            idNameMap.forEach { (raw, obfuscate) ->
                fileText = fileText.replaceWords("@+id/$raw", "@+id/$obfuscate")
                    .replaceWords("@id/$raw", "@id/$obfuscate")
            }
            it.writeText(fileText)
        }
    }

    private fun findXMLIds(file: File): Set<String> {
        val xmlText = file.readText()
        val matchResult = regex.findAll(xmlText).toList()
        return matchResult.map { it.value.removeSurrounding("\"").split("/")[1] }.toSet()
    }
}