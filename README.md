# JavaCC 21

[JavaCC 21](https://javacc.com/) is a continuation of development on the JavaCC codebase that was open-sourced by Sun Microsystems in mid 2003. It is currently the most advanced version of JavaCC. It has many feature enhancements (with more to come soon) and also generates much more modern, readable Java code.

The overall history of this project is rather entangled and anybody interested can read [a more detailed history here](https://doku.javacc.com/doku.php?id=ancient_history). This branch of development was originally released under the name FreeCC in 2008. However, it is really quite clear in retrospect that the FreeCC naming simply created confusion and we have decided on the *JavaCC 21* name to make it quite clear that this is simply a more advanced version of the JavaCC tool originally released by Sun. (*NB. The "21" in JavaCC 21 is not a version number. It is simply part of the name and means that this is JavaCC for the 21st century!*)

A list of the main features (in particular, wrt *legacy JavaCC*) follows:

## Up-to-date Java language support

JavaCC 21 includes support for the Java language through JDK 13. See [here](https://javacc.com/2020/03/22/milestone-javacc-21-now-supports-the-java-language-up-to-jdk-13/). (As of this writing, Java language support in legacy JavaCC is stalled at the JDK 7 level.)

Note also that the [Java grammar that JavaCC 21 uses internally](https://github.com/JavaCC21/JavaCC21/blob/master/src/main/grammars/Java.javacc) can be used in your own projects without any restriction.

## Major Bugfix! Nested Syntactic Lookahead now works correctly!

A longstanding limitation of JavaCC has been that syntactic lookahead does not nest, i.e. work recursively. This has been an issue in JavaCC for 24 years and was never addressed, and surely caused the tool to be less generally useful than it could have been, since attempts to do anything at all sophisticated would typically use recursive lookahead and would simply not work.

This is now fixed in JavaCC 21 and you can read about it [here](https://javacc.com/2020/07/15/nested-syntactic-lookahead-works/).

## Streamlined Syntax

Though the *legacy* syntax is still supported (and will be for the indefinite future), JavaCC 21 offers a [streamlined syntax](https://doku.javacc.com/doku.php?id=new_syntax_summary) that should be easier to read and write. Sometimes the improvement in readability is dramatic.

For example, where you would previously have written:

     void FooBar() :
     {}
     {
         Foo() Bar()
     }

with the streamlined syntax, you can now write: 

     FooBar : Foo Bar;

Cumbersome aspects of the legacy `LOOKAHEAD` construct have been streamlined. See [here.](https://doku.javacc.com/doku.php?id=scan_statement) Again, the newer syntax sometimes affords dramatic improvements in clarity. Where you would have written:

     LOOKAHEAD(Foo() Bar() Baz()) Foo() Bar() Baz()

in JavaCC 21, you can write:

     => Foo Bar Baz

The [new up-to-here marker](https://javacc.com/2020/07/31/new-feature-for-scan-up-to-here/) also offers great gains in readability and maintainability. For example, you can now write:
    
    Foo Bar =>|| Baz

This means that you scan ahead up to the `=>||` or in other words, you check for `Foo Bar` and if that is successful, you parse the full expansion `Foo Bar Baz`. Using the legacy syntax, this would be written as:

     LOOKAHEAD(Foo() Bar()) Foo() Bar() Baz()

The above older syntax still works, of course, so you can use the one you prefer! By the same token, you can still write:

    LOOKAHEAD (3) FooBar()

but we believe that most people will prefer the streamlined version:

    SCAN 3 FooBar

## New Lookbehind construct

*Lookbehind* refers to a way of writing predicates that check whether we are at a given point in a parse. For example:

     SCAN \...\Foo => Bar

would mean that we can only enter a `Bar` production if we are already in a `Foo`. Or, for example:

     SCAN ~\...\Foo => Foo

would mean that we can only enter the `Foo` production if we are *not* already in a `Foo`. (Or in other words, `Foo` is *not re-entrant*).

Granted, it was possible to express these things in legacy JavaCC but it was quite cumbersome and error-prone.

## INCLUDE statement and Updated Java Grammar

JavaCC 21 provides a much needed [INCLUDE instruction](https://doku.javacc.com/doku.php?id=include) that allows you to break up a large grammar into multiple physical files. [Here, for example,](https://github.com/JavaCC21/JavaCC21/blob/master/examples/json/JSONC.javacc) is what JavaCC 21's JSONC (JSON with comments) grammar looks like. It simply INCLUDEs the regular (without comments) [JSON grammar that is here](https://github.com/JavaCC21/JavaCC21/blob/master/examples/json/JSON.javacc).

The INCLUDE feature is used to very good effect in the internal code of JavaCC 21 itself. The [embedded Java grammar](https://github.com/JavaCC21/JavaCC21/blob/master/src/main/grammars/Java.javacc) is simply [INCLUDEd in the JavaCC grammar](https://github.com/JavaCC21/JavaCC21/blob/master/src/main/grammars/JavaCC.javacc#413)

Since the Java grammar stands alone, it can be used freely in separate projects that require a Java grammar. 

## Tree Building

JavaCC 21 is based on the view that building an AST (*Abstract Syntax Tree*) is the *normal* usage of this sort of tool. While the legacy JavaCC package does contain automatic tree-building functionality, i.e. the JJTree preprocessor, JJTree has some (very) longstanding usability issues that JavaCC 21 addresses. 

For one thing, a lot of what makes JJTree quite cumbersome to use is precisely that it is a preprocessor! In JavaCC 21, all of the JJTree functionality is simply in the core tool and the generated parser builds an AST by default. (Tree building can be turned off however.)

(*NB. JavaCC 21 uses the same syntax for tree-building annotations as JJTree.*)

One of the most annoying aspects of JJTree was that it had no disposition for *injecting* code into a generated Node subclass. This is actually rather odd, because the core JavaCC tool does allow you to inject code into the generated parser or lexer class (via <code>PARSER_BEGIN...PARSER_END</code> and <code>TOKEN_MGR_DECLS</code> respectively) but whoever implemented JJTree somehow did not understand the need for this. Presumably, you are supposed to generate your <code>ASTXXX.java</code> files and then, if you want to put any functionality into them, to post-edit them. (*Except... then... how do you do a clean rebuild of your project?*) 

Thus, JavaCC 21 has an [INJECT statement](https://doku.javacc.com/doku.php?id=include) that allows you to *inject* Java code into any generated file (including <code>Token.java</code> or <code>ParseException.java</code>) thus doing away with the unwieldy *antipattern* of post-editing generated files.

Aside from the lack of any ability to inject code into generated ASTXXX classes, legacy JJTree has another strangely half-baked aspect: *Tokens are not Nodes!*. Surely, the most natural thing would be to have Tokens implement the Node API as well, so that you can build a tree in which the terminal nodes are the Token objects themselves. Well, as you might anticipate, JavaCC 21 does allow this. Also, by default, JavaCC 21 generates subclasses to represent the various Token types. (Again, this is the default, but can be turned off.)

## Better Generated Code

JavaCC 21 generates more readable code generally. Certain things have been modernized significantly. Consider the <code>Token.kind</code> field in the <code>Token.java</code> generated by the legacy tool. That field is an integer and is also (contrary to well known best practices) publicly accessible. In the <code>Token.java</code> file that JavaCC 21 generates, the <code>Token.getType()</code> returns a [type-safe Enum](https://docs.oracle.com/javase/8/docs/api/java/lang/Enum.html) and all of the code in the generated parser that previously used integers to represent the type of Token, now uses type-safe Enums. 

Code generated by legacy JavaCC gave just about zero information about where the generated code originated. Parsers generated by JavaCC 21 have line/column information (*relative to the real source file, the grammar file*) and they also inject information into the stack trace generated by ParseException that contain line/column information relative to the grammar file.

If you want to compare side-by-side code generated by legacy JavaCC with that generated by JavaCC 21, [see this page](https://www.freemarker.es/2020/05/08/generatd-parsers-ymtd/).

The current JavaCC 21 codebase itself is the result of a massive refactoring/cleanup. Code generation has been externalized to [FreeMarker](https://freemarker.es/) templates. To get an idea of what this looks like in practice, here is [the main template that generates Java code for grammatical productions](https://github.com/JavaCC21/JavaCC21/blob/master/src/main/resources/templates/java/ParserProductions.java.ftl).

## Assorted Usability Enhancements

In general, JavaCC 21 has more sensible default settings and is [much more usable out-of-the-box](https://doku.javacc.com/doku.php?id=convention_over_configuration).

## JavaCC 21 is actively developed!

Perhaps most importantly, the project is again under active development.  

Now that the project is active again, users can expect significant new features fairly soon. Given that code generation has been externalized to template files, the ability to generate parsers in other languages is probably not very far off. Another near-term major goal is to provide support for *fault-tolerant* parsing, where a parser incorporates heuristics for building an AST even when the input is invalid (unbalanced delimiters, missing semicolon and such).

One by-product of the ongoing work on fault-tolerant parsing is a new ATTEMPT/RECOVER statement that is [described here](https://javacc.com/2020/05/03/new-experimental-feature-attempt-recover/).

One way to stay up to date with the JavaCC 21 project is to subscribe to [our blog newsfeed](https://javacc.com/feed) in any newsreader. 

Usage is really quite simple. As described [here](https://javacc.com/home/) JavaCC 21 is invoked on the command line via:

    java -jar javacc-full.jar MyGrammar.javacc

The latest source code can be checked out from Github via:

    git clone https://github.com/JavaCC21/JavaCC21.git

And then you can do a build by invoking ant from the top-level directory. You should also be able to run the test suite by running "ant test".

If you are interested in this project, either as a user or as a developer, you may [write me]("mailto:revusky@NOSPAMjavacc.com"). Better yet, you can sign up on our [Discourse forum](https://discuss.parsers.org/) and post any questions or suggestions there.
