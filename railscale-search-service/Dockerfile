FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY railscale-search-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
