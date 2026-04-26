#!/bin/sh
# Gradle wrapper stub - downloads Gradle if not present
# In CI, android-actions/setup-android provides Gradle

DEFAULT_GRADLE_VERSION="8.5"
GRADLE_VERSION="${GRADLE_VERSION:-$DEFAULT_GRADLE_VERSION}"
GRADLE_HOME="${GRADLE_HOME:-$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin}"

# Try to find gradle in PATH
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

# Download Gradle if needed
if [ ! -f "${GRADLE_HOME}/gradle-${GRADLE_VERSION}/bin/gradle" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    mkdir -p "${GRADLE_HOME}"
    curl -sL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip
    unzip -q /tmp/gradle.zip -d "${GRADLE_HOME}/"
    mv "${GRADLE_HOME}/gradle-${GRADLE_VERSION}" "${GRADLE_HOME}/gradle-${GRADLE_VERSION}" 2>/dev/null || true
fi

exec "${GRADLE_HOME}/gradle-${GRADLE_VERSION}/bin/gradle" "$@"
