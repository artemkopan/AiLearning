FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update && apt-get install -y --no-install-recommends \
    tmux \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -m -s /bin/bash dev

COPY scripts/ /usr/local/bin/
RUN chmod +x /usr/local/bin/notify-status /usr/local/bin/notify-done /usr/local/bin/claude-run

WORKDIR /app
COPY backend/build/libs/backend-all.jar /app/backend.jar

ENV PROJECTS_ROOT=/projects
ENV PORT=8080

EXPOSE 8080

USER dev
CMD ["java", "-jar", "/app/backend.jar"]
