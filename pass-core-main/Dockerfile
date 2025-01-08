FROM eclipse-temurin:17.0.13_11-jre-jammy

WORKDIR /app

COPY target/pass-core-main-*-exec.jar pass-core-main.jar
COPY entrypoint.sh .

RUN apt update \
    && apt --no-install-recommends install -y curl \
    && chmod +x entrypoint.sh \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -g 1432 passcoregroup && \
    useradd -m -u 1532 -g passcoregroup passcoreuser && \
    chown passcoreuser /app

USER passcoreuser

ENTRYPOINT ["./entrypoint.sh"]
