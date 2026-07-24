# Base images are pulled from AWS's public mirror of Docker Official Images
# (public.ecr.aws) because docker.io is unreachable/throttled on some
# networks. The images are byte-identical to the Docker Hub ones.
FROM public.ecr.aws/docker/library/gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src ./src
RUN gradle bootJar --no-daemon -x test

FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /workspace/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
