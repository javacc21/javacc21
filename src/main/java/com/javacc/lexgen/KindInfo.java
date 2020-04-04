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

public final class KindInfo {
   long[] validKinds;
   long[] finalKinds;
   int    validKindCnt;
   int    finalKindCnt;

   KindInfo(int maxKind)
   {
      validKinds = new long[maxKind / 64 + 1];
      finalKinds = new long[maxKind / 64 + 1];
   }

   public void InsertValidKind(int kind)
   {
      validKinds[kind / 64] |= (1L << (kind % 64));
      validKindCnt++;
   }

   public void InsertFinalKind(int kind)
   {
      finalKinds[kind / 64] |= (1L << (kind % 64));
      finalKindCnt++;
   }
   
   public long[] getFinalKinds() {
       return finalKinds;
   }
   
   public int getFinalKindCnt() {
       return finalKindCnt;
   }
   
   public int getValidKindCnt() {
       return validKindCnt;
   }
   
   public long[] getValidKinds() {
       return validKinds;
   }
}

