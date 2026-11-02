# Etapa 1: compilación con Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests \
 && mvn -q -B test-compile \
 && java -cp target/classes:target/test-classes co.edu.uniquindio.bibliometria.VerificacionAlgoritmos

# Etapa 2: imagen liviana de ejecución
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/bibliometria-ia.jar app.jar
COPY corpus ./corpus
ENV CORPUS_DIR=/app/corpus \
    PORT=8080
EXPOSE 8080
CMD ["sh", "-c", "java -Xmx300m -Djava.awt.headless=true -jar app.jar"]
