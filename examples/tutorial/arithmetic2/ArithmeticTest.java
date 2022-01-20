import java.io.*;
import java.util.*;

public class ArithmeticTest {
    static public void main(String[] args) throws ParseException {
       ArithmeticParser parser = new ArithmeticParser(new InputStreamReader(System.in));
//       parser.AdditiveExpression();
       parser.Root();
       Node root = parser.rootNode();
       System.out.println("Dumping the AST...");
       root.dump();
       System.out.println("The result is: " + evaluate(root));
    }
    
    static double evaluate(Node node) {
        if (node instanceof NUMBER) {
            return Double.parseDouble(node.toString());
        }
        else if (node instanceof ParentheticalExpression) {
            return evaluate(node.getChild(1));
        }
        Iterator<Node> iterator = node.iterator();
        double result = evaluate(iterator.next());
        while (iterator.hasNext()) {
            Node operator = iterator.next();
            double nextValue = evaluate(iterator.next());
            if (operator instanceof PLUS) {
                result += nextValue;
            }
            else if (operator instanceof MINUS) {
                result -= nextValue;
            }
            else if (operator instanceof TIMES) {
                result *= nextValue;
            }
            else if (operator instanceof DIVIDE) {
                result /= nextValue;
            }
        }
        return result;
    }
}
