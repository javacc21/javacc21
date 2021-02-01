This is a first pass on a preprocessor. It is completely based on the C# 
preprocessor. However, it is not a complete implementation. It doesn't 
recognize the #line or the #pragma or #warning or #error directives.
It just handles the #if/#elif/#else and the #def/#undef stuff. It also
doesn't support the full unicode definition of an identifier. Just 
7-bit alpha characters. (That's temporary.)

It's set up to work without even building an AST. It just parses the file
and generates a BitSet that indicates the lines to be ignored (or not.)
