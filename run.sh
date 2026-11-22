#!/bin/zsh

# Find Java runtime
if [ -d "/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home/bin" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -x "/usr/libexec/java_home" ]; then
    export JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
    [ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "=================================================="
echo "  Starting Exam Result Management System..."
echo "  Java Version: $(java -version 2>&1 | head -n 1)"
echo "=================================================="

# Compile
javac ExamResultManagementSystem.java
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# Run
java ExamResultManagementSystem
