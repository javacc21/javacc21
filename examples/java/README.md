Use the build.xml in this directory to build a Java source code parser. This is the same Java Parser that is
used in JavaCC itself to handle embedded java code in JavaCC grammar files. The Java.javacc file is in 
the src/grammars directory but generates its code relative to this directory, in packages javagrammar
and javagrammar.nodes.

The generated JavaParser class contains a main() routine that can be used as a test harness. Try it as follows:

java javagrammar.JavaParser file1.java file2.java...

or:

java javagrammar.JavaParser <directory>

in which case it runs over all the .java files in the directory. This main routine has the somewhat 
odd feature that, if there is only one source file as an argument, it also outputs the parse tree to stdout.

You can see it in action by simply running:

ant test

REPORTING BUGS
--------------

If you find bugs in the grammar, please write to revusky@javacc.com
