import java.io.*;
import jsonc.*;

public class JSONCTest {
    static public void parseFile(File file, boolean dumpTree) throws IOException, ParseException {
        FileReader fr = new FileReader(file);
        JSONCParser parser = new JSONCParser(fr);
        parser.setInputSource(file.toString());
        parser.Value();
        Node root=parser.rootNode();
        if (dumpTree) {
            Nodes.dump(root, "");
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
