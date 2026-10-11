# Materin 边缘网关运行镜像：先在宿主机 mvn package，再构建本镜像
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/materin-gateway-*.jar app.jar
# 配置经环境变量（MATERIN_GATEWAY_*）或挂载 /app/config/application.yml 覆盖
ENV JAVA_OPTS=""
EXPOSE 8090
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
