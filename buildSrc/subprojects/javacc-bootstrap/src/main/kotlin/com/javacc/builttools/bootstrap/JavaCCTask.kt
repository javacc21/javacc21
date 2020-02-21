package com.javacc.builttools.bootstrap

import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.property
import org.gradle.process.JavaExecSpec
import java.io.File
import javax.inject.Inject

open class JavaCCTask @Inject constructor(
    objectFactory: ObjectFactory
) : BaseJavaCCTask("javacc", objectFactory) {
    init {
        mainClass.convention("com.javacc.Main")
    }

    @Input
    val lookAhead = objectFactory.property<Int>().convention(1)

    @InputFile
    val inputFile = objectFactory.property<File>()

    @Optional
    @InputDirectory
    val templateSourceDirectory = objectFactory.directoryProperty()

    @get:InputFiles
    val grammarInputDirectory: File
        get() = inputFile.get().absoluteFile.parentFile

    override fun JavaExecSpec.configureJava() {
        val allSources = project.buildDir.resolve("javacc/$name-input")
        project.sync {
            into(allSources)
            from(grammarInputDirectory) {
                include("**/*.javacc")
            }
            if (templateSourceDirectory.isPresent) {
                from(templateSourceDirectory) {
                    include("**/*.ftl")
                }
            }
        }

        // The class is in the top-level package
        main = mainClass.get()
        args("-LOOKAHEAD:${lookAhead.get()}")
        args("-BASE_SRC_DIR:${output.get()}")
        args(allSources.resolve(inputFile.get().name))
    }
}
