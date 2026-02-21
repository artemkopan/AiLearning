FROM ubuntu:24.04 AS dev-toolbox

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ARG DEBIAN_FRONTEND=noninteractive
ARG GRADLE_VERSION=8.10.2
ARG ANDROID_CMDLINE_TOOLS_VERSION=14742923
ARG TARGETARCH

ENV JAVA17_HOME=/usr/lib/jvm/java-17-openjdk-${TARGETARCH}
ENV JAVA21_HOME=/usr/lib/jvm/java-21-openjdk-${TARGETARCH}
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-${TARGETARCH}
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
    && rm -f /tmp/gradle.zip

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

# Install Claude Code CLI and Cursor CLI.
RUN npm install -g @anthropic-ai/claude-code \
    && curl -fsSL https://cursor.com/install | bash \
    && ln -sf /root/.local/bin/cursor-agent /usr/local/bin/cursor-agent

# Add gdclaude helper to interactive shells in the container.
RUN cat > /etc/profile.d/gdclaude.sh <<'EOF'
gdclaude() {
  local token="${GOCODE_API_TOKEN:-${ANTHROPIC_AUTH_TOKEN:-}}"
  if [ -z "${token}" ]; then
    echo "gdclaude: set GOCODE_API_TOKEN (or ANTHROPIC_AUTH_TOKEN) first." >&2
    return 1
  fi

  env \
    GOCODE_API_TOKEN="${token}" \
    ANTHROPIC_AUTH_TOKEN="${token}" \
    ANTHROPIC_DEFAULT_OPUS_MODEL="${ANTHROPIC_DEFAULT_OPUS_MODEL:-claude-opus-4-6}" \
    ANTHROPIC_DEFAULT_HAIKU_MODEL="${ANTHROPIC_DEFAULT_HAIKU_MODEL:-claude-haiku-4-5-20251001}" \
    ANTHROPIC_MODEL="${ANTHROPIC_MODEL:-claude-opus-4-6}" \
    HTTPS_PROXY="${HTTPS_PROXY:-}" \
    HTTP_PROXY="${HTTP_PROXY:-}" \
    NO_PROXY="${NO_PROXY:-localhost,127.0.0.1}" \
    ANTHROPIC_BASE_URL="${ANTHROPIC_BASE_URL:-}" \
    claude "$@"
}
EOF
RUN chmod 0644 /etc/profile.d/gdclaude.sh \
    && grep -qxF 'source /etc/profile.d/gdclaude.sh' /root/.bashrc \
      || echo 'source /etc/profile.d/gdclaude.sh' >> /root/.bashrc

# Quick sanity checks during build.
RUN java -version \
    && "${JAVA17_HOME}/bin/java" -version \
    && "${JAVA21_HOME}/bin/java" -version \
    && gradle --version \
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

FROM eclipse-temurin:17-jre AS backend-runtime
WORKDIR /opt/app

RUN apt-get update \
    && apt-get install -y --no-install-recommends tmux \
    && rm -rf /var/lib/apt/lists/*

COPY --from=backend-builder /app/backend/build/install/backend ./backend

ENV PORT=8080
EXPOSE 8080

CMD ["./backend/bin/backend"]
