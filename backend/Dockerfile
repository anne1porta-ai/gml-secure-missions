FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
# Un second essai permet de reprendre après une erreur temporaire du dépôt Maven.
RUN mvn -q -DskipTests dependency:go-offline \
    || (sleep 5 && mvn -U -q -DskipTests dependency:go-offline)
COPY src ./src
# Les tests d'integration Testcontainers sont executes avant le build (local/CI).
# Un docker build ne doit pas essayer de lancer Docker depuis son stage Maven.
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 gml \
    && mkdir -p /app/storage/documents \
    && chown -R gml:gml /app
COPY --from=build /workspace/target/gml-shield-backend-0.1.0.jar /app/app.jar
RUN chown gml:gml /app/app.jar
USER gml
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
