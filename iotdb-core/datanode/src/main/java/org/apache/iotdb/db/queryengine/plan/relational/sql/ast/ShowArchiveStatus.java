package org.apache.iotdb.db.queryengine.plan.relational.sql.ast;

public class ShowArchiveStatus extends ShowStatement {

  public ShowArchiveStatus() {
    super(null);
  }

  @Override
  public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
    return visitor.visitShowArchiveStatus(this, context);
  }
}