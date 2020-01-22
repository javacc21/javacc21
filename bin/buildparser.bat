@echo off

REM Build the JavaCC grammar
REM the bootstrap javacc.jar that is in the bootstrap directory.

java -jar ""%~f0\..\javacc.jar" %~f0\..\..\src\grammars\JavaCC.javacc


