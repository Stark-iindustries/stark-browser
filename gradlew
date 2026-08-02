#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0
#

# Attempt to set APP_HOME
APP_HOME=$(cd "$(dirname "$0")" && pwd)
APP_NAME="Gradle"

# Determine the Java command to use to start the JVM
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# For Cygwin or MSYS, switch paths to Windows format before running java
case "$(uname)" in
    CYGWIN* | MSYS* | MINGW* )
        APP_HOME=$(cygpath --path --mixed "$APP_HOME")
        ;;
esac

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
