/**
 * Class to represent a file with line numbers
 */
class RawInput {
    static String readToEnd(Reader reader) throws IOException {
		char[] block = new char[BUF_SIZE];
		int charsRead = reader.read(block);
		if (charsRead < 0) {
			throw new IOException("No input");
		}
		else if (charsRead < BUF_SIZE) {
			char[] result = new char[charsRead];
			System.arraycopy(block, 0, result, 0, charsRead);
			return new String(block, 0, charsRead);
		}
		StringBuilder buf = new StringBuilder();
     	buf.append(block);
		do {
	     	charsRead = reader.read(block);
	     	if (charsRead >0) {
	     		buf.append(block, 0, charsRead);
	     	}
		} while (charsRead == BUF_SIZE);
		return buf.toString();
	}
}