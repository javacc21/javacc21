package javacc;

import java.io.File;
import java.util.*;
import org.apache.tools.ant.*;
import freemarker.log.Logger;



/**
 * JavaCC task.
 * @author Jonathan Revusky
 */
public class JavaCCAntTask extends Task {
    
    private File inputFile;
    private List<String> argStrings = new ArrayList<String>();
    
    
    static {
        try {
            Logger.selectLoggerLibrary(Logger.LIBRARY_NONE);
        } catch (Exception e){}
    }
    
    public void setSrc(File inputFile) {
        this.inputFile = inputFile;
    }
    
    public void setBASE_SRC_DIR(File baseSourceDirectory) {
        argStrings.add("-BASE_SRC_DIR=" + baseSourceDirectory);
    }
    
    public void setPARSER_PACKAGE(String parserPackage) {
        argStrings.add("-PARSER_PACKAGE=" + parserPackage);
    }
    
    public void setNODE_PACKAGE(String nodePackage) {
        argStrings.add("-NODE_PACKAGE=" + nodePackage);
    }
    
    public void setPARSER_CLASS(String parserClass) {
        argStrings.add("-PARSER_CLASS=" + parserClass);
    }
    
    public void setLEXER_CLASS(String lexerClass) {
        argStrings.add("-LEXER_CLASS=" + lexerClass);
    }
    
    public void setMULTI(boolean multi) {
        argStrings.add("-MULTI="+multi);
    }
    
    public void setNODE_PREFIX(String nodePrefix) {
        argStrings.add("-NODE_PREFIX=" + nodePrefix);
    }

    public void setTREE_BUILDING_ENABLED(boolean treeBuildingEnabled) {
        argStrings.add("-TREE_BUILDING_ENABLED="+treeBuildingEnabled);
    }
    
    public void setTREE_BUILDING_DEFAULT(boolean treeBuildingDefault) {
        argStrings.add("-TREE_BUILDING_DEFAULT="+treeBuildingDefault);
    }
    
    public void setIGNORE_CASE(boolean ignoreCase) {
        argStrings.add("-IGNORE_CASE="+ignoreCase);
    }
    
    public void setTOKENS_ARE_NODES(boolean tokensAreNodes) {
        argStrings.add("-TOKENS_ARE_NODES"+tokensAreNodes);
    }
    
    public void setSPECIAL_TOKENS_ARE_NODES(boolean specialTokensAreNodes) {
        argStrings.add("-SPECIAL_TOKENS_ARE_NODES="+specialTokensAreNodes);
    }
    
    public void setFREEMARKER_NODES(boolean freeMarkerNodes) {
        argStrings.add("-FREEMARKER_NODES="+freeMarkerNodes);
    }
    
    public void setDEBUG_LEXER(boolean debugLexer) {
        argStrings.add("-DEBUG_LEXER="+debugLexer);
    }
    
    public void setDEBUG_PARSER(boolean debugParser) {
        argStrings.add("-DEBUG_PARSER="+debugParser);
    }
    
    public void setBUILD_LEXER(boolean buildLexer) {
        argStrings.add("-BUILD_LEXER="+buildLexer);
    }
    
    public void setBUILD_PARSER(boolean buildParser) {
        argStrings.add("-BUILD_PARSER="+buildParser);
    }
    
    public void setCONSTANTS_CLASS(String constantsClass) {
        argStrings.add("-CONSTANTS_CLASS="+constantsClass);
    }
    
    public void setBASE_NODE_CLASS(String baseNodeClass) {
        argStrings.add("-BASE_NODE_CLASS="+baseNodeClass);
    }
    
    public void setJAVA_UNICODE_ESCAPE(boolean javaUnicodeEscape) {
        argStrings.add("-JAVA_UNICODE_ESCAPE="+javaUnicodeEscape);
    }
    
    public void setVISITOR(boolean visitor) {
        argStrings.add("-VISITOR="+visitor);
    }
    
    public void setUSER_DEFINED_LEXER(boolean userDefinedLexer) {
        argStrings.add("-USER_DEFINED_LEXER="+userDefinedLexer);
    }
    
    public void setUSER_CHAR_STREAM(boolean userCharStream) {
        argStrings.add("-USER_CHAR_STREAM="+userCharStream);
    }
    
    public void setLEXER_USES_PARSER(boolean lexerUsesParser) {
        argStrings.add("-LEXER_USES_PARSER=" + lexerUsesParser);
    }
    
    public void setNODE_SCOPE_HOOK(boolean nodeScopeHook) {
        argStrings.add("-NODE_SCOPE_HOOK="+nodeScopeHook);
    }
    
    public void setNODE_DEFAULT_VOID(boolean nodeDefaultVoid) {
        argStrings.add("-NODE_DEFAULT_VOID="+nodeDefaultVoid);
    }
    
    public void setSMART_NODE_CREATION(boolean smartNodeCreation) {
        argStrings.add("-SMART_NODE_CREATION="+smartNodeCreation);
    }
    
    
    public void execute() throws BuildException {
        String[] args = new String[argStrings.size() +1];
        int i=0;
        for (String arg : argStrings) {
            args[i++] = arg;
        }
        args[i] = inputFile.getAbsolutePath();
        int result = 0;
        try {
            result = Main.mainProgram(args);
        } catch (Exception e) {
            e.printStackTrace();
            result=-1;
//            throw new BuildException(e);
        }
        if (result !=0) {
            throw new BuildException("JavaCC error");
        }
    }
}
