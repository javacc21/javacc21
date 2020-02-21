# JavaCC 21 20.02.20

Tagged and Released, 20 February 2020

[JavaCC 21](https://javacc.com/) is a continuation of development on the JavaCC codebase released by Sun Microsystems in mid 2003. This development fork was originally released under the name FreeCC in 2008. However, it is really quite clear in retrospect that the FreeCC naming simply created confusion and we have decided on the *JavaCC 21* name to make it quite clear that this is simply a more advanced version of the JavaCC tool originally released by Sun.

The current JavaCC 21 codebase is the result of a massive refactoring/cleanup, mostly carried out in 2008. Code generation has been externalized to [FreeMarker](https://freemarker.es/) templates. The embedded Java grammar has been updated to support comprehensively Java language constructs through Java 8. It provides a much needed [INCLUDE instruction](https://doku.javacc.com/doku.php?id=include) that allows you to break up a large grammar into multiple physical files. 

The legacy JavaCC and JJTree tools have been merged into a single application and the generated parser builds an AST by default. (Though tree building can be optionally turned off.) There is a new [INJECT facility](https://doku.javacc.com/doku.php?id=include) that allows you to *inject* Java code into generated files, thus doing away with the unwieldy *antipattern* of post-editing generated files. In general, JavaCC 21 has more sensible default settings and is [much more usable out-of-the-box](https://doku.javacc.com/doku.php?id=convention_over_configuration).

Perhaps most importantly, the project is again under active development.  

FreeCC had four public releases on the [now defunct Google Code site](https://code.google.com/archive/p/freecc/), the last of which being version 0.9.3, released in January of 2009. Development resumed at the end of 2019 and a 0.9.4 release was [published on Github](https://github.com/revusky/freecc/releases) on 28 December 2019.

The project is again being actively developed. Given that code generation has been externalized to template files, the ability to generate parsers in other languages is probably not very far off. Another near-term major goal is to provide support for *fault-tolerant* parsing, where a parser incorporates heuristics for building an AST even when the input is invalid (unbalanced delimiters, missing semicolon and such).

To stay up to date with JavaCC 21, you are encouraged to consult [the wiki](https://doku.javacc.com/) and we would encourage people to sign up on our [Discourse forum](https://discuss.parsers.org). 

As described [here](https://javacc.com/) JavaCC 21 is invoked on the command line via:

    java -jar javacc.jar 

FreeCC is not very well documented at the moment. There is some rudimentary information on the [Wiki pages associated with the project on Github](https://github.com/revusky/freecc/wiki). However, I consider that to be a stopgap measure and would like to have something more like real documentation.

It should be possible to check the code out and just do an ant build from the top directory, by just running ant. You should also be able to run a little test suite by running "ant test".

If you are interested in this project, either as a user or as a developer, please do [write me](a href="mailto:revusky@NOSPAMjavacc.com").
