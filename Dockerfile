# Estágio 1: Build
FROM maven:3.9.15-eclipse-temurin-26-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode clean package

# Estágio 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Criar usuário não-root para segurança
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java","-jar","app.jar"]
