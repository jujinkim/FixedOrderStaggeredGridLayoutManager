#!/usr/bin/env sh

##############################################################################
# Gradle start up script for UN*X
##############################################################################

# Add default JVM options here. You can also use the JAVA_OPTS and GRADLE_OPTS
# environment variables to pass JVM options to this script.
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")"; pwd -P)

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME\n\nPlease set the JAVA_HOME variable in your environment to match the\nlocation of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.\n\nPlease set the JAVA_HOME variable in your environment to match the\nlocation of your Java installation."
fi

# Download wrapper jar if missing
if [ ! -f "$CLASSPATH" ]; then
  WRAPPER_URL="https://services.gradle.org/distributions/gradle-8.7-bin.zip"
  echo "gradlew: wrapper jar missing; attempting bootstrap..."
  mkdir -p "$APP_HOME/gradle/wrapper"
  # Try to download the wrapper JAR from GitHub raw as a fallback
  # This is a minimal bootstrap that may fail if network is restricted
  WRAPPER_JAR_URL="https://raw.githubusercontent.com/gradle/gradle/v8.7/gradle/wrapper/gradle-wrapper.jar"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_JAR_URL" -o "$CLASSPATH" || true
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_JAR_URL" -O "$CLASSPATH" || true
  fi
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
  -Dorg.gradle.appname=$APP_BASE_NAME \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"

