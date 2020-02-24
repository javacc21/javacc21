gradlePlugin {
    plugins {
        register("javacc-bootstrap") {
            id = "javacc-bootstrap"
            implementationClass = "com.javacc.builttools.bootstrap.JavaCCPlugin"
        }
    }
}
