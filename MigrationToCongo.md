Existing users of [JavaCC 21](https://javacc.com/) are strongly encouraged to migrate to CongoCC, which is a continuation of development by the same team. CongoCC is now quite rock solid, no less so than the last builds of JavaCC 21. It already self-bootstraps (i.e. is used to build itself) and runs all the same integrated functional tests -- principally, parsers for Java, Python, and CSharp. So I would strongly encourage people to move to using Congo now. I am really quite sure that there is no reason not to. (You can pick up [a prebuilt jarfile](https://parsers.org/download/congocc-full.jar). Just try it and I think you'll see that the disruption is quite minimal.

Note, however, that CongoCC is not fully backward compatible with JavaCC 21. So let me outline the various issues in migrating:

Doubtless the biggest single issue in migrating is that CongoCC removes support for all the legacy JavaCC syntax. Most of the various issues can be dealt with automatically using the syntax converter that is in [recent versions of JavaCC 21](https://parsers.org/download/javacc-full.jar). 

     java -jar javacc-full.jar convert grammarfile 

There is some chance that this is all you need to do. However, it is more likely, at least for non-trivial projects, that your project will not build without a few extra little tweaks. Most (probably all) of this just amounts to adjusting the various import statements. Here is a rundown of the relevant changes to be aware of: 

- The tool now *always* generates two packages if you have tree building enabled: the parser package and the node package.
- You *cannot* generate code into the default/unnamed package. If you do not specify a *parser package* or a *node package* they will be generated, as described below.
- There is no `XXXConstants` interface any more. If any of your own code explicitly refers to that, you will naturally need to do some adjustment. 
- The `BaseNode` class is now generated in the node package, not the parser package. I think was a design mistake before and I take the opportunity to fix it. In any case, what with all the various import adjustments, this is just one more.

Another point that should be mentioned is that you may need to put the option `LEGACY_GLITCHY_LOOKAHEAD=true` at the top of your grammar file, if you never made any adjustment for that. This is explained [here](https://parsers.org/javacc21/nested-lookahead-redux/). Aside from the legacy glitchy lookahead issue (that that is now off by default) it really seems to me that if you manage to get your project to build with CongoCC, it should work just as well as before. (But if that is not the case, by all means do report back!)


