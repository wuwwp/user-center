# 多阶段构建：构建阶段产出可执行 jar，运行阶段仅保留 JRE。
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY deploy/maven/settings.xml /root/.m2/settings.xml
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
