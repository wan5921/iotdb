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

package org.apache.iotdb.db.storageengine.dataregion.read.control;

import org.apache.iotdb.db.storageengine.dataregion.read.QueryDataSource;
import org.apache.iotdb.db.storageengine.dataregion.tsfile.TsFileResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * QueryResourceManager manages resource (file streams) used by each read job, and assign Ids to the
 * jobs. During the life cycle of a read, the following methods must be called in strict order:
 *
 * <p>1. assignQueryId - get an Id for the new read.
 *
 * <p>2. getQueryDataSource - open files for the job or reuse existing readers.
 *
 * <p>3. endQueryForGivenJob - release the resource used by this job.
 */
public class QueryResourceManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(QueryResourceManager.class);

  private final AtomicLong queryIdAtom = new AtomicLong();
  private final QueryFileManager filePathsManager;

  private QueryResourceManager() {
    filePathsManager = new QueryFileManager();
  }

  public static QueryResourceManager getInstance() {
    return QueryTokenManagerHelper.INSTANCE;
  }

  /** Register a new read. When a read request is created firstly, this method must be invoked. */
  public long assignQueryId() {
    return queryIdAtom.incrementAndGet();
  }

  /**
   * Register a read id for compaction.
   *
   * ...
   */
  public long assignCompactionQueryId() {
    long threadNum = Long.parseLong((Thread.currentThread().getName().split("-"))[5]);
    long queryId = Long.MIN_VALUE + threadNum;
    filePathsManager.addQueryId(queryId);
    return queryId;
  }

  /**
   * Get QueryDataSource and log data file paths and TsFileResource start timestamps for debugging.
   * This is the second step in the query lifecycle, invoked after assignQueryId.
   *
   * @param queryId the query id assigned by assignQueryId
   * @param dataSource the QueryDataSource containing seq and unseq TsFileResources
   */
  public void getQueryDataSource(long queryId, QueryDataSource dataSource) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("QueryResourceManager.getQueryDataSource: queryId={}", queryId);

      List<TsFileResource> seqResources = dataSource.getSeqResources();
      if (seqResources != null && !seqResources.isEmpty()) {
        LOGGER.debug(
            "QueryResourceManager.getQueryDataSource: queryId={}, seqResources count={}",
            queryId,
            seqResources.size());
        for (TsFileResource resource : seqResources) {
          LOGGER.debug(
              "QueryResourceManager.getQueryDataSource: queryId={}, seqTsFile={}, fileStartTime={}",
              queryId,
              resource.getTsFile().getAbsolutePath(),
              resource.getFileStartTime());
        }
      }

      List<TsFileResource> unseqResources = dataSource.getUnseqResources();
      if (unseqResources != null && !unseqResources.isEmpty()) {
        LOGGER.debug(
            "QueryResourceManager.getQueryDataSource: queryId={}, unseqResources count={}",
            queryId,
            unseqResources.size());
        for (TsFileResource resource : unseqResources) {
          LOGGER.debug(
              "QueryResourceManager.getQueryDataSource: queryId={}, unseqTsFile={}, fileStartTime={}",
              queryId,
              resource.getTsFile().getAbsolutePath(),
              resource.getFileStartTime());
        }
      }
    }
  }

  /**
   * Whenever the jdbc request is closed normally or abnormally, this method must be invoked. All
   * read tokens created by this jdbc request must be cleared.
   */
  // Suppress high Cognitive Complexity warning
  // attention: Since V1.0, Query Module does not use this method for cleaning
  public void endQuery(long queryId) {
    // remove usage of opened file paths of current thread
    filePathsManager.removeUsedFilesForQuery(queryId);
  }

  public QueryFileManager getQueryFileManager() {
    return filePathsManager;
  }

  private static class QueryTokenManagerHelper {

    private static final QueryResourceManager INSTANCE = new QueryResourceManager();

    private QueryTokenManagerHelper() {}
  }
}
