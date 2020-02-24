## JavaCC Examples

This directory contains various examples which can actually be independently useful. 
  * The java directory gives an example of how to use the Java grammar that JavaCC itself uses.
  * The freemarker directory contains a grammar for FTL (FreeMarker Template Language) which is intended to eventually replace the crufty old grammar that FreeMarker currently uses! There is a separate FEL.javacc file (FEL being FreeMarker Expression Language) which could be separately useful for people in their own projects.
  * The JSON grammar is quite simple and can be *included* in your own grammar via the INCLUDE mechanism. Actually, you can see a simple INCLUDE in action by inspecting the JSONC.javacc grammar.
  * The tutorial directory contains code for the very early draft of a tutorial that you can see [here](https://javacc.com/tutorial/)

The directory legacy-examples contains some very old (I mean VERY old, like older than some of the people reading this!) examples that were included with the legacy JavaCC tool. I mostly include them so as to have a test suite that older grammars still work. (Though they may require a bit of tweaking here and there, admittedly.)
