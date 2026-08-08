# Etapa de build: compila el jar con Maven (los tests corren fuera, necesitan Testcontainers)
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# Etapa de runtime: JRE mínimo + curl para el healthcheck de compose
FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --create-home appuser
USER appuser
WORKDIR /app
COPY --from=build /build/target/finance-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
