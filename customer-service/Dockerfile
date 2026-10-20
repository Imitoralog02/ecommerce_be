FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/customer-service-*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
