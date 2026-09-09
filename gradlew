#!/bin/sh
#
# Gradle start-up script for POSIX (Alal).
#
# This is a compact wrapper launcher. If gradle/wrapper/gradle-wrapper.jar is
# missing (it is a binary and is not shipped in this source drop), the script
# will try to bootstrap it using an installed `gradle` binary. On GitHub Actions
# the workflows use gradle/actions/setup-gradle with a pinned version instead,
# so the jar is never required there.
#
set -e

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  if command -v gradle >/dev/null 2>&1; then
    echo "gradle-wrapper.jar not found; generating it with the installed Gradle..." >&2
    (cd "$APP_HOME" && gradle wrapper --gradle-version 8.9 --distribution-type bin -q)
  else
    echo "ERROR: gradle/wrapper/gradle-wrapper.jar is missing and no 'gradle' binary is installed." >&2
    echo "       Install Gradle 8.9 (https://gradle.org/install/) and run: gradle wrapper --gradle-version 8.9" >&2
    echo "       (CI does not need this; see .github/workflows/build.yml)" >&2
    exit 1
  fi
fi

if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi

# shellcheck disable=SC2086
exec "$JAVACMD" -Xmx64m -Xms64m $JAVA_OPTS $GRADLE_OPTS \
  -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
