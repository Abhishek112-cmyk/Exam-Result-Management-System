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
echo "  Compiling and Running Test Suite..."
echo "=================================================="

javac ExamResultManagementSystem.java ExamResultManagementSystemTest.java
if [ $? -ne 0 ]; then
    echo "Test compilation failed!"
    exit 1
fi

java ExamResultManagementSystemTest
