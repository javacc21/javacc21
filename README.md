# Important Notice regarding JavaCC 21

JavaCC 21 has been rebranded as [CongoCC](https://github.com/congo-cc/congo-parser-generator). That is where all further work and new features are being developed. This repository will likely be archived at some point fairly soon. 

Existing JavaCC 21 users are *strongly* recommended to migrate any existing projects based on JavaCC 21 to Congo. There may be a few hitches, but it should be quite straightforward. 

As for new projects, they should definitely use CongoCC from the outset, and really, people who are looking for a tool with these general characteristics and have never previously used JavaCC 21 or legacy JavaCC have *zero* reason to use anything other than CongoCC. (*I hope that is clear!*)

The text on the remainder of this page has been edited to reflect the name change.

# ~~JavaCC 21~~ CongoCC

~~JavaCC 21~ CongoCC is a continuation of development on JavaCC, which was, in turn, on the JavaCC codebase that was open-sourced by Sun Microsystems in mid 2003. It is currently the most advanced version of JavaCC. (*By far.*) It has many feature enhancements (with more to come soon) and also generates much more modern, readable Java code.

The overall history of this project is rather entangled and anybody interested can read [a more detailed history here](https://wiki.parsers.org/doku.php?id=ancient_history). This branch of development was originally released under the name FreeCC in 2008. 

A list of the main features (in particular, wrt *legacy JavaCC*) follows:

## Up-to-date Java language support

CongoCC includes support for the Java language through JDK ~~8~~ ~~13~~ ~~15~~ ~~16~~ 17. See [here](https://parsers.org/roadmap/milestone-javacc-21-now-supports-the-java-language-up-to-jdk-13/), [here](https://parsers.org/announcements/jdk-14-now-fully-supported-new-switch-syntax/) and [here](https://parsers.org/javacc21/announcement-jdk17-python310/). (As of this writing, Java language support in legacy JavaCC is stalled at the JDK 7 level.)

Note also that the [Java grammar that ~~JavaCC 21~~ CongoCC uses internally](https://github.com/congo-cc/congo-parser-generator/blob/main/examples/java/Java.ccc) can be used in your own projects without any restriction.

## Major Bugfix! Nested Syntactic Lookahead now works correctly!

A longstanding limitation of JavaCC has been that syntactic lookahead does not nest, i.e. work recursively. This has been an issue in JavaCC for 24 years and was never addressed, and surely caused the tool to be less generally useful than it could have been, since attempts to do anything at all sophisticated would typically use recursive lookahead and would simply not work.

This is now fixed in ~~JavaCC 21~~ CongoCC and you can read about it [here](https://parsers.org/announcements/nested-syntactic-lookahead-works/).

Another longstanding pain point for developers was this "Code too Large" problem. In particular, if you wrote a very large, complex lexical specification, one would easily run into this. This has now been completely vanquished and you can see [here](https://parsers.org/javacc21/code-too-large-problem-fixed/) if you want more of the gory details. But the bottom line is that the whole issue has been completely addressed in JavaCC 21 (and its successor CongoCC).

## Streamlined Syntax

Though the *legacy* syntax is still supported (and will be for the indefinite future), ~~JavaCC 21~~ CongoCC offers a [streamlined syntax](https://wiki.parsers.org/doku.php?id=new_syntax_summary) that should be easier to read and write. Sometimes the improvement in readability is dramatic. (*N.B. Note that CongoCC, unlike JavaCC 21, does not support the legacy JavaCC syntax.)

For example, where you would previously have written:

     void FooBar() :
     {}
     {
         Foo() Bar()
     }

with the streamlined syntax, you can now write:

     FooBar : Foo Bar;

Cumbersome aspects of the legacy `LOOKAHEAD` construct have been streamlined. See [here.](https://wiki.parsers.org/doku.php?id=scan_statement) Again, the newer syntax sometimes affords dramatic improvements in clarity. Where you would have written:

     LOOKAHEAD(Foo() Bar() Baz()) Foo() Bar() Baz()

in ~~JavaCC 21~ CongoCC, you can write:

     Foo Bar Baz =>||

(*N.B. In CongoCC, you __must__ write it that way, since the legacy `LOOKAHEAD` syntax is gone.*)     

The [new up-to-here marker](https://parsers.org/announcements/new-feature-for-scan-up-to-here/) also offers great gains in readability and maintainability. For example, you can now write:

    Foo Bar =>|| Baz

This means that you scan ahead up to the `=>||` or in other words, you check for `Foo Bar` and if that is successful, you parse the full expansion `Foo Bar Baz`. Using the legacy syntax, this was written as:

    LOOKAHEAD(Foo() Bar()) Foo() Bar() Baz()

    LOOKAHEAD (3) FooBar()

We believe that most people will prefer the streamlined version:

    SCAN 3 FooBar

## Contextual Predicates

A *contextual predicate* is used to check whether we are at a given point in a parse. For example:

     SCAN \...\Foo => Bar

would mean that we can only enter a `Bar` production if we are already in a `Foo`. Or, for example:

     SCAN ~\...\Foo => Foo

would mean that we can only enter the `Foo` production if we are *not* already in a `Foo`. (Or in other words, `Foo` is *not re-entrant*).

Granted, it was possible to express these things in legacy JavaCC but it was quite cumbersome and error-prone.

## INCLUDE statement and Updated Java Grammar

~~JavaCC 21~~ CongoCC provides a much needed [INCLUDE instruction](https://wiki.parsers.org/doku.php?id=include) that allows you to break up a large grammar into multiple physical files. [Here, for example,](https://github.com/congo-cc/congo-parser-generator/blob/main/examples/json/JSONC.ccc) is what CongoCC's JSONC (JSON with comments) grammar looks like. It simply INCLUDEs the regular (without comments) [JSON grammar that is here](https://github.com/congo-cc/congo-parser-generator/blob/main/examples/json/JSON.ccc).

The INCLUDE feature is used to very good effect in the internal code of ~~JavaCC 21~~ CongoCC itself. The [embedded Java grammar](https://github.com/congo-cc/congo-parser-generator/blob/main/examples/java/Java.ccc) is simply [INCLUDEd in the CongoCC grammar](https://github.com/congo-cc/congo-parser-generator/blob/main/src/grammars/CongoCC.ccc#L441)

Since the Java grammar stands alone, it can be used freely in separate projects that require a Java grammar.

## Preprocessor

~~JavaCC 21~~ CongoCC has a preprocessor that is quite similar to the preprocessor in Microsoft's C# programming language. It only implements the `#define/#undef` and `#if/#elif/#else/#endif` constructs that are used to conditionally turn on and off ranges of lines in the input file. See [here](https://parsers.org/tips-and-tricks/javacc-21-has-a-preprocessor/) for more information.

## Tree Building

~~JavaCC 21~~ CongoCC is based on the view that building an AST (*Abstract Syntax Tree*) is the *normal* usage of this sort of tool. While the legacy JavaCC package does contain automatic tree-building functionality, i.e. the JJTree preprocessor, JJTree has some (very) longstanding usability issues that ~~JavaCC 21~~ CongoCC addresses.

For one thing, a lot of what makes JJTree quite cumbersome to use is precisely that it is a preprocessor! In ~~JavaCC 21~~ CongoCC, all of the JJTree functionality is simply in the core tool and the generated parser builds an AST by default. (Tree building can be turned off however.)

(*NB. ~~JavaCC 21~~ CongoCC use the same syntax for tree-building annotations as JJTree.*)

One of the most annoying aspects of JJTree was that it had no disposition for *injecting* code into a generated Node subclass. This is actually rather odd, because the core JavaCC tool does allow you to inject code into the generated parser or lexer class (via <code>PARSER_BEGIN...PARSER_END</code> and <code>TOKEN_MGR_DECLS</code> respectively) but whoever implemented JJTree somehow did not understand the need for this. Presumably, you are supposed to generate your <code>ASTXXX.java</code> files and then, if you want to put any functionality into them, to post-edit them. (*Except... then... how do you do a clean rebuild of your project?*)

Thus, ~~JavaCC 21~~ CongoCC has an [INJECT statement](https://wiki.parsers.org/doku.php?id=include) that allows you to *inject* Java code into any generated file (including <code>Token.java</code> or <code>ParseException.java</code>) thus doing away with the unwieldy *antipattern* of post-editing generated files.

Aside from the lack of any ability to inject code into generated ASTXXX classes, legacy JJTree has another strangely half-baked aspect: *Tokens are not Nodes!*. Surely, the most natural thing would be to have Tokens implement the Node API as well, so that you can build a tree in which the terminal nodes are the Token objects themselves. Well, as you might anticipate, JavaCC 21 (and its successor CongoCC) does allow this. Also, by default, JavaCC 21 generates subclasses to represent the various Token types. (Again, this is the default, but can be turned off.)

## Better Generated Code

~~JavaCC 21~~ CongoCC generates more readable code generally. Certain things have been modernized significantly. Consider the <code>Token.kind</code> field in the <code>Token.java</code> generated by the legacy tool. That field is an integer and is also (contrary to well known best practices) publicly accessible. In the <code>Token.java</code> file that JavaCC 21 generates, the <code>Token.getType()</code> returns a [type-safe Enum](https://docs.oracle.com/javase/8/docs/api/java/lang/Enum.html) and all of the code in the generated parser that previously used integers to represent the type of Token, now uses type-safe Enums.

Code generated by legacy JavaCC gave just about zero information about where the generated code originated. Parsers generated by ~~JavaCC 21 CongoCC have line/column information (*relative to the real source file, the grammar file*) and they also inject information into the stack trace generated by ParseException that contain line/column information relative to the grammar file.

The current ~~JavaCC 21~~ CongoCC codebase itself is the result of a massive refactoring/cleanup. Code generation has been externalized to FreeMarker templates. To get an idea of what this looks like in practice, here is [the main template that generates Java code for grammatical productions](https://github.com/congo-cc/congo-parser-generator/blob/main/src/templates/java/ParserProductions.java.ftl).

## Assorted Usability Enhancements

In general, ~~JavaCC 21~~ CongoCC has more sensible default settings and is [much more usable out-of-the-box](https://wiki.parsers.org/doku.php?id=convention_over_configuration).

## Robust Up-to-date Grammars/Parsers for Python and C#

~~JavaCC 21~~ CongoCC includes robust, up-to-date grammars for the latest stable versions of [Python](https://github.com/congo-cc/congo-parser-generator/blob/main/examples/python/Python.ccc) and [C Sharp](https://github.com/congo-cc/congo-parser-generator/blob/main/examples/csharp/CSharp.ccc). Both of these grammars can be used and adapted in your own projects without any restriction.


## ~~JavaCC 21~~ CongoCC is actively developed!

Perhaps most importantly, the project is again under active development.

Now that the project is active again, users can expect significant new features fairly soon. Given that code generation has been externalized to template files, the ability to generate parsers in other languages is probably not very far off. Another near-term major goal is to provide support for *fault-tolerant* parsing, where a parser incorporates heuristics for building an AST even when the input is invalid (unbalanced delimiters, missing semicolon and such).

One way to stay up to date with the ~~JavaCC 21~~ CongoCC project is to subscribe to [our blog newsfeed](https://parsers.org/feed) in any newsreader.

Usage is really quite simple. As described [here](https://parsers.org/home/) CongoCC is invoked on the command line via:

    java -jar congocc-full.jar MyGrammar.ccc

The latest source code can be checked out from Github via:

    git clone https://github.com/congo-cc/congo-parser-generator.git

And then you can do a build by invoking ant from the top-level directory. You should also be able to run the test suite by running "ant test".

If you are interested in this project, either as a user or as a developer, feel free to join us on our [Gitter chat channel](https://gitter.im/javacc21/javacc21). You can also sign up on our [Flarum forum](https://discuss.congocc.org/) and post any questions or suggestions there.
