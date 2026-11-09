FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
