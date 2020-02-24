import com.javacc.builttools.bootstrap.BaseJavaCCTask
import com.javacc.builttools.bootstrap.JavaCCTask

plugins {
    `javacc-bootstrap`
    id("com.github.vlsi.ide")
}

dependencies {
    implementation(files("$rootDir/bin/freemarker.jar"))
}

val javacc by tasks.registering(JavaCCTask::class) {
    description = "Generate the Java CC Main Parser"
    mainClass.set("javacc.Main")
    inputFile.set(file("src/main/javacc/JavaCC.javacc"))
    templateSourceDirectory.set(file("src/main/ftl"))
}

tasks.jar {
    into("com/javacc/output/java") {
        from("src/main/ftl") {
            include("**/*.ftl")
        }
    }
}

ide {
    tasks.withType<BaseJavaCCTask> {
        generatedJavaSources(this, output.get().asFile)
    }
}
