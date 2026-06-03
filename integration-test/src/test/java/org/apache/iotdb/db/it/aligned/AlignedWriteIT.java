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
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;

import org.apache.tsfile.enums.TSDataType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(IoTDBTestRunner.class)
@Category({LocalStandaloneIT.class, ClusterIT.class})
public class AlignedWriteIT {

  private static final String STORAGE_GROUP = "root.sg1";
  private static final String DEVICE_ID = "root.sg1.d1";

  @Before
  public void setUp() throws Exception {
    EnvFactory.getEnv().initClusterEnvironment();
  }

  @After
  public void tearDown() throws Exception {
    EnvFactory.getEnv().cleanClusterEnvironment();
  }

  private void createAlignedTimeseries(ISession session)
      throws IoTDBConnectionException, StatementExecutionException {
    session.executeNonQueryStatement("CREATE DATABASE " + STORAGE_GROUP);
    session.executeNonQueryStatement(
        "CREATE ALIGNED TIMESERIES " + DEVICE_ID + "(s1 INT32, s2 INT32)");
  }

  @Test
  public void testInsertNullAlignedValuesAndQuery() {
    try (ISession session = EnvFactory.getEnv().getSessionConnection()) {
      createAlignedTimeseries(session);

      List<String> measurements = Arrays.asList("s1", "s2");
      List<TSDataType> types = Arrays.asList(TSDataType.INT32, TSDataType.INT32);

      session.insertAlignedRecord(DEVICE_ID, 1, measurements, types, Arrays.asList(10, 20));
      session.insertAlignedRecord(DEVICE_ID, 2, measurements, types, Arrays.asList(null, 21));
      session.insertAlignedRecord(DEVICE_ID, 3, measurements, types, Arrays.asList(12, null));
      session.insertAlignedRecord(DEVICE_ID, 4, measurements, types, Arrays.asList(null, null));
      session.insertAlignedRecord(DEVICE_ID, 5, measurements, types, Arrays.asList(14, 23));

      session.executeNonQueryStatement("flush");

      SessionDataSet dataSet =
          session.executeQueryStatement("select * from " + DEVICE_ID);
      int count = 0;
      while (dataSet.hasNext()) {
        dataSet.next();
        count++;
      }
      assertEquals(5, count);
      dataSet.closeOperationHandle();

      dataSet = session.executeQueryStatement("select s1 from " + DEVICE_ID);
      count = 0;
      while (dataSet.hasNext()) {
        dataSet.next();
        count++;
      }
      assertEquals(5, count);
      dataSet.closeOperationHandle();
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void testSelectStarAndSelectS1Performance() {
    try (ISession session = EnvFactory.getEnv().getSessionConnection()) {
      createAlignedTimeseries(session);

      List<String> measurements = Arrays.asList("s1", "s2");
      List<TSDataType> types = Arrays.asList(TSDataType.INT32, TSDataType.INT32);
      for (int i = 1; i <= 100; i++) {
        session.insertAlignedRecord(
            DEVICE_ID, i, measurements, types, Arrays.asList(i, i * 2));
      }
      session.executeNonQueryStatement("flush");
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }

    try (Connection connection = EnvFactory.getEnv().getConnection();
        Statement statement = connection.createStatement()) {

      try (ResultSet resultSet =
          statement.executeQuery("explain select * from " + DEVICE_ID)) {
        boolean found = false;
        while (resultSet.next()) {
          if (resultSet.getString(1).contains("AlignedSeriesScan")) {
            found = true;
            break;
          }
        }
        assertTrue("SELECT * should use AlignedSeriesScan", found);
      }

      try (ResultSet resultSet =
          statement.executeQuery("explain select s1 from " + DEVICE_ID)) {
        boolean found = false;
        while (resultSet.next()) {
          if (resultSet.getString(1).contains("AlignedSeriesScan")) {
            found = true;
            break;
          }
        }
        assertTrue("SELECT s1 should use AlignedSeriesScan", found);
      }
    } catch (SQLException e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }
}