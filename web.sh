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

javac ExamResultManagementSystem.java
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

(sleep 0.8 && open "http://localhost:8080") &
java ExamResultManagementSystem --server --port=8080
