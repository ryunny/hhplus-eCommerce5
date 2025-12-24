FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle clean build -x test

FROM eclipse-temurin:17-jdk
WORKDIR /app

# Pinpoint Agent 다운로드 및 설치
ARG PINPOINT_VERSION=2.5.4
RUN apt-get update && \
    apt-get install -y wget && \
    wget -O pinpoint-agent.tar.gz https://github.com/pinpoint-apm/pinpoint/releases/download/v${PINPOINT_VERSION}/pinpoint-agent-${PINPOINT_VERSION}.tar.gz && \
    mkdir -p /pinpoint-agent && \
    tar -zxf pinpoint-agent.tar.gz -C /pinpoint-agent --strip-components=1 && \
    rm pinpoint-agent.tar.gz && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 애플리케이션 JAR 복사
COPY --from=build /app/build/libs/*.jar /app/app.jar

# Pinpoint Agent 설정
ENV PINPOINT_AGENT_ID=ecommerce-app
ENV PINPOINT_AGENT_NAME=ecommerce-service
ENV PINPOINT_COLLECTOR_IP=pinpoint-collector

EXPOSE 8080

ENTRYPOINT ["java", \
    "-javaagent:/pinpoint-agent/pinpoint-bootstrap-2.5.4.jar", \
    "-Dpinpoint.agentId=ecommerce-app", \
    "-Dpinpoint.applicationName=ecommerce-service", \
    "-Dpinpoint.profiler.profiles.active=release", \
    "-Dpinpoint.collector.ip=pinpoint-collector", \
    "-jar", "/app/app.jar"]
