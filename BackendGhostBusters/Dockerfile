FROM openjdk:27-ea-jdk-bookworm AS build

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew build -x test

FROM openjdk:27-ea-jdk-bookworm

WORKDIR /app

COPY --from=build /app/build/libs/GhostBusters-0.0.1-SNAPSHOT.jar app.jar

CMD ["java", "-jar", "app.jar"]
