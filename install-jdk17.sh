#!/bin/bash
# Installs JDK 17 alongside the existing JDK, for building this project
# (its Gradle/AGP version doesn't support JDK 26). Does not change the system default `java`.
set -euo pipefail
sudo pacman -S --needed jdk17-openjdk

echo
echo "Installed. To build this project with JDK 17 without changing your system default:"
echo "  JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:compileNormalDebugKotlin"
