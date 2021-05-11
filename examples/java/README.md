Use the build.xml in this directory to build a Java source code parser. This is the same Java Parser that is
used in JavaCC itself to handle embedded java code in JavaCC grammar files. In fact, the JavaCC.javacc grammar file 
in the src/main/grammars directory simply uses the grammar file in this directory via an [INCLUDE](https://doku.javacc.com/doku.php?id=include).

The JParse.java in the top-level directory can be used as a test harness. Try it as follows:

     java JParse file1.java file2.java...

or:

     java JParse <directory>

in which case it runs over all the .java files in the directory. This main routine has the somewhat 
odd feature that, if there is only one source file as an argument, it also outputs the parse tree to stdout.

You can see it in action by simply running:

ant test

REPORTING BUGS
--------------

If you find bugs in the grammar, please write to revusky@javacc.com
