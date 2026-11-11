# Etapa 1: compila o projeto
FROM maven:3.9-amazoncorretto-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -q package -DskipTests

# Etapa 2: imagem final, só com o Java e o jar
FROM amazoncorretto:25-al2023-headless
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
