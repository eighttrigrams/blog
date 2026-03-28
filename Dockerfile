FROM clojure:temurin-21-tools-deps-alpine AS builder

WORKDIR /opt

COPY deps.edn build.clj ./
COPY src ./src
COPY resources ./resources

RUN clj -Sdeps '{:mvn/local-repo "./.m2/repository"}' -T:build uber

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

COPY --from=builder /opt/target/blog-0.0.1-standalone.jar /app/app.jar
COPY config.prod.edn /app/config.edn

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
