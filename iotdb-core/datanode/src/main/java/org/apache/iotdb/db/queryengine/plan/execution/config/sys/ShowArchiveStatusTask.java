/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.queryengine.plan.execution.config.sys;

import org.apache.iotdb.commons.schema.column.ColumnHeader;
import org.apache.iotdb.commons.schema.column.ColumnHeaderConstant;
import org.apache.iotdb.db.queryengine.common.header.DatasetHeaderFactory;
import org.apache.iotdb.db.queryengine.plan.execution.config.ConfigTaskResult;
import org.apache.iotdb.db.queryengine.plan.execution.config.IConfigTask;
import org.apache.iotdb.db.queryengine.plan.execution.config.executor.IConfigTaskExecutor;
import org.apache.iotdb.db.storageengine.StorageEngine;
import org.apache.iotdb.db.storageengine.dataregion.DataRegion;
import org.apache.iotdb.rpc.TSStatusCode;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.apache.tsfile.common.conf.TSFileConfig;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.read.common.block.TsBlockBuilder;
import org.apache.tsfile.utils.Binary;

import java.util.List;
import java.util.stream.Collectors;

public class ShowArchiveStatusTask implements IConfigTask {

  @Override
  public ListenableFuture<ConfigTaskResult> execute(IConfigTaskExecutor configTaskExecutor) {
    SettableFuture<ConfigTaskResult> future = SettableFuture.create();
    List<TSDataType> outputDataTypes =
        ColumnHeaderConstant.SHOW_ARCHIVE_STATUS_COLUMN_HEADERS.stream()
            .map(ColumnHeader::getColumnType)
            .collect(Collectors.toList());
    TsBlockBuilder builder = new TsBlockBuilder(outputDataTypes);
    for (DataRegion dataRegion : StorageEngine.getInstance().getAllDataRegions()) {
      DataRegion.ArchiveStatusSnapshot snapshot = dataRegion.getArchiveStatusSnapshot();
      builder.getTimeColumnBuilder().writeLong(0L);
      builder
          .getColumnBuilder(0)
          .writeBinary(new Binary(snapshot.getDatabase(), TSFileConfig.STRING_CHARSET));
      builder
          .getColumnBuilder(1)
          .writeBinary(new Binary(snapshot.getDataRegionId(), TSFileConfig.STRING_CHARSET));
      builder.getColumnBuilder(2).writeInt(snapshot.getLifecycleDays());
      writeNullableBinary(builder, 3, snapshot.getArchivePath());
      builder
          .getColumnBuilder(4)
          .writeBinary(new Binary(snapshot.getStatus(), TSFileConfig.STRING_CHARSET));
      builder.getColumnBuilder(5).writeLong(snapshot.getPendingTsFileCount());
      builder.getColumnBuilder(6).writeLong(snapshot.getPendingTsFileSize());
      builder.getColumnBuilder(7).writeLong(snapshot.getArchivedTsFileCount());
      builder.getColumnBuilder(8).writeLong(snapshot.getArchivedTsFileSize());
      writeNullableTimestamp(builder, 9, snapshot.getLastScanTime());
      writeNullableTimestamp(builder, 10, snapshot.getLastArchiveTime());
      writeNullableBinary(builder, 11, snapshot.getLastError());
      builder.declarePosition();
    }
    future.set(
        new ConfigTaskResult(
            TSStatusCode.SUCCESS_STATUS,
            builder.build(),
            DatasetHeaderFactory.getShowArchiveStatusHeader()));
    return future;
  }

  private static void writeNullableBinary(TsBlockBuilder builder, int index, String value) {
    if (value == null || value.isEmpty()) {
      builder.getColumnBuilder(index).appendNull();
      return;
    }
    builder.getColumnBuilder(index).writeBinary(new Binary(value, TSFileConfig.STRING_CHARSET));
  }

  private static void writeNullableTimestamp(TsBlockBuilder builder, int index, long value) {
    if (value <= 0) {
      builder.getColumnBuilder(index).appendNull();
      return;
    }
    builder.getColumnBuilder(index).writeLong(value);
  }
}
