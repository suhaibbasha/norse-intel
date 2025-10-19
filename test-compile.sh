#!/bin/bash

echo "Testing Maven compilation..."
cd /Users/suhib/Desktop/java-norse

# Clean and compile
mvn clean compile -q -B

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
else
    echo "❌ Compilation failed!"
    mvn compile
fi