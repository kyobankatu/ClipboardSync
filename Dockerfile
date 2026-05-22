FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY src src

RUN chmod +x gradlew \
    && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

ENV PORT=8080

COPY --from=build /workspace/build/libs/*.jar /app/clipboard-sync.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/clipboard-sync.jar"]
