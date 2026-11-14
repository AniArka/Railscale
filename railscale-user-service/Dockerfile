FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY railscale-user-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
