FROM openjdk:11-slim

WORKDIR /iotdb

# 复制并解压 IoTDB 发行包
COPY distribution/target/apache-iotdb-2.0.7-SNAPSHOT-all-bin.zip /iotdb/
RUN unzip -q apache-iotdb-2.0.7-SNAPSHOT-all-bin.zip && \
    rm apache-iotdb-2.0.7-SNAPSHOT-all-bin.zip && \
    mv apache-iotdb-2.0.7-SNAPSHOT/* . && \
    rmdir apache-iotdb-2.0.7-SNAPSHOT

# 暴露 IoTDB 端口
EXPOSE 6667

# 启动 IoTDB
CMD ["./sbin/start-datanode.sh"]
