FROM eclipse-temurin:17.0.8_7-jre-jammy

WORKDIR /app

COPY target/pass-core-main-*-exec.jar pass-core-main.jar
COPY entrypoint.sh .

RUN apt update \
    && apt install -y curl \
    && chmod +x entrypoint.sh \
    && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["./entrypoint.sh"]
