@echo off

REM Build the JavaCC grammar
REM the bootstrap javacc.jar that is in the bootstrap directory.

%~f0\..\javacc %~f0\..\..\src\grammars\JavaCC.javacc
