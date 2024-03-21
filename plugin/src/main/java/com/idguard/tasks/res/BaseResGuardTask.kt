package com.idguard.tasks.res

import com.idguard.utils.findDirsInRes
import com.idguard.utils.isAndroidProject
import com.idguard.utils.javaDirs
import com.idguard.utils.manifestFile
import groovy.transform.Internal
import org.gradle.api.DefaultTask
import java.io.File
import javax.inject.Inject

/**
 * @Description
 * @author yanz
 * @date 2024/3/20 16:56
 */
open class BaseResGuardTask @Inject constructor(
    private val variantName: String,
) : DefaultTask() {
    init {
        group = "guard"
    }

    fun findNeedReplaceFiles(vararg dirNames: String): List<File> {
        val dirs = mutableListOf<File>()
        project.rootProject.subprojects {
            if (it.isAndroidProject()) {
                dirs.addAll(
                    it.findDirsInRes(
                        variantName,
                        *dirNames
                    )
                )
            }
        }
        project.rootProject.subprojects { project ->
            if (!project.isAndroidProject()) {
                return@subprojects
            }
            dirs.addAll(project.javaDirs(variantName))
            dirs.add(project.manifestFile())
        }
        return dirs.toList()
    }
}