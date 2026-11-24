#!/bin/zsh
cd "$(dirname "$0")"

# Find Java runtime
if [ -d "/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home/bin" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -x "/usr/libexec/java_home" ]; then
    export JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
    [ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "=================================================="
echo "  Launching Exam Result Management System (GUI)..."
echo "  Java Version: $(java -version 2>&1 | head -n 1)"
echo "=================================================="

javac ExamResultManagementSystem.java
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

java -Xdock:name="Exam Result Management System" ExamResultManagementSystem --gui
