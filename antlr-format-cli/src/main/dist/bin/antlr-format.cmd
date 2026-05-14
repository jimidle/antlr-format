@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "APP_HOME=%%~fI"
set "JAR_PATH=%APP_HOME%\lib\${project.build.finalName}.jar"

if defined JAVA_HOME (
  set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_BIN=java"
)

if defined ANTLR_FORMAT_JAVA_OPTS (
  "%JAVA_BIN%" %ANTLR_FORMAT_JAVA_OPTS% -jar "%JAR_PATH%" %*
) else (
  "%JAVA_BIN%" -jar "%JAR_PATH%" %*
)

