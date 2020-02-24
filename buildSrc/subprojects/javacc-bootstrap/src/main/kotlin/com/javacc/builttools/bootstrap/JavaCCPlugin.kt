package com.javacc.builttools.bootstrap

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType

open class JavaCCPlugin : Plugin<Project> {
    companion object {
        const val JAVACC_CLASSPATH_CONFIGURATION_NAME = "javaccClaspath"
        const val GENERATE_SOURCES_TASK_NAME = "generateSources"
    }

    override fun apply(target: Project) {
        target.configureJavaCC()
    }

    fun Project.configureJavaCC() {
        configurations.create(JAVACC_CLASSPATH_CONFIGURATION_NAME) {
            isCanBeConsumed = false
        }.defaultDependencies {
            add(dependencies.create(files("$rootDir/bin/javacc.jar")))
        }

        tasks.register(GENERATE_SOURCES_TASK_NAME) {
            description = "Generates sources (e.g. JavaCC)"
            dependsOn(tasks.withType<JavaCCTask>())
        }
    }
}
