FROM ubuntu:24.04 AS dev-toolbox

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ARG DEBIAN_FRONTEND=noninteractive
ARG GRADLE_VERSION=8.10.2
ARG GRADLE_85_VERSION=8.5
ARG ANDROID_CMDLINE_TOOLS_VERSION=14742923
ARG TARGETARCH

ENV JAVA17_HOME=/usr/lib/jvm/java-17-openjdk-${TARGETARCH}
ENV JAVA21_HOME=/usr/lib/jvm/java-21-openjdk-${TARGETARCH}
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-${TARGETARCH}
ENV GRADLE_85_HOME=/opt/gradle-8.5
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=/root/.local/bin:/opt/gradle/bin:${JAVA_HOME}/bin:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    git \
    gnupg \
    nodejs \
    npm \
    openjdk-17-jdk \
    openjdk-21-jdk \
    tmux \
    unzip \
    wget \
    xz-utils \
    zip \
    && rm -rf /var/lib/apt/lists/*

# Install Gradle from official distribution.
RUN curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s /opt/gradle-${GRADLE_VERSION} /opt/gradle \
    && rm -f /tmp/gradle.zip \
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_85_VERSION}-bin.zip" -o /tmp/gradle85.zip \
    && unzip -q /tmp/gradle85.zip -d /opt \
    && ln -sf "${GRADLE_85_HOME}/bin/gradle" /usr/local/bin/gradle85 \
    && rm -f /tmp/gradle85.zip

# Install Android SDK command-line tools + platform tools.
RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" /root/.android \
    && touch /root/.android/repositories.cfg \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/android-cli \
    && mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && if [ -d /tmp/android-cli/cmdline-tools/bin ]; then \
         mv /tmp/android-cli/cmdline-tools/* "${ANDROID_SDK_ROOT}/cmdline-tools/latest/"; \
       else \
         mv /tmp/android-cli/cmdline-tools/cmdline-tools/* "${ANDROID_SDK_ROOT}/cmdline-tools/latest/"; \
       fi \
    && rm -rf /tmp/android-cli /tmp/cmdline-tools.zip \
    && (yes | sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --licenses || true) \
    && if [ "${TARGETARCH}" = "amd64" ]; then \
         sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" "platform-tools"; \
       else \
         echo "Skipping Android platform-tools on ${TARGETARCH} (adb binary is not available for this architecture)."; \
       fi

# Install Claude Code (native installer) and Cursor CLI.
RUN curl -fsSL https://claude.ai/install.sh | bash \
    && curl -fsSL https://cursor.com/install | bash \
    && ln -sf /root/.local/bin/cursor-agent /usr/local/bin/cursor-agent \
    && ln -sf /usr/local/bin/cursor-agent /usr/local/bin/agent

# Add gdclaude helper to interactive shells in the container.
RUN cat > /etc/profile.d/gdclaude.sh <<'EOF'
gdclaude() {
  token="${GOCODE_API_TOKEN:-${ANTHROPIC_AUTH_TOKEN:-}}"
  if [ -n "${token}" ]; then
    env \
      GOCODE_API_TOKEN="${token}" \
      ANTHROPIC_AUTH_TOKEN="${token}" \
      ANTHROPIC_DEFAULT_OPUS_MODEL="${ANTHROPIC_DEFAULT_OPUS_MODEL:-}" \
      ANTHROPIC_DEFAULT_HAIKU_MODEL="${ANTHROPIC_DEFAULT_HAIKU_MODEL:-}" \
      ANTHROPIC_MODEL="${ANTHROPIC_MODEL:-}" \
      HTTPS_PROXY="${HTTPS_PROXY:-}" \
      HTTP_PROXY="${HTTP_PROXY:-}" \
      NO_PROXY="${NO_PROXY:-}" \
      ANTHROPIC_BASE_URL="${ANTHROPIC_BASE_URL:-}" \
      claude "$@"
  else
    env \
      ANTHROPIC_DEFAULT_OPUS_MODEL="${ANTHROPIC_DEFAULT_OPUS_MODEL:-}" \
      ANTHROPIC_DEFAULT_HAIKU_MODEL="${ANTHROPIC_DEFAULT_HAIKU_MODEL:-}" \
      ANTHROPIC_MODEL="${ANTHROPIC_MODEL:-}" \
      HTTPS_PROXY="${HTTPS_PROXY:-}" \
      HTTP_PROXY="${HTTP_PROXY:-}" \
      NO_PROXY="${NO_PROXY:-}" \
      ANTHROPIC_BASE_URL="${ANTHROPIC_BASE_URL:-}" \
      claude "$@"
  fi
}
EOF
RUN chmod 0644 /etc/profile.d/gdclaude.sh \
    && grep -qxF 'source /etc/profile.d/gdclaude.sh' /root/.bashrc \
      || echo 'source /etc/profile.d/gdclaude.sh' >> /root/.bashrc

RUN cat > /usr/local/bin/bootstrap-agent-hooks <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

hooks_dir="${HOOKS_DIR:-/docker/shared/ai-learning/scripts}"
claude_settings_local="/root/.claude/settings.local.json"
cursor_hooks="/root/.cursor/hooks.json"

mkdir -p /root/.claude /root/.cursor

if [ ! -f "${claude_settings_local}" ]; then
  cat > "${claude_settings_local}" <<JSON
{
  "hooks": {
    "Notification": [
      {
        "matcher": "*",
        "hooks": [
          {
            "type": "command",
            "command": "${hooks_dir}/hook-claude-notification"
          }
        ]
      }
    ]
  }
}
JSON
fi

if [ ! -f "${cursor_hooks}" ]; then
  cat > "${cursor_hooks}" <<JSON
{
  "version": 1,
  "hooks": {
    "beforeShellExecution": [
      { "command": "${hooks_dir}/hook-cursor-before-shell" }
    ],
    "afterShellExecution": [
      { "command": "${hooks_dir}/hook-cursor-after-shell" }
    ]
  }
}
JSON
fi

exec "$@"
EOF
RUN chmod +x /usr/local/bin/bootstrap-agent-hooks

# Quick sanity checks during build.
RUN java -version \
    && "${JAVA17_HOME}/bin/java" -version \
    && "${JAVA21_HOME}/bin/java" -version \
    && gradle --version \
    && gradle85 --version \
    && node --version \
    && npm --version \
    && claude --version \
    && cursor-agent --version \
    && sdkmanager --version \
    && if [ "${TARGETARCH}" = "amd64" ]; then adb version; fi

WORKDIR /workspace
CMD ["/bin/bash"]

FROM eclipse-temurin:17-jdk AS backend-builder
WORKDIR /app

COPY . .
RUN chmod +x gradlew
RUN ./gradlew :backend:installDist --no-daemon

FROM dev-toolbox AS backend-runtime
WORKDIR /opt/app

COPY --from=backend-builder /app/backend/build/install/backend ./backend

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/bootstrap-agent-hooks"]
CMD ["./backend/bin/backend"]
