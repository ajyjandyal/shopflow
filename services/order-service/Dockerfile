FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY common/src common/src
COPY order-service/pom.xml order-service/pom.xml
COPY order-service/src order-service/src

RUN mvn -N install -DskipTests && \
    mvn -f common/pom.xml install -DskipTests && \
    mvn -f order-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/order-service/target/*.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app/app.jar"]
