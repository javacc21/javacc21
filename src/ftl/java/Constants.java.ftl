/* Generated by: ${generated_by}. Do not edit. ${filename} */
[#if grammar.parserPackage?has_content]
package ${grammar.parserPackage};
[/#if]



  public interface ${grammar.constantsClassName} {

  /**
   * The various token types. The first type EOF
   * and the last type INVALID are auto-generated.
   * They represent the end of input and invalid input
   * respectively.
   */
  public enum TokenType {
     [#list grammar.lexerData.regularExpressions as regexp]
       ${regexp.label},
     [/#list]
     [#list grammar.extraTokenNames as extraToken]
       ${extraToken},
     [/#list]
     INVALID
  }
  
  /**
   * Lexical States
   */

  public enum LexicalState {
  [#list grammar.lexerData.lexicalStates as lexicalState]
     ${lexicalState.name}
     [#if lexicalState_has_next],[/#if]
  [/#list]
   }
}

