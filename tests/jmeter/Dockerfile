# JMeter 运行镜像（零宿主依赖）：JRE 21 + JMeter 5.6.3
# 基础镜像走 daocloud 镜像源（本机 Docker Hub 不可达）
FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre

ARG JMETER_VERSION=5.6.3
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && curl -fsSL "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.tgz" -o /tmp/jmeter.tgz \
    && tar -xzf /tmp/jmeter.tgz -C /opt \
    && mv "/opt/apache-jmeter-${JMETER_VERSION}" /opt/jmeter \
    && rm /tmp/jmeter.tgz \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

ENV JMETER_HOME=/opt/jmeter
ENV PATH=/opt/jmeter/bin:$PATH
WORKDIR /plans
ENTRYPOINT ["jmeter"]
