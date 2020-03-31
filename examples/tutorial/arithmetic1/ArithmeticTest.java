import java.io.*;
import java.util.*;

public class ArithmeticTest {
    static public void main(String[] args) throws ParseException {
       ArithmeticParser parser = new ArithmeticParser(new java.io.InputStreamReader(System.in));
       parser.AdditiveExpression();
       Node root = parser.rootNode();
       System.out.println("Dumping the AST...");
       Nodes.dump(root, "  ");
       System.out.println("The result is: " + evaluate(root));
    }
    
    static double evaluate(Node node) {
        if (node instanceof NUMBER) {
            return Double.parseDouble(node.toString());
        }
        List<NUMBER> numbers = node.childrenOfType(NUMBER.class);
        double result = 0.0;
        for (NUMBER num : numbers) {
            result += evaluate(num);
        }
        return result;
    }
}
