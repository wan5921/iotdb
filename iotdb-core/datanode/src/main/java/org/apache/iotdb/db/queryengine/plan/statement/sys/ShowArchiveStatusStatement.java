package org.apache.iotdb.db.queryengine.plan.statement.sys;

import org.apache.iotdb.db.queryengine.plan.statement.StatementType;
import org.apache.iotdb.db.queryengine.plan.statement.StatementVisitor;
import org.apache.iotdb.db.queryengine.plan.statement.metadata.ShowStatement;

public class ShowArchiveStatusStatement extends ShowStatement {

  public ShowArchiveStatusStatement() {
    super();
    statementType = StatementType.SHOW_ARCHIVE_STATUS;
  }

  @Override
  public <R, C> R accept(StatementVisitor<R, C> visitor, C context) {
    return visitor.visitShowArchiveStatus(this, context);
  }
}
