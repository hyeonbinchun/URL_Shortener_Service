FROM eclipse-temurin:17-jre

# Maven outputs to target/
COPY target/*SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]