# ── Stage 1: Build Frontend ──
FROM node:18-alpine AS frontend
WORKDIR /app/admin-ui
COPY admin-ui/package.json admin-ui/package-lock.json ./
RUN npm ci
COPY admin-ui/ ./
RUN npm run build

# ── Stage 2: Build Backend ──
FROM maven:3.9-eclipse-temurin-17-alpine AS backend
WORKDIR /app
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw
RUN chmod +x mvnw && ./mvnw dependency:resolve -B

COPY src ./src
# Copy frontend build into Spring Boot static resources
COPY --from=frontend /app/admin-ui/dist/ ./src/main/resources/static/
RUN ./mvnw package -DskipTests -B

# ── Stage 3: Runtime ──
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar

# Railway provides PORT env var
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
