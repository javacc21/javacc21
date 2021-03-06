import java.io.*;
import java.util.*;
import org.parsers.java.*;


/**
 * A test harness for parsing Java files from 
 * the command line.
 */
public class JParse {
    
    static public ArrayList<Node> roots= new ArrayList<>();
    static boolean tolerantParsing;

   static public void main(String args[]) {
      List<File> failures = new ArrayList<File>();
      List<File> successes = new ArrayList<File>();    
      if (args.length == 0) {
        usage();
      }
      List<File> files = new ArrayList<File>();
      for (String arg : args) {
          if (arg.equals("-t")) {
              tolerantParsing = true;
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
      for (File file : files) {
          try {
             // A bit screwball, we'll dump the tree if there is only one arg. :-)
//	     for (int i=0;i<30;i++)
              parseFile(file, files.size() == 1);
          } 
          catch (Exception e) {
              System.err.println("Error processing file: " + file);
              e.printStackTrace();
	      failures.add(file);
              continue;
          }
          System.out.println(file.getName()  + " parsed successfully.");
          successes.add(file);
       }
       for (File file : failures) {
           System.out.println("Parse failed on: " + file);
       }
       System.out.println("\nParsed " + successes.size() + " files successfully");
       System.out.println("Failed on " + failures.size() + " files.");
       System.out.println("\nDuration: " + (System.currentTimeMillis() - startTime) + " milliseconds");
       //System.out.println("JAVA_MOVE_COUNT: " + JavaLexer.moveCallCount_JAVA);
    }
      
   static public void parseFile(File file, boolean dumpTree) throws IOException, ParseException {
       JavaParser parser = new JavaParser(file.toPath());
       parser.setParserTolerant(tolerantParsing);
       Node root=parser.CompilationUnit();
       FileLineMap.clearFileLineMapCache();
// Uncomment the following code if you want all the parsed trees 
//  to remain in memory. This is useful if you want to know how much
//  memory it takes to parse all the source code in the JDK, for example.
//  (About 8GB if we're talking about JDK 13)
//       roots.add(root);
//       if (roots.size() % 1000 == 0) {
//            System.out.println("-----------------------------------------------");
//            System.out.println("Parsed "  +  roots.size() + " files.");
//            System.out.println("-----------------------------------------------");
//       }
       
       if (dumpTree) {
           root.dump("");
       }
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
       System.out.println("Usage: java JParse <sourcefiles or directories>");
       System.out.println("If you just pass it one java source file, it dumps the AST");
       System.out.println("Use the -t flag to set whether to parse in tolerant mode");
       System.exit(-1);
   }
}
