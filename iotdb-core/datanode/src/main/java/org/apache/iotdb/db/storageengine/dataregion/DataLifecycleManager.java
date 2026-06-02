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

package org.apache.iotdb.db.storageengine.dataregion;

import org.apache.iotdb.commons.concurrent.IoTDBThreadPoolFactory;
import org.apache.iotdb.commons.concurrent.ThreadName;
import org.apache.iotdb.commons.concurrent.WrappedRunnable;
import org.apache.iotdb.commons.concurrent.threadpool.ScheduledExecutorUtil;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.storageengine.dataregion.tsfile.TsFileManager;
import org.apache.iotdb.db.storageengine.dataregion.tsfile.TsFileResource;
import org.apache.tsfile.external.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DataLifecycleManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(DataLifecycleManager.class);

  private static final IoTDBConfig CONFIG = IoTDBDescriptor.getInstance().getConfig();

  private final DataRegion dataRegion;
  private final TsFileManager tsFileManager;

  private final ScheduledExecutorService scheduledExecutorService;
  private Future<?> future;

  // Archive progress tracking
  private final AtomicLong totalArchivedFiles = new AtomicLong(0);
  private final AtomicLong totalArchivedSize = new AtomicLong(0);
  private final List<String> archivedFileNames = new CopyOnWriteArrayList<>();
  private volatile long lastArchiveTime = 0L;

  private volatile boolean isRunning = false;

  public DataLifecycleManager(DataRegion dataRegion, TsFileManager tsFileManager) {
    this.dataRegion = dataRegion;
    this.tsFileManager = tsFileManager;
    this.scheduledExecutorService =
        IoTDBThreadPoolFactory.newSingleThreadScheduledExecutor(
            ThreadName.DATA_LIFECYCLE_MANAGER.name());
  }

  public void start() {
    if (CONFIG.getDataLifecycleDays() <= 0) {
      LOGGER.info("Data lifecycle is disabled (dataLifecycleDays <= 0)");
      return;
    }

    if (isRunning) {
      LOGGER.warn("Data lifecycle manager is already running");
      return;
    }

    synchronized (this) {
      if (!isRunning) {
        future =
            ScheduledExecutorUtil.safelyScheduleWithFixedDelay(
                scheduledExecutorService,
                new WrappedRunnable() {
                  @Override
                  public void runMayThrow() {
                    try {
                      scanAndArchiveExpiredFiles();
                    } catch (Exception e) {
                      LOGGER.error("Error during data lifecycle scan", e);
                    }
                  }
                },
                1, // Initial delay in minutes
                60 * 24, // Period: run once per day in minutes
                TimeUnit.MINUTES);
        isRunning = true;
        LOGGER.info(
            "Data lifecycle manager started with dataLifecycleDays = {}",
            CONFIG.getDataLifecycleDays());
      }
    }
  }

  public void stop() {
    synchronized (this) {
      if (future != null) {
        future.cancel(false);
        future = null;
      }
      isRunning = false;
      LOGGER.info("Data lifecycle manager stopped");
    }
  }

  private void scanAndArchiveExpiredFiles() {
    if (CONFIG.getDataLifecycleDays() <= 0) {
      return;
    }

    long currentTime = System.currentTimeMillis();
    long expirationTime =
        currentTime - TimeUnit.DAYS.toMillis(CONFIG.getDataLifecycleDays());

    LOGGER.info("Starting data lifecycle scan for expired files (older than {} days)",
        CONFIG.getDataLifecycleDays());

    // Collect all sequence and unsequence files
    List<TsFileResource> allFiles = new ArrayList<>();
    allFiles.addAll(tsFileManager.getTsFileList(true));
    allFiles.addAll(tsFileManager.getTsFileList(false));

    List<TsFileResource> expiredFiles = new ArrayList<>();

    for (TsFileResource tsFileResource : allFiles) {
      if (tsFileResource.isClosed() && tsFileResource.getFileEndTime() < expirationTime) {
        expiredFiles.add(tsFileResource);
      }
    }

    LOGGER.info("Found {} expired files to archive", expiredFiles.size());

    // Archive expired files
    for (TsFileResource tsFileResource : expiredFiles) {
      try {
        archiveTsFile(tsFileResource);
      } catch (IOException e) {
        LOGGER.error("Failed to archive TsFile: {}", tsFileResource.getTsFile().getPath(), e);
      }
    }
  }

  private void archiveTsFile(TsFileResource tsFileResource) throws IOException {
    File sourceFile = tsFileResource.getFile();
    File archiveDir = new File(CONFIG.getArchivePath());

    // Create archive directory if it doesn't exist
    if (!archiveDir.exists()) {
      FileUtils.forceMkdir(archiveDir);
    }

    // Create database and data region subdirectories in archive
    File regionArchiveDir = new File(
        archiveDir,
        dataRegion.getDatabaseName() + File.separator + dataRegion.getDataRegionIdString());
    if (!regionArchiveDir.exists()) {
      FileUtils.forceMkdir(regionArchiveDir);
    }

    File destFile = new File(regionArchiveDir, sourceFile.getName());

    LOGGER.info("Archiving TsFile: {} -> {}", sourceFile.getPath(), destFile.getPath());

    // Move the TsFile and its resource file
    FileUtils.moveFile(sourceFile, destFile);

    File resourceFile = new File(sourceFile.getPath() + ".resource");
    if (resourceFile.exists()) {
      File destResourceFile = new File(regionArchiveDir, resourceFile.getName());
      FileUtils.moveFile(resourceFile, destResourceFile);
    }

    // Move modification file if exists
    File modFile = new File(sourceFile.getPath() + ".mod");
    if (modFile.exists()) {
      File destModFile = new File(regionArchiveDir, modFile.getName());
      FileUtils.moveFile(modFile, destModFile);
    }

    // Remove from TsFileManager
    tsFileManager.remove(tsFileResource, tsFileResource.isSeq());

    // Update statistics
    long fileSize = destFile.length();
    totalArchivedFiles.incrementAndGet();
    totalArchivedSize.addAndGet(fileSize);
    archivedFileNames.add(destFile.getName());
    lastArchiveTime = System.currentTimeMillis();

    LOGGER.info("Successfully archived TsFile: {}", destFile.getName());
  }

  public long getTotalArchivedFiles() {
    return totalArchivedFiles.get();
  }

  public long getTotalArchivedSize() {
    return totalArchivedSize.get();
  }

  public List<String> getArchivedFileNames() {
    return new ArrayList<>(archivedFileNames);
  }

  public boolean isRunning() {
    return isRunning;
  }

  public long getLastArchiveTime() {
    return lastArchiveTime;
  }
}
