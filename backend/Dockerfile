FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

COPY backend /build/backend
COPY api /build/api

WORKDIR /build/backend

RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /build/backend/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]


