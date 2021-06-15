import org.parsers.preprocessor.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.BitSet;

public class PPTest {
    static public void main(String[] args) throws Exception {
       if (args.length==0) {
           usage();
       }
       File file = new File(args[0]);
       if (!file.exists()) {
           System.out.println("File " + args[0] + " does not exist!");
           System.exit(-1);
       }
       String content = new String(Files.readAllBytes(file.toPath()),Charset.forName("UTF-8"));
       PreprocessorParser parser = new PreprocessorParser(file.toPath().toString(), content);
       BitSet lines = parser.PP_Root();
       outputLines(content, lines);
    }

    static public void outputLines(String content, BitSet lineMarkers) {
        String[] lines = content.split("\r\n|\n|\r");
        for (int i=0; i<lines.length; i++) {
            String line = lines[i];
            if (lineMarkers.get(i+1)) System.out.println(line);
        }
    }

    static public void usage() {
        System.out.println("Usage: java PPTest <sourcefile>");
        System.out.println("The program just outputs the file applying the preprocessor logic.");
        System.exit(-1);
    }
 
}