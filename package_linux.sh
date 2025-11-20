#!/bin/bash

# Ensure JAVA_HOME is set to JDK 14+
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
    echo "JAVA_HOME not set. Using default: $JAVA_HOME"
fi

# Build the project first
mvn clean package

# Define variables
APP_NAME="BugAnalyzer"
APP_VERSION="1.0"
INPUT_DIR="target"
MAIN_JAR="BugAnalyzer-1.0-SNAPSHOT.jar"
MAIN_CLASS="com.buganalyzer.Launcher"
ICON_PATH="icon.png" # Ensure this exists

# Create DEB package
echo "Creating DEB package..."
"$JAVA_HOME/bin/jpackage" \
  --type deb \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --input "$INPUT_DIR" \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  --icon "$ICON_PATH" \
  --linux-shortcut \
  --linux-menu-group "Development" \
  --description "Android Bug Report Analyzer Tool" \
  --dest "dist"

echo "Packaging complete. Check 'dist' directory."
