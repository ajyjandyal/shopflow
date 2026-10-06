FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY common/src common/src
COPY product-service/pom.xml product-service/pom.xml
COPY product-service/src product-service/src

RUN mvn -N install -DskipTests && \
    mvn -f common/pom.xml install -DskipTests && \
    mvn -f product-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/product-service/target/*.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/app.jar"]
