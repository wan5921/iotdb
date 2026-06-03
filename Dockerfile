#
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
#

FROM eclipse-temurin:11-jre-slim

ARG IOTDB_VERSION=2.0.7-SNAPSHOT

RUN apt-get update \
  && apt-get install -y unzip \
  && rm -rf /var/lib/apt/lists/*

COPY distribution/target/apache-iotdb-${IOTDB_VERSION}-all-bin.zip /tmp/

RUN unzip /tmp/apache-iotdb-${IOTDB_VERSION}-all-bin.zip -d /opt \
  && rm /tmp/apache-iotdb-${IOTDB_VERSION}-all-bin.zip \
  && mv /opt/apache-iotdb-${IOTDB_VERSION}-all-bin /opt/iotdb

ENV IOTDB_HOME=/opt/iotdb

WORKDIR /opt/iotdb

EXPOSE 6667

VOLUME /opt/iotdb/data
VOLUME /opt/iotdb/logs

ENTRYPOINT ["/opt/iotdb/sbin/start-standalone.sh"]