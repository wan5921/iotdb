FROM eclipse-temurin:11-jre-slim

ARG IOTDB_VERSION=2.0.7-SNAPSHOT

WORKDIR /iotdb

# 复制并解压 IoTDB 发行包
COPY distribution/target/apache-iotdb-${IOTDB_VERSION}-all-bin.zip /iotdb/
RUN unzip -q apache-iotdb-${IOTDB_VERSION}-all-bin.zip && \
    rm apache-iotdb-${IOTDB_VERSION}-all-bin.zip && \
    mv apache-iotdb-${IOTDB_VERSION}/* . && \
    rmdir apache-iotdb-${IOTDB_VERSION}

# 暴露 IoTDB 端口
EXPOSE 6667

# 启动 IoTDB
CMD ["./sbin/start-datanode.sh"]
