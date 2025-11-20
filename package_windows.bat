@echo off
setlocal

REM Ensure JAVA_HOME is set to JDK 14+
if "%JAVA_HOME%"=="" (
    echo Error: JAVA_HOME is not set.
    exit /b 1
)

REM Build the project first
call mvn clean package

REM Define variables
set APP_NAME=BugAnalyzer
set APP_VERSION=1.0
set INPUT_DIR=target
set MAIN_JAR=BugAnalyzer-1.0-SNAPSHOT.jar
set MAIN_CLASS=com.buganalyzer.Launcher
set ICON_PATH=icon.png

REM Create EXE package
echo Creating EXE package...
"%JAVA_HOME%\bin\jpackage" ^
  --type exe ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --input "%INPUT_DIR%" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class "%MAIN_CLASS%" ^
  --icon "%ICON_PATH%" ^
  --win-shortcut ^
  --win-menu ^
  --win-per-user-install ^
  --win-dir-chooser ^
  --description "Android Bug Report Analyzer Tool" ^
  --dest "dist"

echo Packaging complete. Check 'dist' directory.
endlocal
