FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY common/src common/src
COPY user-service/pom.xml user-service/pom.xml
COPY user-service/src user-service/src

RUN mvn -N install -DskipTests && \
    mvn -f common/pom.xml install -DskipTests && \
    mvn -f user-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/user-service/target/*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
