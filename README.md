# JavaCC 21 Preview 1

Tagged and Released, ?? January 2020

I (revusky) decided to dust off this old unfinished open source project, abandoned since early 2009. 

There were four publicly released versions of FreeCC back when, on Google Code, which is now defunct. There is an archived version of the site [here](https://code.google.com/archive/p/freecc/).

The last version released in January 2009 was labeled 0.9.3. Therefore I have taken up that naming, and the current release is 0.9.4. Version numbers are quite arbitrary, of course. These version numbers are somewhat misleading as regards the maturity of the tool. FreeCC is based on forking the JavaCC codebase in April of 2008. That version of JavaCC was 4.1, as I recall, and this 0.9.x version of FreeCC is a (vastly) more advanced version of that JavaCC tool. I noticed that the JavaCC people put out more versions since then, labeling it JavaCC 6.0. But a quick comparison of that with the version available in 2008 shows that there is virtually no diffeerence.

The original intention was not to fork a new project, but to contribute all of these enhancements to JavaCC. However, in their infinite wisdom, the maintainers of the project declined the contribution. Actually, they even declined to look at it!

## Changes in the new resuscitated codebase

I have marked this as a milestone because I finally got the Java grammar updated to support the new constructs, I believe up through Java 8. I have tested this on a huge body of java source, including the thousands of files of Java source code in the JDK and in open source projects such as JRuby, Jython, and FreeMarker, and this java parser can handle all of it. Literally millions of lines of code.

The included Java grammar is useful on it own and also is embedded in the FreeCC grammar, so FreeCC can handle Lambda expressions, which, as far as I know, JavaCC still cannot do.

FreeCC is not very well documented at the moment. There is some rudimentary information on the [Wiki pages associated with the project on Github](https://github.com/revusky/freecc/wiki). However, I consider that to be a stopgap measure and would like to have something more like real documentation.

It should be possible to check the code out and just do an ant build from the top directory, by just running ant. You should also be able to run a little test suite by running "ant test".

If you are interested in this project, either as a user or as a developer, please do [write me](a href="mailto:revuskyNOSPAM@gmail.com").
