package org.apache.iotdb.db.queryengine.plan.execution.config.session;

import org.apache.iotdb.db.queryengine.common.header.DatasetHeaderFactory;
import org.apache.iotdb.db.queryengine.plan.execution.config.ConfigTaskResult;
import org.apache.iotdb.db.queryengine.plan.execution.config.IConfigTask;
import org.apache.iotdb.db.queryengine.plan.execution.config.executor.IConfigTaskExecutor;
import org.apache.iotdb.db.queryengine.plan.execution.memory.StatementMemorySourceVisitor;
import org.apache.iotdb.rpc.TSStatusCode;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

public class ShowArchiveStatusTask implements IConfigTask {

  public ShowArchiveStatusTask() {
    // Empty constructor
  }

  public static void buildTsBlock(SettableFuture<ConfigTaskResult> future) {
    future.set(
        new ConfigTaskResult(
            TSStatusCode.SUCCESS_STATUS,
            StatementMemorySourceVisitor.getArchiveStatusResult(),
            DatasetHeaderFactory.getShowArchiveStatusHeader()));
  }

  @Override
  public ListenableFuture<ConfigTaskResult> execute(IConfigTaskExecutor configTaskExecutor)
      throws InterruptedException {
    return configTaskExecutor.showArchiveStatus();
  }
}
