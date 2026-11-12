# API Logitrack — imagem de produção para o Railway.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependências primeiro, para aproveitar a cache de camadas.
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# /data é o volume persistente (fotografias das guias).
ENV PORT=8080 \
    LOGITRACK_UPLOAD_DIR=/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
