/* Copyright (c) 2008-2020 Jonathan Revusky, revusky@javacc.com
 * Copyright (c) 2006, Sun Microsystems Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notices,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name Jonathan Revusky, Sun Microsystems, Inc.
 *       nor the names of any contributors may be used to endorse or promote
 *       products derived from this software without specific prior written
 *       permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.javacc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.javacc.JavaCCError.ErrorCode;
import com.javacc.parser.JavaCCParser;
import com.javacc.parser.Node;
import com.javacc.parser.ParseException;

public class JavaCCAssert {

	// Validation Assert

	private static class JavaCCErrorReporterForTest implements JavaCCErrorReporter {

		private final List<JavaCCError> errors;

		public JavaCCErrorReporterForTest() {
			errors = new ArrayList<>();
		}

		@Override
		public void reportError(JavaCCError error) {
			errors.add(error);
		}

		public List<JavaCCError> getErrors() {
			return errors;
		}
	}

	public static class SimpleError {

		private final ErrorCode code;

		private final String message;

		public SimpleError(ErrorCode code, String message) {
			this.code = code;
			this.message = message;
		}

		public ErrorCode getCode() {
			return code;
		}

		public String getMessage() {
			return message;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((code == null) ? 0 : code.hashCode());
			result = prime * result + ((message == null) ? 0 : message.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SimpleError other = (SimpleError) obj;
			if (code != other.code)
				return false;
			if (message == null) {
				if (other.message != null)
					return false;
			} else if (!message.equals(other.message))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "code:" + code + "\n, message:" + message;
		}

	}

	public static void testValidationFor(String fileName, String content, SimpleError... expectedErrors) {
		JavaCCErrorReporterForTest reporter = new JavaCCErrorReporterForTest();
		try {
			parseGrammar(fileName, content, reporter);
			List<SimpleError> actualErrors = reporter.getErrors().stream() //
					.map(error -> new SimpleError(error.getCode(), error.toString())) //
					.collect(Collectors.toList());
			assertArrayEquals(expectedErrors, actualErrors.toArray());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static SimpleError err(ErrorCode code, String message) {
		return new SimpleError(code, message);
	}

	public static Node parseGrammar(String fileName, String content) throws IOException, ParseException {
		return parseGrammar(fileName, content, null);
	}

	public static Node parseGrammar(String fileName, String content, JavaCCErrorReporter reporter)
			throws IOException, ParseException {
		Grammar grammar = new Grammar(new JavaCCOptions((Grammar) null));
		grammar.setFilename(fileName);
		if (reporter != null) {
			grammar.setReporter(reporter);
		}
		JavaCCParser parser = new JavaCCParser(grammar, fileName, content);
		return parser.Root();
	}

}
