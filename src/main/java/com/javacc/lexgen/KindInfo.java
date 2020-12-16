/* Copyright (c) 2008-2019 Jonathan Revusky, revusky@javacc.com
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

package com.javacc.lexgen;

import com.javacc.parsegen.TokenSet;
import com.javacc.Grammar;

public final class KindInfo {
   
  private TokenSet validKindSet, finalKindSet;
   
  KindInfo(Grammar grammar) {
	  this.validKindSet = new TokenSet(grammar);
	  this.finalKindSet = new TokenSet(grammar);
  }

   void insertValidKind(int kind) {
      validKindSet.set(kind);
   }

   void insertFinalKind(int kind)  {
      finalKindSet.set(kind);
   }
   
   public long[] getFinalKinds() {
       return finalKindSet.toLongArray();
   }
   
   public int getFinalKindCnt() {
	   return finalKindSet.cardinality();
   }
   
   public int getValidKindCnt() {
	   return validKindSet.cardinality();
   }
   
   public long[] getValidKinds() {
       return validKindSet.toLongArray();
   }
   
   boolean isValidKind(int kind) {
	   return validKindSet.get(kind);
   }
   
   boolean isFinalKind(int kind) {
	   return finalKindSet.get(kind);
   }
}

