#!/bin/bash
# Quick test runner for OpenAI client
# Usage: ./test.sh
# Loads API key from .env file or OPENAI_API_KEY env var

set -e

echo "Building project..."
./gradlew compileTestJava --quiet

echo "Running OpenAI streaming test..."
# Build classpath without Paper API (avoid Bukkit dependencies)
CP="build/classes/java/main:build/classes/java/test"
for jar in ~/.gradle/caches/modules-2/files-2.1/com.squareup.okhttp3/okhttp/4.12.0/*/*.jar; do
    [ -f "$jar" ] && CP="$CP:$jar"
done
for jar in ~/.gradle/caches/modules-2/files-2.1/com.squareup.okhttp3/okhttp-sse/4.12.0/*/*.jar; do
    [ -f "$jar" ] && CP="$CP:$jar"
done
for jar in ~/.gradle/caches/modules-2/files-2.1/com.squareup.okio/okio-jvm/3.6.0/*/*.jar; do
    [ -f "$jar" ] && CP="$CP:$jar"
done
for jar in ~/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1/*/*.jar; do
    [ -f "$jar" ] && CP="$CP:$jar"
done
for jar in ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.10/*/*.jar; do
    [ -f "$jar" ] && CP="$CP:$jar"
done

java -cp "$CP" com.example.aibuild.StreamingTest
