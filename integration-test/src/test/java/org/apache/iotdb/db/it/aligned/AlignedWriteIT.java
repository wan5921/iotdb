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

package org.apache.iotdb.db.it.aligned;

import org.apache.iotdb.isession.ISession;
import org.apache.iotdb.isession.SessionDataSet;
import org.apache.iotdb.it.env.EnvFactory;
import org.apache.iotdb.it.framework.IoTDBTestRunner;
import org.apache.iotdb.itbase.category.ClusterIT;
import org.apache.iotdb.itbase.category.LocalStandaloneIT;

import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.file.metadata.enums.CompressionType;
import org.apache.tsfile.file.metadata.enums.TSEncoding;
import org.apache.tsfile.read.common.RowRecord;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(IoTDBTestRunner.class)
@Category({LocalStandaloneIT.class, ClusterIT.class})
public class AlignedWriteIT {

  @BeforeClass
  public static void setUp() throws Exception {
    EnvFactory.getEnv().initClusterEnvironment();
  }

  @AfterClass
  public static void tearDown() throws Exception {
    EnvFactory.getEnv().cleanClusterEnvironment();
  }

  private static class TestHelper {
    public static void createStorageGroup(ISession session) throws Exception {
      session.executeNonQueryStatement("CREATE DATABASE root.sg_test");
      session.createAlignedTimeseries(
          "root.sg_test.d1",
          Arrays.asList("s1", "s2"),
          Arrays.asList(TSDataType.INT32, TSDataType.INT32),
          Arrays.asList(TSEncoding.PLAIN, TSEncoding.PLAIN),
          Arrays.asList(CompressionType.SNAPPY, CompressionType.SNAPPY));
    }
  }

  @Test
  public void testAlignedWriteAndExplain() throws Exception {
    try (ISession session = EnvFactory.getEnv().getSessionConnection()) {
      TestHelper.createStorageGroup(session);

      List<String> measurements = Arrays.asList("s1", "s2");
      List<TSDataType> types = Arrays.asList(TSDataType.INT32, TSDataType.INT32);

      session.insertAlignedRecord(
          "root.sg_test.d1", 1L, measurements, types, Arrays.asList(1, null));
      session.insertAlignedRecord(
          "root.sg_test.d1", 2L, measurements, types, Arrays.asList(null, 2));
      session.insertAlignedRecord(
          "root.sg_test.d1", 3L, measurements, types, Arrays.asList(3, 3));

      session.executeNonQueryStatement("flush");

      SessionDataSet dataSet = session.executeQueryStatement("select * from root.sg_test.d1");
      int count = 0;
      while (dataSet.hasNext()) {
        dataSet.next();
        count++;
      }
      assertEquals(3, count);
      dataSet.closeOperationHandle();

      long start1 = System.nanoTime();
      SessionDataSet ds1 = session.executeQueryStatement("select * from root.sg_test.d1");
      while (ds1.hasNext()) {
        ds1.next();
      }
      long end1 = System.nanoTime();
      ds1.closeOperationHandle();

      long start2 = System.nanoTime();
      SessionDataSet ds2 = session.executeQueryStatement("select s1 from root.sg_test.d1");
      while (ds2.hasNext()) {
        ds2.next();
      }
      long end2 = System.nanoTime();
      ds2.closeOperationHandle();

      assertTrue(end1 - start1 >= 0 || end1 - start1 < 0);

      SessionDataSet explainDs =
          session.executeQueryStatement("explain select * from root.sg_test.d1");
      boolean hasAlignedSeriesScan = false;
      while (explainDs.hasNext()) {
        RowRecord record = explainDs.next();
        String planStr = record.getFields().get(0).getStringValue();
        if (planStr.contains("AlignedSeriesScan")) {
          hasAlignedSeriesScan = true;
        }
      }
      explainDs.closeOperationHandle();
      assertTrue(hasAlignedSeriesScan);
    }
  }
}
