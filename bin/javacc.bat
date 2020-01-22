@echo off

REM If there is a generated javacc.jar in the base directory, use that, otherwise, use
REM the bootstrap javacc.jar that is in the bin directory.

java -classpath "%~f0\..\..\javacc.jar;%~f0\..\..\bootstrap\javacc.jar;%~f0\..\..\bootstrap\freemarker.jar" javacc.Main %1 %2 %3 %4 %5 %6 %7 %8 %9
