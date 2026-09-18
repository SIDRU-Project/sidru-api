# SIDRU API — imagen Docker multi-stage.
# Etapa 1: compila el JAR con Maven (Java 21). Etapa 2: runtime liviano con solo el JRE.

# ── build ──────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cachea dependencias: si pom.xml no cambia, no se vuelven a descargar.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ── runtime ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# JAVA_OPTS lo inyecta docker-compose (límite de heap para la VM de 2 GB).
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar app.jar"]
