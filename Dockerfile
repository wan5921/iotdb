FROM openjdk:11-slim

ARG IOTDB_VERSION

COPY distribution/target/iotdb-${IOTDB_VERSION}/ /opt/iotdb/

WORKDIR /opt/iotdb

EXPOSE 6667

CMD sbin/start-standalone.sh && tail -F logs/*.log
