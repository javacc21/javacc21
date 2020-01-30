## JSON (and JSONC) JavaCC Parsers

The JSON.javacc is a pretty faithful implementation of the JSON grammar 
described [here](https://www.json.org/json-en.html)

Surprisingly, this core spec does not admit comments of any sort, so it turns out
that many JSON files *in the wild*, as it were, do have C-style comments in them.

So, I created a separated grammar JSONC, i.e. JSON with Comments! Well, this would
be a rather annoying thing to do in legacy JavaCC, since it would involve 
copying-pasting the JSON grammar to a new file and adding the extra bit for
handling the comments.

There is an ant task (I know I'm dating myself...) that builds the whole thing. You
just go into the directory and type ant on the command-line. And then you can run
either:
   java JSONTest (filenames)
or 
   java JSONCTest (filenames)

to try it out. These test harnesses just output the AST in indented text form. You can
see that the JSONC parser handles C-style (or Java-style) comments while the 
JSON parser does not.

Actually, this is a fairly nice little example, and a JSON (or JSONC) parser 
actually is something pretty useful!

