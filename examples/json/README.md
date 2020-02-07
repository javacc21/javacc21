## JSON (and JSONC) JavaCC Parsers

The JSON.javacc is a pretty faithful implementation of the JSON grammar 
described [here](https://www.json.org/json-en.html)

Surprisingly, this core spec does not admit comments of any sort, so it turns out
that many JSON files *in the wild*, as it were, do have C-style comments in them.

So, I created a separated grammar JSONC, i.e. JSON with Comments! Well, this would
be a rather annoying thing to do in legacy JavaCC, since it would involve 
copying-pasting the JSON grammar to a new file and adding the extra bit for
handling the comments.

Both of these grammars make use of a couple of very new features in JavaCC 21. The first is a new option
called <pre>DEFAULT_LEXICAL_STATE</pre>. The idea should actually be fairly self-explanatory. The purpose of this
is that a grammar that is frequently INCLUDEd in other grammars defines its own default lexical state so that
any TOKEN productions in it do not automatically clobber things defined in the including grammar.

Another new feature that is used here is the ability to define a superclass/subclass relationship for Token subclasses.

For example, consider the following snippet:

<pre>
    TOKEN #Literal : 
    {
       <TRUE: "true"> #BooleanLiteral
       |
       <FALSE: "false"> #BooleanLiteral
       |
       <NULL: "null"> #NullLiteral 
...
    }   
</pre>

The effect of the *\#Literal* annotation is that a subclass of <code>Token</code> called <code>Literal</code>
is generated and any token defined in that block will be generated as an instance of <code>Literal</code>.

Now, optionally, if a Token itself also specifies a class name, as in the above <code>BooleanLiteral</code> and <code>NullLiteral</code> then 
those are taken to be subclasses of <code>Literal</code>.

Well, I'll leave it to the reader's imagine all the various ways that this can help you 
organize your code. In particular, in conjunction with the *code injection*
feature, you can put some code inside the <code>Literal</code> superclass and it would be available within
any subclass, such as, in this case, <code>BooleanLiteral</code> or </code>NullLiteral</code>.

<pre>
   INJECT(Literal) : {}
   {
      

   }
</pre>


## Building and Testing the JSON Parser

As for trying it out, there is an ant task (I know I'm dating myself...) that builds the whole thing. You
just go into the directory and type ant on the command-line. There is a <code>test</code> target such that
you can run:
<pre>
   ant test
</pre>

and it will parse the json files in the testfiles directory and spit out an indented text version of the generated AST's. Generally, you 
can also try it on any JSON file via:

<pre>
   java JSONTest (filenames)
</pre>

or

<pre> 
   java JSONCTest (filenames)
</pre>

to try it out with any JSON files. These test harnesses just output the AST in indented text form. You can
see that the JSONC parser handles C-style (or Java-style) comments while the JSON parser does not.

Actually, this is a fairly nice little example, and, in many cases, a JSON (or JSONC) parser 
could be something pretty useful that you can easily embed in your own grammars!
