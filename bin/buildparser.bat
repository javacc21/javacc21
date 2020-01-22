@echo off

REM If there is a generated javacc.jar in the base directory, use that, otherwise, use
REM the bootstrap javacc.jar that is in the bootstrap directory.

java -classpath "%~f0\..\..\build;%~f0\..\..\bootstrap\javacc.jar;%~f0\..\..\bootstrap\freemarker.jar" javacc.Main %~f0\..\..\src\grammars\JavaCC.javacc


