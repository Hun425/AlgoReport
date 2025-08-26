#!/bin/bash
# Simple gradlew script for macOS/Linux
# This assumes gradle is available or uses the wrapper properties

GRADLE_VERSION=$(grep "distributionUrl" gradle/wrapper/gradle-wrapper.properties | cut -d'/' -f5 | cut -d'-' -f2)
GRADLE_HOME="$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}"

# Check if we have gradle available
if command -v gradle &> /dev/null; then
    gradle "$@"
elif [[ -d "$GRADLE_HOME" ]]; then
    # Use gradle wrapper if available
    java -classpath "$GRADLE_HOME/lib/*" org.gradle.launcher.GradleMain "$@"
else
    echo "Gradle not found. Please install gradle or run './gradlew.bat' if on Windows"
    exit 1
fi