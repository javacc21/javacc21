import com.github.vlsi.gradle.properties.dsl.props

plugins {
    `java-library`
    id("com.github.autostyle")
    id("com.gradle.plugin-publish") apply false
    id("com.github.vlsi.gradle-extensions")
    id("com.github.vlsi.ide")
    id("com.github.vlsi.stage-vote-release")
    kotlin("jvm") apply false
}

val String.v: String get() = rootProject.extra["$this.version"] as String

val buildVersion = "javacc".v + releaseParams.snapshotSuffix

description = "JavaCC21 is a parser/scanner generator for java"

println("Building JavaCC21 $buildVersion")

val enableGradleMetadata by props()
val skipJavadoc by props()
val skipAutostyle by props()
val slowSuiteLogThreshold by props(0L)
val slowTestLogThreshold by props(2000L)

releaseParams {
    tlp.set("javacc")
    organizationName.set("javacc21")
    componentName.set("javacc")
    prefixForProperties.set("gh")
    svnDistEnabled.set(false)
    sitePreviewEnabled.set(false)
    nexus {
        mavenCentral()
    }
    voteText.set {
        """
        ${it.componentName} v${it.version}-rc${it.rc} is ready for preview.

        Git SHA: ${it.gitSha}
        Staging repository: ${it.nexusRepositoryUri}
        """.trimIndent()
    }
}

allprojects {
    group = "com.javacc"
    version = buildVersion

    repositories {
        mavenCentral()
    }

    val javaMainUsed = file("src/main/java").isDirectory
    val javaTestUsed = file("src/test/java").isDirectory
    val javaUsed = javaMainUsed || javaTestUsed
    if (javaUsed) {
        apply(plugin = "java-library")
        dependencies {
            val compileOnly by configurations
            compileOnly("net.jcip:jcip-annotations:1.0")
            compileOnly("com.github.spotbugs:spotbugs-annotations:3.1.6")
            compileOnly("com.google.code.findbugs:jsr305:3.0.2")
        }
    }

    val kotlinMainUsed = file("src/main/kotlin").isDirectory
    val kotlinTestUsed = file("src/test/kotlin").isDirectory
    val kotlinUsed = kotlinMainUsed || kotlinTestUsed
    if (kotlinUsed) {
        apply(plugin = "java-library")
        apply(plugin = "org.jetbrains.kotlin.jvm")
        dependencies {
            add(if (kotlinMainUsed) "implementation" else "testImplementation", kotlin("stdlib"))
        }
    }

    if (javaUsed || kotlinUsed) {
        dependencies {
            val configurationName = when {
                javaMainUsed || kotlinMainUsed -> "implementation"
                else -> "testImplementation"
            }
            configurationName(platform(project(":javacc-dependencies-bom")))
        }
    }
    val hasTests = javaTestUsed || kotlinTestUsed
    if (hasTests) {
        // It activates test output rendering
        apply(plugin = "com.github.vlsi.gradle-extensions")

        // Add default tests dependencies
        dependencies {
            val testImplementation by configurations
            val testRuntimeOnly by configurations
            // Enable to write tests with JUnit5 API
            testImplementation("org.junit.jupiter:junit-jupiter-api")
            testImplementation("org.junit.jupiter:junit-jupiter-params")
            testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
        }
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        // Ensure builds are reproducible
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        dirMode = "775".toInt(8)
        fileMode = "664".toInt(8)
    }

    if (!skipAutostyle) {
        apply(plugin = "com.github.autostyle")
        autostyle {
            kotlinGradle {
                ktlint()
            }
            format("configs") {
                filter {
                    include("**/*.sh", "**/*.bsh", "**/*.cmd", "**/*.bat")
                    include("**/*.properties", "**/*.yml")
                    include("**/*.xsd", "**/*.xsl", "**/*.xml")
                    include("**/*.javacc", "**/*.ftl")
                    // Autostyle does not support gitignore yet https://github.com/autostyle/autostyle/issues/13
                    exclude("out/**")
                    exclude("target/*")
                    // TODO: remove this when .gitattributes are merged to master
                    exclude("scripts/*.bat")
                    if (project == rootProject) {
                        exclude("gradlew*")
                    }
                }
                trimTrailingWhitespace()
                endWithNewline()
            }
            format("markdown") {
                filter.include("**/*.md")
                endWithNewline()
            }
        }
    }

    plugins.withType<JavaPlugin> {
        configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_1_7
            targetCompatibility = JavaVersion.VERSION_1_7
            withSourcesJar()
            if (!skipJavadoc) {
                withJavadocJar()
            }
        }

        apply(plugin = "maven-publish")

        if (!enableGradleMetadata) {
            tasks.withType<GenerateModuleMetadata> {
                enabled = false
            }
        }

        if (!skipAutostyle) {
            autostyle {
                java {
                    // TODO: a blank line between comments looks nice, however there are violations for now
                    // replaceRegex("side by side comments", "(\n\\s*+[*]*+/\n)(/[/*])", "\$1\n\$2")
                    importOrder(
                        "static ",
                        "com.javacc.",
                        "freemarker.",
                        "java.",
                        ""
                    )
                    indentWithSpaces(4)
                    trimTrailingWhitespace()
                    endWithNewline()
                }
            }
        }

        tasks {
            withType<JavaCompile>().configureEach {
                options.encoding = "UTF-8"
            }

            withType<Jar>().configureEach {
                manifest {
                    attributes["Bundle-License"] = "BSD-3-Clause"
                    attributes["Implementation-Title"] = "JavaCC"
                    attributes["Implementation-Version"] = project.version
                    attributes["Specification-Vendor"] = "JavaCC"
                    attributes["Specification-Version"] = project.version
                    attributes["Specification-Title"] = "JavaCC"
                    attributes["Implementation-Vendor"] = "JavaCC"
                    attributes["Implementation-Vendor-Id"] = "com.javacc"
                }
            }
            withType<Javadoc>().configureEach {
                (options as StandardJavadocDocletOptions).apply {
                    noTimestamp.value = true
                    showFromProtected()
                    locale = "en"
                    docEncoding = "UTF-8"
                    charSet = "UTF-8"
                    encoding = "UTF-8"
                    docTitle = "JavaCC21 ${project.name} API"
                    windowTitle = "JavaCC21 ${project.name} API"
                    header = "<b>JavaCC</b>"
                    addBooleanOption("Xdoclint:none", true)
                    addStringOption("source", "7")
                    // TODO: compute lastEditYear
                    bottom =
                        "Copyright © 2006-???? Sun Microsystems, Inc, ????-2020 JavaCC development group"
                    if (JavaVersion.current() >= JavaVersion.VERSION_1_9) {
                        addBooleanOption("html5", true)
                        links("https://docs.oracle.com/javase/9/docs/api/")
                    } else {
                        links("https://docs.oracle.com/javase/7/docs/api/")
                    }
                }
            }
            withType<Test>().configureEach {
                useJUnitPlatform()
                testLogging {
                    showStandardStreams = true
                }
                // Pass the property to tests
                fun passProperty(name: String, default: String? = null) {
                    val value = System.getProperty(name) ?: default
                    value?.let { systemProperty(name, it) }
                }
                passProperty("junit.jupiter.execution.parallel.enabled", "true")
                passProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
                passProperty("junit.jupiter.execution.timeout.default", "5 m")
            }
            configure<PublishingExtension> {
                if (project.path == ":") {
                    // Do not publish "root" project. Java plugin is applied here for DSL purposes only
                    return@configure
                }
                if (project.path.startsWith(":javacc-dependencies-bom") ||
                    project.path.startsWith(":javacc-integration-tests")) {
                    // We don't publish examples to Maven Central
                    return@configure
                }
                publications {
                    // Gradle plugin is not yet in JavaCC tree, but it would require
                    // a slightly different publication
                    if (project.path != ":javacc-plugin-gradle") {
                        create<MavenPublication>(project.name) {
                            artifactId = project.name
                            version = rootProject.version.toString()
                            description = project.description
                            from(project.components.get("java"))
                        }
                    }
                    withType<MavenPublication> {
                        // if (!skipJavadoc) {
                        // Eager task creation is required due to
                        // https://github.com/gradle/gradle/issues/6246
                        //  artifact(sourcesJar.get())
                        //  artifact(javadocJar.get())
                        // }

                        // Use the resolved versions in pom.xml
                        // Gradle might have different resolution rules, so we set the versions
                        // that were used in Gradle build/test.
                        versionMapping {
                            usage(Usage.JAVA_RUNTIME) {
                                fromResolutionResult()
                            }
                            usage(Usage.JAVA_API) {
                                fromResolutionOf("runtimeClasspath")
                            }
                        }
                        pom {
                            withXml {
                                val sb = asString()
                                var s = sb.toString()
                                // <scope>compile</scope> is Maven default, so delete it
                                s = s.replace("<scope>compile</scope>", "")
                                // Cut <dependencyManagement> because all dependencies have the resolved versions
                                s = s.replace(
                                    Regex(
                                        "<dependencyManagement>.*?</dependencyManagement>",
                                        RegexOption.DOT_MATCHES_ALL
                                    ),
                                    ""
                                )
                                sb.setLength(0)
                                sb.append(s)
                                // Re-format the XML
                                asNode()
                            }
                            name.set(
                                (project.findProperty("artifact.name") as? String)
                                    ?: "JavaCC ${project.name.capitalize()}"
                            )
                            description.set(
                                project.description
                                    ?: "JavaCC ${project.name.capitalize()}"
                            )
                            inceptionYear.set("1996")
                            url.set("https://github.com/javacc21/javacc21")
                            licenses {
                                license {
                                    name.set("BSD-3-Clause")
                                    url.set("https://raw.githubusercontent.com/javacc21/javacc21/master/LICENSE")
                                    comments.set("BSD-3-Clause, Copyright (c) 2006, Sun Microsystems, Inc; Copyright (c)  2008-2019 Jonathan Revusky")
                                    distribution.set("repo")
                                }
                            }
                            issueManagement {
                                system.set("GitHub")
                                url.set("https://github.com/javacc21/javacc21/issues")
                            }
                            scm {
                                connection.set("scm:git:https://github.com/javacc21/javacc21.git")
                                developerConnection.set("scm:git:https://github.com/javacc21/javacc21.git")
                                url.set("https://github.com/javacc21/javacc21")
                                tag.set("HEAD")
                            }
                            organization {
                                name.set("javacc.com")
                                url.set("https://javacc.com")
                            }
                            mailingLists {
                                mailingList {
                                    name.set("Users")
                                    archive.set("https://discuss.parsers.org/")
                                }
                            }
                            developers {
                                developer {
                                    name.set("Jonathan Revusky")
                                    id.set("revusky")
                                    email.set("revusky@javacc.com")
                                    roles.add("Owner")
                                    timezone.set("0")
                                    organization.set("javacc.com")
                                    organizationUrl.set("https://javacc.com")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
