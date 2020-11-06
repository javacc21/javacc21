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

import static com.javacc.JavaCCAssert.err;
import static com.javacc.JavaCCAssert.testValidationFor;

import org.junit.jupiter.api.Test;

import com.javacc.JavaCCError.ErrorCode;
import com.javacc.parser.JavaCCParser;

/**
 * Tests with {@link JavaCCParser} validation.
 * 
 * @author Angelo ZERR
 *
 */
public class JavaCCParserValidationTest {

	// Test for Options validation

	@Test
	public void UnrecognizedOption() {
		testValidationFor("test.javacc", //
				"options {\r\n" + //
						"	BAD_OPTION = true;	\r\n" + //
						"}\r\n" + //
						"TOKEN: \"\";", //
				err(ErrorCode.UnrecognizedOption, //
						"Warning: Line 2, Column 2 in test.javacc: Unrecognized option name `BAD_OPTION`. Option setting will be ignored."));
	}

	@Test
	public void DuplicateOption() {
		testValidationFor("test.javacc", //
				"options {\r\n" + //
						"	TABS_TO_SPACES = 2;	\r\n" + //
						"	TABS_TO_SPACES = 4;	\r\n" + //
						"}\r\n" + //
						"TOKEN: \"\";", //
				err(ErrorCode.DuplicateOption, //
						"Warning: Line 3, Column 2 in test.javacc: Duplicate option setting for `TABS_TO_SPACES`. Option setting will be ignored."));
	}

	@Test
	public void OptionValueTypeMismatch() {
		testValidationFor("test.javacc", //
				"options {\r\n" + //
						"	TABS_TO_SPACES = true;	\r\n" + //
						"}\r\n" + //
						"TOKEN: \"\";", //
				err(ErrorCode.OptionValueTypeMismatch, //
						"Warning: Line 2, Column 19 in test.javacc: Bad option value `true` for `TABS_TO_SPACES`. Expected type is `Boolean`. Option setting will be ignored."));
	}

	@Test
	public void Unknown() {
		testValidationFor("test.javacc", //
				"options {\r\n" + //
						"	STATIC = true;	\r\n" + //
						"}\r\n" + //
						"TOKEN: \"\";", //
				err(ErrorCode.Unknown, //
						"Warning: Line 2, Column 2 in test.javacc: In JavaCC 21, the STATIC option is superfluous. All parsers are non-static. Option setting will be ignored."));
	}
}
