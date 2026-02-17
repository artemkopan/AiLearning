FROM gradle:8.10-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle :backend:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /opt/app
COPY --from=builder /app/backend/build/install/backend ./backend
ENV PORT=8080
EXPOSE 8080
CMD ["./backend/bin/backend"]
