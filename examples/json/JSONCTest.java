import java.io.*;
import org.parsers.jsonc.*;

public class JSONCTest {
    static public void parseFile(File file, boolean dumpTree) throws IOException, ParseException {
        JSONCParser parser = new JSONCParser(file.toPath());
        parser.Root();
        Node root=parser.rootNode();
        if (dumpTree) {
            root.dump();
        }
    }
 
    static public void main(String[] args) throws Exception {
      if (args.length == 0) {
        usage();
      }
      else {
        for (String arg :args) {
          File f = new File(arg);
          try {
            parseFile(f, true);
          }
          catch (Exception e) {
            System.err.println("Error parsing file: " + f);
            e.printStackTrace();
          }
        }
      }
    }

    static public void usage() {
      System.out.println("Little test harness for JSONC Parser");
      System.out.println("java JSONCTest <filename>");
    }
}
