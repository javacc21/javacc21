@echo off

REM If there are classfiles in the build directory, then use them, otherwise
REM use the bootstrap javacc.jar that is in the bin directory.

java -classpath "%~f0\..\..\build;%~f0\..\javacc.jar;%~f0\..\freemarker.jar" javacc.Main %1 %2 %3 %4 %5 %6 %7 %8 %9
