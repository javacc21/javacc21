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

import com.javacc.Grammar;
import com.javacc.parser.tree.CharacterList;
import com.javacc.parser.tree.CharacterRange;
import com.javacc.parser.tree.OneOrMoreRegexp;
import com.javacc.parser.tree.RegexpChoice;
import com.javacc.parser.tree.RegexpRef;
import com.javacc.parser.tree.RegexpSequence;
import com.javacc.parser.tree.RegexpStringLiteral;
import com.javacc.parser.tree.RepetitionRange;
import com.javacc.parser.tree.ZeroOrMoreRegexp;
import com.javacc.parser.tree.ZeroOrOneRegexp;

import java.util.ArrayList;
import java.util.List;

/**
 * NFA (Non-deterministic Finite Automaton) code.
 */
class Nfa {

    private NfaState start, end;

    private static final char[] diffUpperCaseRanges = { 97, 122, 224, 246, 248, 254, 255, 255, 257, 257, 259, 259, 261, 261,
    263, 263, 265, 265, 267, 267, 269, 269, 271, 271, 273, 273, 275, 275, 277, 277, 279, 279, 281, 281, 283,
    283, 285, 285, 287, 287, 289, 289, 291, 291, 293, 293, 295, 295, 297, 297, 299, 299, 301, 301, 303, 303,
    305, 305, 307, 307, 309, 309, 311, 311, 314, 314, 316, 316, 318, 318, 320, 320, 322, 322, 324, 324, 326,
    326, 328, 328, 331, 331, 333, 333, 335, 335, 337, 337, 339, 339, 341, 341, 343, 343, 345, 345, 347, 347,
    349, 349, 351, 351, 353, 353, 355, 355, 357, 357, 359, 359, 361, 361, 363, 363, 365, 365, 367, 367, 369,
    369, 371, 371, 373, 373, 375, 375, 378, 378, 380, 380, 382, 382, 383, 383, 387, 387, 389, 389, 392, 392,
    396, 396, 402, 402, 409, 409, 417, 417, 419, 419, 421, 421, 424, 424, 429, 429, 432, 432, 436, 436, 438,
    438, 441, 441, 445, 445, 453, 453, 454, 454, 456, 456, 457, 457, 459, 459, 460, 460, 462, 462, 464, 464,
    466, 466, 468, 468, 470, 470, 472, 472, 474, 474, 476, 476, 479, 479, 481, 481, 483, 483, 485, 485, 487,
    487, 489, 489, 491, 491, 493, 493, 495, 495, 498, 498, 499, 499, 501, 501, 507, 507, 509, 509, 511, 511,
    513, 513, 515, 515, 517, 517, 519, 519, 521, 521, 523, 523, 525, 525, 527, 527, 529, 529, 531, 531, 533,
    533, 535, 535, 595, 595, 596, 596, 598,
    /* new for fixing 1.0.2 */598, 599, /* End new */599, /*
                                                           * 600, Sreeni
                                                           * fixed for
                                                           * 1.2
                                                           */
    601, 601, 603, 603, 608, 608, 611, 611, 616, 616, 617, 617, 623, 623, 626, 626, 643, 643, 648, 648, 650,
    651, 658, 658, 940, 940, 941, 943, 945, 961,
    /* new for fixing 1.0.2 */962, 962, /* End new */963, 971, 972, 972, 973, 974, 976, 976, 977, 977, 981,
    981, 982, 982, 995, 995, 997, 997, 999, 999, 1001, 1001, 1003, 1003, 1005, 1005, 1007, 1007, 1008, 1008,
    1009, 1009, 1072, 1103, 1105, 1116, 1118, 1119, 1121, 1121, 1123, 1123, 1125, 1125, 1127, 1127, 1129, 1129,
    1131, 1131, 1133, 1133, 1135, 1135, 1137, 1137, 1139, 1139, 1141, 1141, 1143, 1143, 1145, 1145, 1147, 1147,
    1149, 1149, 1151, 1151, 1153, 1153, 1169, 1169, 1171, 1171, 1173, 1173, 1175, 1175, 1177, 1177, 1179, 1179,
    1181, 1181, 1183, 1183, 1185, 1185, 1187, 1187, 1189, 1189, 1191, 1191, 1193, 1193, 1195, 1195, 1197, 1197,
    1199, 1199, 1201, 1201, 1203, 1203, 1205, 1205, 1207, 1207, 1209, 1209, 1211, 1211, 1213, 1213, 1215, 1215,
    1218, 1218, 1220, 1220, 1224, 1224, 1228, 1228, 1233, 1233, 1235, 1235, 1237, 1237, 1239, 1239, 1241, 1241,
    1243, 1243, 1245, 1245, 1247, 1247, 1249, 1249, 1251, 1251, 1253, 1253, 1255, 1255, 1257, 1257, 1259, 1259,
    1263, 1263, 1265, 1265, 1267, 1267, 1269, 1269, 1273, 1273, 1377, 1414, 7681, 7681, 7683, 7683, 7685, 7685,
    7687, 7687, 7689, 7689, 7691, 7691, 7693, 7693, 7695, 7695, 7697, 7697, 7699, 7699, 7701, 7701, 7703, 7703,
    7705, 7705, 7707, 7707, 7709, 7709, 7711, 7711, 7713, 7713, 7715, 7715, 7717, 7717, 7719, 7719, 7721, 7721,
    7723, 7723, 7725, 7725, 7727, 7727, 7729, 7729, 7731, 7731, 7733, 7733, 7735, 7735, 7737, 7737, 7739, 7739,
    7741, 7741, 7743, 7743, 7745, 7745, 7747, 7747, 7749, 7749, 7751, 7751, 7753, 7753, 7755, 7755, 7757, 7757,
    7759, 7759, 7761, 7761, 7763, 7763, 7765, 7765, 7767, 7767, 7769, 7769, 7771, 7771, 7773, 7773, 7775, 7775,
    7777, 7777, 7779, 7779, 7781, 7781, 7783, 7783, 7785, 7785, 7787, 7787, 7789, 7789, 7791, 7791, 7793, 7793,
    7795, 7795, 7797, 7797, 7799, 7799, 7801, 7801, 7803, 7803, 7805, 7805, 7807, 7807, 7809, 7809, 7811, 7811,
    7813, 7813, 7815, 7815, 7817, 7817, 7819, 7819, 7821, 7821, 7823, 7823, 7825, 7825, 7827, 7827, 7829, 7829,
    7841, 7841, 7843, 7843, 7845, 7845, 7847, 7847, 7849, 7849, 7851, 7851, 7853, 7853, 7855, 7855, 7857, 7857,
    7859, 7859, 7861, 7861, 7863, 7863, 7865, 7865, 7867, 7867, 7869, 7869, 7871, 7871, 7873, 7873, 7875, 7875,
    7877, 7877, 7879, 7879, 7881, 7881, 7883, 7883, 7885, 7885, 7887, 7887, 7889, 7889, 7891, 7891, 7893, 7893,
    7895, 7895, 7897, 7897, 7899, 7899, 7901, 7901, 7903, 7903, 7905, 7905, 7907, 7907, 7909, 7909, 7911, 7911,
    7913, 7913, 7915, 7915, 7917, 7917, 7919, 7919, 7921, 7921, 7923, 7923, 7925, 7925, 7927, 7927, 7929, 7929,
    7936, 7943, 7952, 7957, 7968, 7975, 7984, 7991, 8000, 8005, 8017, 8017, 8019, 8019, 8021, 8021, 8023, 8023,
    8032, 8039, 8048, 8049, 8050, 8053, 8054, 8055, 8056, 8057, 8058, 8059, 8060, 8061, 8064, 8071, 8080, 8087,
    8096, 8103, 8112, 8113, 8115, 8115, 8131, 8131, 8144, 8145, 8160, 8161, 8165, 8165, 8179, 8179, 8560, 8575,
    9424, 9449, 65345, 65370, 65371, 0xfffe, 0xffff, 0xffff };
    static final char[] diffLowerCaseRanges = { 65, 90, 192, 214, 216, 222, 256, 256, 258, 258, 260, 260, 262, 262,
    264, 264, 266, 266, 268, 268, 270, 270, 272, 272, 274, 274, 276, 276, 278, 278, 280, 280, 282, 282, 284,
    284, 286, 286, 288, 288, 290, 290, 292, 292, 294, 294, 296, 296, 298, 298, 300, 300, 302, 302, /*
                                                                                                    * new
                                                                                                    * for
                                                                                                    * fixing
                                                                                                    * 1.0
                                                                                                    * .2
                                                                                                    */304, 304, /*
                                                                                                                 * End
                                                                                                                 * new
                                                                                                                 */
    306, 306, 308, 308, 310, 310, 313, 313, 315, 315, 317, 317, 319, 319, 321, 321, 323, 323, 325, 325, 327,
    327, 330, 330, 332, 332, 334, 334, 336, 336, 338, 338, 340, 340, 342, 342, 344, 344, 346, 346, 348, 348,
    350, 350, 352, 352, 354, 354, 356, 356, 358, 358, 360, 360, 362, 362, 364, 364, 366, 366, 368, 368, 370,
    370, 372, 372, 374, 374, 376, 376, 377, 377, 379, 379, 381, 381, 385, 385, 386, 386, 388, 388, 390, 390,
    391, 391,
    /* new for fixing 1.0.2 */393, 393, /* End new */394, 394, 395, 395,
    /* 398, Sreeni fixed for 1.2 */399, 399, 400, 400, 401, 401, 403, 403, 404, 404, 406, 406, 407, 407, 408,
    408, 412, 412, 413, 413, 416, 416, 418, 418, 420, 420, 423, 423, 425, 425, 428, 428, 430, 430, 431, 431,
    433, 434, 435, 435, 437, 437, 439, 439, 440, 440, 444, 444, 452, 452, 453, 453, 455, 455, 456, 456, 458,
    458, 459, 459, 461, 461, 463, 463, 465, 465, 467, 467, 469, 469, 471, 471, 473, 473, 475, 475, 478, 478,
    480, 480, 482, 482, 484, 484, 486, 486, 488, 488, 490, 490, 492, 492, 494, 494, 497, 497, 498, 498, 500,
    500, 506, 506, 508, 508, 510, 510, 512, 512, 514, 514, 516, 516, 518, 518, 520, 520, 522, 522, 524, 524,
    526, 526, 528, 528, 530, 530, 532, 532, 534, 534, 902, 902, 904, 906, 908, 908, 910, 911, 913, 929, 931,
    939, 994, 994, 996, 996, 998, 998, 1000, 1000, 1002, 1002, 1004, 1004, 1006, 1006, 1025, 1036, 1038, 1039,
    1040, 1040, 1041, 1041, 1042, 1071, 1120, 1120, 1122, 1122, 1124, 1124, 1126, 1126, 1128, 1128, 1130, 1130,
    1132, 1132, 1134, 1134, 1136, 1136, 1138, 1138, 1140, 1140, 1142, 1142, 1144, 1144, 1146, 1146, 1148, 1148,
    1150, 1150, 1152, 1152, 1168, 1168, 1170, 1170, 1172, 1172, 1174, 1174, 1176, 1176, 1178, 1178, 1180, 1180,
    1182, 1182, 1184, 1184, 1186, 1186, 1188, 1188, 1190, 1190, 1192, 1192, 1194, 1194, 1196, 1196, 1198, 1198,
    1200, 1200, 1202, 1202, 1204, 1204, 1206, 1206, 1208, 1208, 1210, 1210, 1212, 1212, 1214, 1214, 1217, 1217,
    1219, 1219, 1223, 1223, 1227, 1227, 1232, 1232, 1234, 1234, 1236, 1236, 1238, 1238, 1240, 1240, 1242, 1242,
    1244, 1244, 1246, 1246, 1248, 1248, 1250, 1250, 1252, 1252, 1254, 1254, 1256, 1256, 1258, 1258, 1262, 1262,
    1264, 1264, 1266, 1266, 1268, 1268, 1272, 1272, 1329, 1366, 4256, 4293, 7680, 7680, 7682, 7682, 7684, 7684,
    7686, 7686, 7688, 7688, 7690, 7690, 7692, 7692, 7694, 7694, 7696, 7696, 7698, 7698, 7700, 7700, 7702, 7702,
    7704, 7704, 7706, 7706, 7708, 7708, 7710, 7710, 7712, 7712, 7714, 7714, 7716, 7716, 7718, 7718, 7720, 7720,
    7722, 7722, 7724, 7724, 7726, 7726, 7728, 7728, 7730, 7730, 7732, 7732, 7734, 7734, 7736, 7736, 7738, 7738,
    7740, 7740, 7742, 7742, 7744, 7744, 7746, 7746, 7748, 7748, 7750, 7750, 7752, 7752, 7754, 7754, 7756, 7756,
    7758, 7758, 7760, 7760, 7762, 7762, 7764, 7764, 7766, 7766, 7768, 7768, 7770, 7770, 7772, 7772, 7774, 7774,
    7776, 7776, 7778, 7778, 7780, 7780, 7782, 7782, 7784, 7784, 7786, 7786, 7788, 7788, 7790, 7790, 7792, 7792,
    7794, 7794, 7796, 7796, 7798, 7798, 7800, 7800, 7802, 7802, 7804, 7804, 7806, 7806, 7808, 7808, 7810, 7810,
    7812, 7812, 7814, 7814, 7816, 7816, 7818, 7818, 7820, 7820, 7822, 7822, 7824, 7824, 7826, 7826, 7828, 7828,
    7840, 7840, 7842, 7842, 7844, 7844, 7846, 7846, 7848, 7848, 7850, 7850, 7852, 7852, 7854, 7854, 7856, 7856,
    7858, 7858, 7860, 7860, 7862, 7862, 7864, 7864, 7866, 7866, 7868, 7868, 7870, 7870, 7872, 7872, 7874, 7874,
    7876, 7876, 7878, 7878, 7880, 7880, 7882, 7882, 7884, 7884, 7886, 7886, 7888, 7888, 7890, 7890, 7892, 7892,
    7894, 7894, 7896, 7896, 7898, 7898, 7900, 7900, 7902, 7902, 7904, 7904, 7906, 7906, 7908, 7908, 7910, 7910,
    7912, 7912, 7914, 7914, 7916, 7916, 7918, 7918, 7920, 7920, 7922, 7922, 7924, 7924, 7926, 7926, 7928, 7928,
    7944, 7951, 7960, 7965, 7976, 7983, 7992, 7999, 8008, 8013, 8025, 8025, 8027, 8027, 8029, 8029, 8031, 8031,
    8040, 8047, 8072, 8079, 8088, 8095, 8104, 8111, 8120, 8121, 8122, 8123, 8124, 8124, 8136, 8139, 8140, 8140,
    8152, 8153, 8154, 8155, 8168, 8169, 8170, 8171, 8172, 8172, 8184, 8185, 8186, 8187, 8188, 8188, 8544, 8559,
    9398, 9423, 65313, 65338, 65339, 0xfffe, 0xffff, 0xffff };

    Nfa(LexicalState lexicalState) {
        start = new NfaState(lexicalState);
        end = new NfaState(lexicalState);
    }

    Nfa(NfaState start, NfaState end) {
        this.start = start;
        this.end = end;
    }

    NfaState getStart() {
        return start;
    }

    NfaState getEnd() {
        return end;
    }

    static Nfa buildNfa(RegularExpression regexp, LexicalState lexicalState, boolean ignoreCase) {
        if (regexp instanceof RegexpRef) {
            return buildNfa(((RegexpRef) regexp).getRegexp(), lexicalState, ignoreCase);
        } else if (regexp instanceof RegexpStringLiteral) {
            return buildNfa((RegexpStringLiteral) regexp, lexicalState, ignoreCase);
        } else if (regexp instanceof ZeroOrMoreRegexp) {
            return buildNfa((ZeroOrMoreRegexp) regexp, lexicalState, ignoreCase);
        } else if (regexp instanceof ZeroOrOneRegexp) {
            return buildNfa((ZeroOrOneRegexp) regexp, lexicalState, ignoreCase);
        } else if (regexp instanceof OneOrMoreRegexp) {
            return buildNfa((OneOrMoreRegexp) regexp, lexicalState, ignoreCase);
        } else if (regexp instanceof CharacterList) {
            return buildNfa((CharacterList) regexp, lexicalState, ignoreCase);
        } else if (regexp instanceof RepetitionRange) {
            return buildNfa((RepetitionRange) regexp, lexicalState, ignoreCase);
        } else if (regexp instanceof RegexpSequence) {
            return buildNfa((RegexpSequence) regexp, lexicalState, ignoreCase);
        } else if (regexp instanceof RegexpChoice) {
            return buildNfa((RegexpChoice) regexp, lexicalState, ignoreCase);
        } else {
            return null;
        }
    }

    static private Nfa buildNfa(RegexpStringLiteral stringLiteral, LexicalState lexState, boolean ignoreCase) {
        String image = stringLiteral.getImage();
        Grammar grammar = stringLiteral.getGrammar();

        if (image.length() == 1) {
            CharacterList charList = new CharacterList();
            charList.setGrammar(grammar);
            CharacterRange cr = new CharacterRange();
            cr.setGrammar(grammar);
            cr.left = cr.right = image.charAt(0);
            charList.addChild(cr);
            return buildNfa(charList, lexState, ignoreCase);
        }
        NfaState startState = new NfaState(lexState);
        NfaState theStartState = startState;
        NfaState finalState = null;

        if (image.length() == 0) {
            return new Nfa(theStartState, theStartState);
        }

        for (int i = 0; i < image.length(); i++) {
            finalState = new NfaState(lexState);
            startState.setCharMoves(new char[1]);
            startState.addChar(image.charAt(i));
            if (grammar.getOptions().getIgnoreCase() || ignoreCase) {
                startState.addChar(Character.toLowerCase(image.charAt(i)));
                startState.addChar(Character.toUpperCase(image.charAt(i)));
            }
            startState.setNext(finalState);
            startState = finalState;
        }
        return new Nfa(theStartState, finalState);
    }

    static private Nfa buildNfa(ZeroOrMoreRegexp zom, LexicalState lexState, boolean ignoreCase) {
        Nfa nfa = new Nfa(lexState);
        NfaState startState = nfa.getStart();
        NfaState finalState = nfa.getEnd();
        Nfa temp = buildNfa(zom.getRegexp(), lexState, ignoreCase);
        startState.addMove(temp.getStart());
        startState.addMove(finalState);
        temp.getEnd().addMove(finalState);
        temp.getEnd().addMove(temp.getStart());
        return nfa;
    }

    static private Nfa buildNfa(ZeroOrOneRegexp zoo, LexicalState lexState, boolean ignoreCase) {
        Nfa nfa = new Nfa(lexState);
        NfaState startState = nfa.getStart();
        NfaState finalState = nfa.getEnd();
        Nfa temp = buildNfa(zoo.getRegexp(), lexState, ignoreCase);
        startState.addMove(temp.getStart());
        startState.addMove(finalState);
        temp.getEnd().addMove(finalState);
        return nfa;
    }

    static private Nfa buildNfa(OneOrMoreRegexp oom, LexicalState lexState, boolean ignoreCase) {
        Nfa nfa = new Nfa(lexState);
        NfaState startState = nfa.getStart();
        NfaState finalState = nfa.getEnd();
        Nfa temp = buildNfa(oom.getRegexp(), lexState, ignoreCase);
        startState.addMove(temp.getStart());
        temp.getEnd().addMove(temp.getStart());
        temp.getEnd().addMove(finalState);
        return nfa;
    }

    static private Nfa buildNfa(CharacterList charList, LexicalState lexState, boolean ignoreCase) {
        List<CharacterRange> descriptors = charList.getDescriptors();
        if (ignoreCase) {
            descriptors = toCaseNeutral(descriptors);
        }
        descriptors = sortDescriptors(descriptors);
        if (charList.isNegated()) {
            descriptors = removeNegation(descriptors);
        }
        Nfa retVal = new Nfa(lexState);
        NfaState startState = retVal.getStart();
        NfaState finalState = retVal.getEnd();
        for (int i = 0; i < descriptors.size(); i++) {
            if (descriptors.get(i).isSingleChar()) {
                startState.addChar(descriptors.get(i).left);
            } else {
                CharacterRange cr = descriptors.get(i);
                startState.addRange(cr.left, cr.right);
            }
        }
        startState.setNext(finalState);
        return retVal;
    }

    static private Nfa buildNfa(RepetitionRange repRange, LexicalState lexState, boolean ignoreCase) {
        Grammar grammar = repRange.getGrammar();
        List<RegularExpression> units = new ArrayList<RegularExpression>();
        RegexpSequence seq;
        int i;

        for (i = 0; i < repRange.getMin(); i++) {
            units.add(repRange.getRegexp());
        }

        if (repRange.hasMax() && repRange.getMax() == -1) // Unlimited
        {
            ZeroOrMoreRegexp zom = new ZeroOrMoreRegexp();
            zom.setGrammar(grammar);
            zom.setRegexp(repRange.getRegexp());
            units.add(zom);
        }

        while (i++ < repRange.getMax()) {
            ZeroOrOneRegexp zoo = new ZeroOrOneRegexp();
            zoo.setGrammar(grammar);
            zoo.setRegexp(repRange.getRegexp());
            units.add(zoo);
        }
        seq = new RegexpSequence();
        seq.setGrammar(grammar);
        seq.setOrdinal(Integer.MAX_VALUE);
        for (RegularExpression re : units) {
            seq.addChild(re);
        }
        return buildNfa(seq, lexState, ignoreCase);
    }


    static private Nfa buildNfa(RegexpSequence seq, LexicalState lexState, boolean ignoreCase) {
        if (seq.getUnits().size() == 1) {
            return buildNfa(seq.getUnits().get(0), lexState, ignoreCase);
        }

        Nfa retVal = new Nfa(lexState);
        NfaState startState = retVal.getStart();
        NfaState finalState = retVal.getEnd();

        RegularExpression curRE = seq.getUnits().get(0);
        Nfa temp1 = buildNfa(curRE, lexState, ignoreCase);
        Nfa temp2 = null;
        startState.addMove(temp1.getStart());

        for (int i = 1; i < seq.getUnits().size(); i++) {
            curRE = seq.getUnits().get(i);
            temp2 = buildNfa(curRE, lexState, ignoreCase);
            temp1.getEnd().addMove(temp2.getStart());
            temp1 = temp2;
        }

        temp2.getEnd().addMove(finalState);

        return retVal;
    }

    static private Nfa buildNfa(RegexpChoice choice, LexicalState lexState, boolean ignoreCase) {
        List<RegularExpression> choices = compressCharLists(choice.getChoices());

        if (choices.size() == 1) {
            return buildNfa(choices.get(0), lexState, ignoreCase);
        }

        Nfa retVal = new Nfa(lexState);
        NfaState startState = retVal.getStart();
        NfaState finalState = retVal.getEnd();

        for (RegularExpression curRE : choices) {
            Nfa nfa = buildNfa(curRE, lexState, ignoreCase);
            startState.addMove(nfa.getStart());
            nfa.getEnd().addMove(finalState);
        }

        return retVal;
    }

    static private List<RegularExpression> compressCharLists(List<RegularExpression> choices) {
        List<RegularExpression> compressedChoices = compressChoices(choices); // Unroll nested choices
        List<RegularExpression> result = new ArrayList<RegularExpression>();
        CharacterList mergedCharList = new CharacterList();
        mergedCharList.setGrammar(compressedChoices.get(0).getGrammar());
        for (RegularExpression curRE : compressedChoices) {
            while (curRE instanceof RegexpRef) {
                curRE = ((RegexpRef) curRE).getRegexp();
            }
            if (curRE instanceof RegexpStringLiteral && ((RegexpStringLiteral) curRE).getImage().length() == 1) {
                CharacterList charList = new CharacterList();
                CharacterRange cr = new CharacterRange();
                cr.left = cr.right = ((RegexpStringLiteral) curRE).getImage().charAt(0);
                charList.addChild(cr);
                curRE = charList;
            }
            if (curRE instanceof CharacterList){
                CharacterList charList = (CharacterList) curRE;
                List<CharacterRange> descriptors = sortDescriptors(charList.getDescriptors());
                if (charList.isNegated()) {
                    descriptors = removeNegation(descriptors);
                }
                for (CharacterRange cr : descriptors) {
                    mergedCharList.addChild(cr);
                }
            } else {
                result.add(curRE);
            }
        }
        if (!mergedCharList.getDescriptors().isEmpty()) {
            result.add(mergedCharList);
        }
        return result;
    }

    static private List<RegularExpression> compressChoices(List<RegularExpression> choices) {
        List<RegularExpression> result = new ArrayList<RegularExpression>();
        for (RegularExpression curRE : choices) {

            while (curRE instanceof RegexpRef) {
                curRE = ((RegexpRef) curRE).getRegexp();
            }
            if (curRE instanceof RegexpChoice) {
                for (RegularExpression re : ((RegexpChoice)curRE).getChoices()) {
                    result.add(re);
                }
            }
            else {
                result.add(curRE);
            }
        }
        return result;
    }

    static private boolean inRange(char c, CharacterRange range) {
        return (c >= range.left && c <= range.right);
    }

    static private boolean subRange(CharacterRange r1, CharacterRange r2) {
        return (r1.left >= r2.left && r1.right <= r2.right);
    }

    static private boolean Overlaps(CharacterRange r1, CharacterRange r2) {
        return (r1.left <= r2.right && r1.right > r2.right);
    }

    static private List<CharacterRange> toCaseNeutral(List<CharacterRange> descriptors) {
        List<CharacterRange> result = new ArrayList<CharacterRange>();
        for (CharacterRange range : descriptors) {
            result.add(range);
            char l = range.left;
            char r = range.right;
            int j = 0;

            /* Add ranges for which lower case is different. */
            for (;;) {
                while (l > diffLowerCaseRanges[j])
                    j += 2;

                if (l < diffLowerCaseRanges[j]) {
                    if (r < diffLowerCaseRanges[j])
                        break;

                    if (r <= diffLowerCaseRanges[j + 1]) {
                        CharacterRange crc = new CharacterRange();
                        crc.left = Character.toLowerCase(diffLowerCaseRanges[j]);
                        crc.right = (char) (Character.toLowerCase(diffLowerCaseRanges[j]) + r - diffLowerCaseRanges[j]);
                        result.add(crc);
                        break;
                    }
                    CharacterRange cr = new CharacterRange();
                    cr.left = Character.toLowerCase(diffLowerCaseRanges[j]);
                    cr.right = Character.toLowerCase(diffLowerCaseRanges[j + 1]);
                    result.add(cr);
                } else {
                    if (r <= diffLowerCaseRanges[j + 1]) {
                        CharacterRange cr = new CharacterRange();
                        cr.left = (char) (Character.toLowerCase(diffLowerCaseRanges[j]) + l - diffLowerCaseRanges[j]);
                        cr.right = (char) (Character.toLowerCase(diffLowerCaseRanges[j]) + r - diffLowerCaseRanges[j]);
                        result.add(cr);
                        break;
                    }
                    CharacterRange crs = new CharacterRange();
                    crs.left = (char) (Character.toLowerCase(diffLowerCaseRanges[j]) + l - diffLowerCaseRanges[j]);
                    crs.right = Character.toLowerCase(diffLowerCaseRanges[j + 1]);
                    result.add(crs);
                }

                j += 2;
                while (r > diffLowerCaseRanges[j]) {
                    if (r <= diffLowerCaseRanges[j + 1]) {
                        CharacterRange cr = new CharacterRange();
                        cr.left = Character.toLowerCase(diffLowerCaseRanges[j]);
                        cr.right = (char) (Character.toLowerCase(diffLowerCaseRanges[j]) + r - diffLowerCaseRanges[j]);
                        result.add(cr);
                        break;
                    }
                    CharacterRange crs = new CharacterRange();
                    crs.left = Character.toLowerCase(diffLowerCaseRanges[j]);
                    crs.right = Character.toLowerCase(diffLowerCaseRanges[j + 1]);
                    result.add(crs);
                    j += 2;
                }
                break;
            }

            /* Add ranges for which upper case is different. */
            j = 0;
            while (l > diffUpperCaseRanges[j])
                j += 2;

            if (l < diffUpperCaseRanges[j]) {
                if (r < diffUpperCaseRanges[j])
                    continue;

                if (r <= diffUpperCaseRanges[j + 1]) {
                    CharacterRange crs = new CharacterRange();
                    crs.left = Character.toUpperCase(diffUpperCaseRanges[j]);
                    crs.right = (char) (Character.toUpperCase(diffUpperCaseRanges[j]) + r - diffUpperCaseRanges[j]);
                    result.add(crs);
                    continue;
                }
                CharacterRange cr = new CharacterRange();
                cr.left = Character.toUpperCase(diffUpperCaseRanges[j]);
                cr.right = Character.toUpperCase(diffUpperCaseRanges[j + 1]);
                result.add(cr);
            } else {
                if (r <= diffUpperCaseRanges[j + 1]) {
                    CharacterRange crs = new CharacterRange();
                    crs.left = (char) (Character.toUpperCase(diffUpperCaseRanges[j]) + l - diffUpperCaseRanges[j]);
                    crs.right = (char) (Character.toUpperCase(diffUpperCaseRanges[j]) + r - diffUpperCaseRanges[j]);
                    result.add(crs);
                    continue;
                }
                CharacterRange cr = new CharacterRange();
                cr.left = (char) (Character.toUpperCase(diffUpperCaseRanges[j]) + l - diffUpperCaseRanges[j]);
                cr.right = Character.toUpperCase(diffUpperCaseRanges[j + 1]);
                result.add(cr);
            }
            j += 2;
            while (r > diffUpperCaseRanges[j]) {
                if (r <= diffUpperCaseRanges[j + 1]) {
                    CharacterRange crs = new CharacterRange();
                    crs.left = Character.toUpperCase(diffUpperCaseRanges[j]);
                    crs.right = (char) (Character.toUpperCase(diffUpperCaseRanges[j]) + r - diffUpperCaseRanges[j]);
                    result.add(crs);
                    break;
                }
                CharacterRange cr = new CharacterRange();
                cr.left = Character.toUpperCase(diffUpperCaseRanges[j]);
                cr.right = Character.toUpperCase(diffUpperCaseRanges[j + 1]);
                result.add(cr);
                j += 2;
            }
        }
        return result;
    }

    static private List<CharacterRange> removeNegation(List<CharacterRange> descriptors) {
        List<CharacterRange> result = new ArrayList<CharacterRange>();
        int lastRemoved = -1; // One less than the first valid character.

        for (CharacterRange obj : descriptors) {
            if (obj.isSingleChar()) {
                char c = obj.left;

                if (c >= 0 && c <= lastRemoved + 1) {
                    lastRemoved = c;
                    continue;
                }
                CharacterRange crs = new CharacterRange();
                crs.left = (char) (lastRemoved + 1);
                crs.right = (char) ((lastRemoved = c) - 1);
                result.add(crs);
            } else {
                char l = ((CharacterRange) obj).left;
                char r = ((CharacterRange) obj).right;

                if (l >= 0 && l <= lastRemoved + 1) {
                    lastRemoved = r;
                    continue;
                }
                CharacterRange crs = new CharacterRange();
                crs.left = (char) (lastRemoved + 1);
                crs.right = (char) (l - 1);
                result.add(crs);
                lastRemoved = r;
            }
        }

        // System.out.println("lastRem : " + (int)lastRemoved);
        if (lastRemoved < (char) 0xffff) {
            CharacterRange crs = new CharacterRange();
            crs.left = (char) (lastRemoved + 1);
            crs.right = (char) 0xffff;
            result.add(crs);
        }
        return result;
    }

    static private List<CharacterRange> sortDescriptors(List<CharacterRange> descriptors) {
        int j;

        List<CharacterRange> result = new ArrayList<CharacterRange>(descriptors.size());
        int cnt = 0;

        Outer: for (int i = 0; i < descriptors.size(); i++) {
            CharacterRange range;

            if (descriptors.get(i).isSingleChar()) {
                CharacterRange s = descriptors.get(i);

                for (j = 0; j < cnt; j++) {
                    if (result.get(j).isSingleChar()) {
                        if ((result.get(j)).left > s.left)
                            break;
                        else if (result.get(j).left == s.left)
                            continue Outer;
                    } else {
                        char l = ((CharacterRange) result.get(j)).left;

                        if (inRange(s.left, result.get(j)))
                            continue Outer;
                        else if (l > s.left)
                            break;
                    }
                }
                result.add(j, s);
                cnt++;
            } else {
                range = (CharacterRange) descriptors.get(i);
                for (j = 0; j < cnt; j++) {
                    if (result.get(j).isSingleChar()) {
                        if (inRange((result.get(j)).left, range)) {
                            result.remove(j--);
                            cnt--;
                        } else if (result.get(j).left > range.right)
                            break;
                    } else {
                        if (subRange(range, (CharacterRange) result.get(j))) {
                            continue Outer;
                        } else if (subRange((CharacterRange) result.get(j), range)) {
                            result.set(j, range);
                            continue Outer;
                        } else if (Overlaps(range, (CharacterRange) result.get(j))) {
                            range.left = (char) (((CharacterRange) result.get(j)).right + 1);
                        } else if (Overlaps((CharacterRange) result.get(j), range)) {
                            CharacterRange tmp = range;
                            ((CharacterRange) result.get(j)).right = (char) (range.left + 1);
                            range = (CharacterRange) result.get(j);
                            result.set(j, tmp);
                        } else if (((CharacterRange) result.get(j)).left > range.right)
                            break;
                    }
                }

                result.add(j, range);
                cnt++;
            }
        }
        return result;
    }
}
