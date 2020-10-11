Currently, this project is severely undermanned. Our current policy is not to waste much (ideally, *any*) man-hours on meaningless formalisms. And, to be clear, *releases are mostly a meaningless formalism.* A release is simply a snapshot of the code at any particular point in time.

If, in whatever context, we must talk about so-called *releases* or * *versions*, the *version* of JavaCC 21 you are using is exactly the same thing as the date of the build that you are using. As I write these lines, that is 23 September 2020. If you type:

    java -jar javacc-full.jar

on the command line, the first two lines it outputs are:

    JavaCC 21 Parser Generator (javacc-full.jar built by revusky on 2020-09-23)

If you absolutely must talk about *releases* or *version numbers*, then the date of the build is considered to be that.    

There is no technical support available (from us, anyway!) for anything but the most recent version. That is the version that can be downloaded at https://javacc.com/download/javacc-full.jar.

Well, alternatively, if you are actually getting your hands dirty in the JavaCC code itself, your own build could also be considered the most recent version, *as long as you are in synch with the most recent version fo the code!*, e.g.

    git pull
    ant clean full-jar

## Needed Clarification

Now, to be clear, we do understand that, in the case of very large systems, like the JDK or the Linux Kernel or whatever, all this stuff about releases and versioning becomes quite important. This is particularly true if you are integrating a large number of subsystems. However, for projects of a quite moderate scale, like FreeMarker or JavaCC, which are what I will be working on mainly in the coming period, this stuff is mostly like a swamp you can get stuck in.

Much of this dates back to the pre-Internet days of shrink-wrapped software.  However, the real origin of this is by analogy with the engineering of hard physical products. If you bought a washing machine in 2018, it was presumably the 2018 model. Or maybe the 2017 model. This can be resolved by checking the serial number and so forth. But you have whatever model you have and you can't go to Github and "check out" the latest one. Ergo, there has to be technical support for the model you actually own. (A bit of a rant. To be continued...)


