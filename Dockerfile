# Estágio 1: Build
FROM maven:3.9-eclipse-temurin-26-alpine AS build
WORKDIR /app
COPY pom.xml .
# Baixa as dependências numa camada própria: só refaz quando o pom.xml muda.
RUN mvn --batch-mode dependency:go-offline
COPY src ./src
# Os testes não rodam aqui: os de integração usam Testcontainers e precisam de Docker,
# que não existe dentro do build da imagem. Eles rodam no CI e com "mvnw verify".
RUN mvn --batch-mode clean package -DskipTests

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

ENTRYPOINT ["java","-Duser.timezone=America/Sao_Paulo","-jar","app.jar"]
