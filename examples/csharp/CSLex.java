import java.io.*;
import java.nio.file.Files;
import java.util.*;

import org.parsers.csharp.*;
import org.parsers.csharp.CSharpConstants.TokenType;

/**
 * A test harness for lexing C# files from
 * the command line.
 */
public class CSLex {

    static public ArrayList<Node> roots = new ArrayList<>();

    static public void main(String args[]) {
        List<File> failures = new ArrayList<>();
        List<File> successes = new ArrayList<>();
        if (args.length == 0) {
            usage();
        }
        List<File> files = new ArrayList<File>();
        boolean quiet = false;

        for (String arg : args) {
            if (arg.equals("-q")) {
                quiet = true;
                continue;
            }
            File file = new File(arg);
            if (!file.exists()) {
                System.err.println("File " + file + " does not exist.");
                continue;
            }
            addFilesRecursively(files, file);
        }
        long startTime = System.currentTimeMillis();
        int numTokens = 0;
        for (File file : files) {
            try {
                numTokens += tokenizeFile(file, quiet);
            } catch (Exception e) {
                System.err.println("Error processing file: " + file);
                e.printStackTrace();
                failures.add(file);
                continue;
            }
            System.out.println(file.getName() + " tokenized successfully.");
            successes.add(file);
        }
        if (!failures.isEmpty()) for (File file : failures) {
            System.out.println("Lexing failed on: " + file);
        }
        System.out.println("\nTokenized " + successes.size() + " files, containing " + numTokens + " tokens.");
        System.out.println("Failed on " + failures.size() + " files.");
        System.out.println("\nDuration: " + (System.currentTimeMillis() - startTime) + " milliseconds");
    }

    static public int tokenizeFile(File file, boolean quiet) throws IOException, ParseException {
        String content = CSharpConstants.stringFromBytes(Files.readAllBytes(file.toPath()), null);
        CSharpLexer lexer = new CSharpLexer(file.toString(), content);
        Token t = null;
        int numTokens = 0;
        do {
            t = lexer.getNextToken(t);
            if (t instanceof InvalidToken) {
                throw new ParseException(t);
            }
            if (!quiet) {
                String s = String.format("%s: %s %d %d %d %d", t.getType(),
                        t.getImage(),
                        t.getBeginLine(), t.getBeginColumn(),
                        t.getEndLine(), t.getEndColumn());
                System.out.println(s);
            }
            ++numTokens;
        }
        while (t.getType() != TokenType.EOF);
        return numTokens;
    }

    static public void addFilesRecursively(List<File> files, File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                addFilesRecursively(files, f);
            }
        } else if (file.getName().endsWith(".cs")) {
            files.add(file);
        }
    }


    static public void usage() {
        System.out.println("Usage: java CSLex <sourcefiles or directories>");
        System.exit(-1);
    }
}
