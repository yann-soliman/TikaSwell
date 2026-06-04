FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S tikaswell \
	&& adduser -S tikaswell -G tikaswell \
	&& mkdir -p /app/data \
	&& chown -R tikaswell:tikaswell /app

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

USER tikaswell
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
