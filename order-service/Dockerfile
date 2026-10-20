FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/order-service-*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
