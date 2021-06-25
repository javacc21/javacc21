import static java.lang.Character.*;
import java.text.Normalizer;

/**
 * This is a surprisingly complicated little utility to generate
 * all the list of character ranges for the start and continuation
 * of a Python identifier. As far as I can tell, this generates 
 * the character ranges for the identifier definition found here: 
 * https://docs.python.org/3/reference/lexical_analysis.html#identifiers
 */

public class PythonIdentifierGen {

	static public void main(String[] args) {
		System.out.println("TOKEN :");
        System.out.println("  <#PYTHON_IDENTIFIER_START :");
        System.out.println("    [");
		outputRanges(0, 0x10ffff, true);
		System.out.println("\n    ]");
		System.out.println("\n  >");
		System.out.println("  |");
        System.out.println("  <#PYTHON_IDENTIFIER_PART :");
        System.out.println("    [");
		outputRanges(0, 0x10ffff, false);
		System.out.println("\n    ]");
		System.out.println("  >");
		System.out.println(";");
	}

    static boolean isIdStart(int codePoint) {
        if (codePoint == '_') return true;
        return ((((1 << UPPERCASE_LETTER) |
            (1 << LOWERCASE_LETTER) |
            (1 << TITLECASE_LETTER) |
            (1 << MODIFIER_LETTER) |
            (1 << OTHER_LETTER) |
            (1<< LETTER_NUMBER)) >> getType(codePoint)) & 1)
            != 0;
    }

    static boolean isIdContinue(int codePoint) {
        if (codePoint == '_') return true;
        return ((((1 << UPPERCASE_LETTER) |
        (1 << LOWERCASE_LETTER) |
        (1 << TITLECASE_LETTER) |
        (1 << MODIFIER_LETTER) |
        (1 << OTHER_LETTER) |
        (1<< LETTER_NUMBER) |
        (1<< NON_SPACING_MARK)|
        (1<< COMBINING_SPACING_MARK) |
        (1<< DECIMAL_DIGIT_NUMBER) |
        (1<< CONNECTOR_PUNCTUATION)) >> getType(codePoint)) & 1)
        != 0;
    }


    static boolean isPythonIdentifierStart(int ch) {
        if (!isIdStart(ch)) {
            return false;
        }
        int[] codePoints = getNormalizedCodePoints(ch);
        if (codePoints.length == 1 && codePoints[0] == ch) return true;
        for (int i=0; i< codePoints.length; i++) {
            int c = codePoints[i];
            if (i==0) {
                if (!isIdStart(c)) {
                    return false;
                }
            }
            else {
                if (!isPythonIdentifierPart(c)) {
                    return false;
                }
            }
        }
        return true;
    }

    static int[] getNormalizedCodePoints(int ch) {
        StringBuilder buf = new StringBuilder();
        buf.appendCodePoint(ch);
        String normalizedForm = Normalizer.normalize(buf, Normalizer.Form.NFKC);
        return normalizedForm.codePoints().toArray();
    }

    static boolean isPythonIdentifierPart(int ch) {
        if (!isIdContinue(ch)) {
            return false;
        }
        int[] codePoints = getNormalizedCodePoints(ch);
        for (int i=0; i<codePoints.length; i++) {
            int c = codePoints[i];
            if (!isIdContinue(c)) {
                return false;
            }
        }
        return true;
    }

	static void outputRanges(int start, int end, boolean justStart) {
		int lhs=start;
		boolean firstLine = true;
		for (int ch = start+1; ch<=end ;ch++) {
			boolean prevID = justStart ? isPythonIdentifierStart(ch-1) : isPythonIdentifierPart(ch-1);
			boolean currentID = justStart ? isPythonIdentifierStart(ch) : isPythonIdentifierPart(ch);
			if (prevID != currentID) {
				if (currentID) {
					lhs = ch;
				} else {
					if (!firstLine) {
						System.out.print(",\n");
					}
					firstLine = false;
					outputRange(lhs, ch-1);
				}
			}
		}
	}

	static void outputRange(int left, int right) {
		System.out.print("        ");
		String output = toUnicodeRep(left);
		if (left != right) {
			output += "-";
			output += toUnicodeRep(right);
		}
		System.out.print(output);
	}

	static String toUnicodeRep(int ch) {
		if (ch <= 0xFFFF) {
			String hex = Integer.toString(ch, 16);
			int leadingZeros = 4-hex.length();
			switch (leadingZeros) {
				case 1 : hex = "0" + hex; break;
				case 2 : hex = "00" +hex; break;
				case 3 : hex = "000" + hex;
			}
			return "\"\\u" + hex + "\"";
	    }
		char high = Character.highSurrogate(ch);
		char low = Character.lowSurrogate(ch);
		String highRep = toUnicodeRep(high);
		String lowRep = toUnicodeRep(low);
		return highRep.substring(0, highRep.length()-1) + lowRep.substring(1, lowRep.length());
	}
}