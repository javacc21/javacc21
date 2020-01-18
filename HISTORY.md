## JavaCC History

JavaCC was a Java-based parser generator released by Sun Microsystems in 1996. It was a tool much along the lines of the venerable YACC and Bison implemented in C on Unix systems. As best as one can deduce, no work was done on JavaCC after about 1998 or 1999 at the latest.

In 2003, to some fanfare, Sun released JavaCC a liberal open source license (BSD) on the (now defunct) [java.net](https://en.wikipedia.org/wiki/Java.net) platform. Ostensibly, some community formed around it. However, no real development on the tool ever really took place.

In 2008, the author of these lines (Jon Revusky) became quite interested in trying to revive the project and add some interesting features. However, the people in control of the project on java.net refused to even consider any ideas or any of the substantial work on the code that Revusky was offering them. This, despite the objectively verifiable fact that in the five years since the code had been available on Java.net, no development had taken place -- or, if any had, it was at most the amount of work that single competent, motivated developer might do in a day or two. Perhaps even more damning was the clear fact that the "JavaCC" team had no roadmap for further development.

Having put a lot of effort into this, Revusky released his forked version under the name "FreeCC". FreeCC fixed some of the most glaring problems with JavaCC, such as the way tree building was implemented as a separate preprocessort tool (JJTree) and also introduced an INCLUDE directive and the code injection feature that allows one to include code in any of the generated AST node files. Under the hood, there was a massive cleanup of the way the tool worked. The JavaCC tool used <code>println</code> statements to output Java source code, and FreeCC moved all of this to separate template files. 

Revusky's original intention was to donate all of this body of work to the *canonical* JavaCC project on java.net. However, his offer was rebuffed. (Not exactly very graciously either.) 

Towards the end of 2019, Revusky decided to pick up the FreeCC project and get it towards a stable release. He updated the tool to handle embedded Java code using all the more modern constructs through Java 8. (And some newer constructs introduced after Java 8 are supported as well, but the Java language support up to Java 8 is comprehensive.) FreeCC had some small community of users in 2009/2010. Revusky went through the bug tracker and nailed every reported bug. FreeCC 0.9.4 was released on 28 December 2019. 

On reflection, the decision was made to rename FreeCC as "JavaCC 21" and simply refer to it as "JavaCC". This is based on the realization that it is simply absurd to allow people to sit on a well known open source project indefinitely with no real plans for further development.

It is really quite obvious that the people in control of the canonical JavaCC project forfeited any moral right to the exclusive use of the name long ago -- assuming that they really ever had any such right to start with.

