import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.parsers.java.*;
import org.parsers.java.JavaConstants.TokenType;

/**
 * A test harness for lexing Java files from 
 * the command line.
 */
public class JLex {
    
    static public ArrayList<Node> roots= new ArrayList<>();

   static public void main(String args[]) {
      List<File> failures = new ArrayList<File>();
      List<File> successes = new ArrayList<File>();    
      if (args.length == 0) {
        usage();
      }
      List<File> files = new ArrayList<File>();
      for (String arg : args) {
          File file = new File(arg);
          if (!file.exists()) {
              System.err.println("File " + file + " does not exist.");
              continue;
          }
	   addFilesRecursively(files, file);
      }
      long startTime = System.currentTimeMillis();
      int numTokens =0;
      for (File file : files) {
          try {
              numTokens+=tokenizeFile(file);
          } 
          catch (Exception e) {
              System.err.println("Error processing file: " + file);
              e.printStackTrace();
              failures.add(file);
              continue;
          }
          System.out.println(file.getName()  + " tokenized successfully.");
          successes.add(file);
       }
       if (!failures.isEmpty()) for (File file : failures) {
           System.out.println("Lexing failed on: " + file);
       }
       System.out.println("\nTokenized " + successes.size() + " files, containing " + numTokens + " tokens.");
       System.out.println("Failed on " + failures.size() + " files.");
       System.out.println("\nDuration: " + (System.currentTimeMillis() - startTime) + " milliseconds");
    }
      
   static public int tokenizeFile(File file) throws IOException, ParseException {
       String content = new String(Files.readAllBytes(file.toPath()));
       JavaLexer lexer = new JavaLexer(file.toString(), content);
       Token t = null;
       int numTokens = 0;
       do {
           t = lexer.getNextToken();
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
       }
       else if (file.getName().endsWith(".java") && !file.getName().endsWith("-info.java")) {
           files.add(file);
       }
   }
   
   
   static public void usage() {
       System.out.println("Usage: java JLex <sourcefiles or directories>");
       System.exit(-1);
   }
}
