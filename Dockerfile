# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

FROM eclipse-temurin:11-jre-slim

ARG IOTDB_VERSION=2.0.7-SNAPSHOT
ENV IOTDB_VERSION=${IOTDB_VERSION}
ENV IOTDB_HOME=/opt/iotdb

RUN apt-get update \
    && apt-get install -y --no-install-recommends procps unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
COPY distribution/target/apache-iotdb-${IOTDB_VERSION}-all-bin.zip /tmp/apache-iotdb.zip
RUN unzip /tmp/apache-iotdb.zip -d /opt \
    && mv /opt/apache-iotdb-${IOTDB_VERSION}-all-bin ${IOTDB_HOME} \
    && rm /tmp/apache-iotdb.zip \
    && mkdir -p ${IOTDB_HOME}/logs

WORKDIR ${IOTDB_HOME}
EXPOSE 6667

CMD ["bash", "-c", "./sbin/start-standalone.sh && while pgrep -f 'org.apache.iotdb.confignode.service.ConfigNode|org.apache.iotdb.db.service.DataNode' >/dev/null; do sleep 5; done"]
