@echo off

REM Build the JavaCC grammar
REM the bootstrap javacc.jar that is in the bootstrap directory.

cd "%f0\.."

javacc.bat ..\src\grammars\JavaCC.javacc

