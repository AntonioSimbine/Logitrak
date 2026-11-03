# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S logitrack \
    && adduser -S logitrack -G logitrack \
    && mkdir -p /app/uploads \
    && chown -R logitrack:logitrack /app

COPY --from=build /build/target/logitrack-1.0.0.jar /app/app.jar

USER logitrack
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
