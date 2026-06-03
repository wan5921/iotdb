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

package org.apache.iotdb.udaf.median;

import org.apache.iotdb.udf.api.State;
import org.apache.iotdb.udf.api.customizer.analysis.AggregateFunctionAnalysis;
import org.apache.iotdb.udf.api.customizer.parameter.FunctionArguments;
import org.apache.iotdb.udf.api.exception.UDFArgumentNotValidException;
import org.apache.iotdb.udf.api.exception.UDFException;
import org.apache.iotdb.udf.api.relational.AggregateFunction;
import org.apache.iotdb.udf.api.relational.access.Record;
import org.apache.iotdb.udf.api.type.Type;
import org.apache.iotdb.udf.api.utils.ResultValue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MedianUDAF implements AggregateFunction {

  static class MedianState implements State {

    List<Double> values = new ArrayList<>();

    @Override
    public void reset() {
      values.clear();
    }

    @Override
    public byte[] serialize() {
      try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(bos)) {
        oos.writeObject(values);
        return bos.toByteArray();
      } catch (IOException e) {
        throw new UDFException("Failed to serialize MedianState", e);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void deserialize(byte[] bytes) {
      try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
          ObjectInputStream ois = new ObjectInputStream(bis)) {
        values = (List<Double>) ois.readObject();
      } catch (IOException | ClassNotFoundException e) {
        throw new UDFException("Failed to deserialize MedianState", e);
      }
    }
  }

  @Override
  public AggregateFunctionAnalysis analyze(FunctionArguments arguments)
      throws UDFArgumentNotValidException {
    if (arguments.getArgumentsSize() != 1) {
      throw new UDFArgumentNotValidException("Median only accepts one column as input");
    }
    if (arguments.getDataType(0) != Type.INT32
        && arguments.getDataType(0) != Type.INT64
        && arguments.getDataType(0) != Type.FLOAT
        && arguments.getDataType(0) != Type.DOUBLE) {
      throw new UDFArgumentNotValidException(
          "Median only accepts INT32, INT64, FLOAT, DOUBLE as input");
    }
    return new AggregateFunctionAnalysis.Builder().outputDataType(Type.DOUBLE).build();
  }

  @Override
  public State createState() {
    return new MedianState();
  }

  @Override
  public void addInput(State state, Record input) {
    if (!input.isNull(0)) {
      MedianState medianState = (MedianState) state;
      double value;
      switch (input.getDataType(0)) {
        case INT32:
          value = input.getInt(0);
          break;
        case INT64:
          value = input.getLong(0);
          break;
        case FLOAT:
          value = input.getFloat(0);
          break;
        case DOUBLE:
          value = input.getDouble(0);
          break;
        default:
          throw new UDFException("Median only accepts INT32, INT64, FLOAT, DOUBLE as input");
      }
      medianState.values.add(value);
    }
  }

  @Override
  public void combineState(State state, State rhs) {
    MedianState medianState = (MedianState) state;
    MedianState medianRhs = (MedianState) rhs;
    medianState.values.addAll(medianRhs.values);
  }

  @Override
  public void outputFinal(State state, ResultValue resultValue) {
    MedianState medianState = (MedianState) state;
    if (medianState.values.isEmpty()) {
      resultValue.setNull();
      return;
    }
    Collections.sort(medianState.values);
    int size = medianState.values.size();
    double median;
    if (size % 2 == 0) {
      median =
          (medianState.values.get(size / 2 - 1) + medianState.values.get(size / 2)) / 2.0;
    } else {
      median = medianState.values.get(size / 2);
    }
    resultValue.setDouble(median);
  }
}
