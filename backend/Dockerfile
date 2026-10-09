# ---- build stage: Amazon Corretto 21 (AWS's open-source OpenJDK build) ----
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- run stage ----
FROM amazoncorretto:21-alpine
WORKDIR /app
COPY --from=build /app/target/jalmarg.jar app.jar
ENV PORT=8091
EXPOSE 8091
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
