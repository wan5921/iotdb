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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.PriorityQueue;

public class MedianUDAF implements AggregateFunction {

  private Type inputType;

  static class MedianState implements State {

    private PriorityQueue<Double> lowerHalf = new PriorityQueue<>(Collections.reverseOrder());
    private PriorityQueue<Double> upperHalf = new PriorityQueue<>();

    void addValue(double value) {
      if (lowerHalf.isEmpty() || value <= lowerHalf.peek()) {
        lowerHalf.offer(value);
      } else {
        upperHalf.offer(value);
      }
      rebalance();
    }

    void merge(MedianState other) {
      for (double value : other.lowerHalf) {
        addValue(value);
      }
      for (double value : other.upperHalf) {
        addValue(value);
      }
    }

    double getMedian() {
      if (lowerHalf.size() == upperHalf.size()) {
        return (lowerHalf.peek() + upperHalf.peek()) / 2.0;
      }
      return lowerHalf.peek();
    }

    boolean isEmpty() {
      return lowerHalf.isEmpty() && upperHalf.isEmpty();
    }

    private void rebalance() {
      if (lowerHalf.size() < upperHalf.size()) {
        lowerHalf.offer(upperHalf.poll());
      } else if (lowerHalf.size() - upperHalf.size() > 1) {
        upperHalf.offer(lowerHalf.poll());
      }
    }

    @Override
    public void reset() {
      lowerHalf.clear();
      upperHalf.clear();
    }

    @Override
    public byte[] serialize() {
      try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
          DataOutputStream outputStream = new DataOutputStream(byteArrayOutputStream)) {
        outputStream.writeInt(lowerHalf.size());
        for (double value : lowerHalf) {
          outputStream.writeDouble(value);
        }
        outputStream.writeInt(upperHalf.size());
        for (double value : upperHalf) {
          outputStream.writeDouble(value);
        }
        outputStream.flush();
        return byteArrayOutputStream.toByteArray();
      } catch (IOException e) {
        throw new UDFException("Failed to serialize MedianState", e);
      }
    }

    @Override
    public void deserialize(byte[] bytes) {
      reset();
      try (DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(bytes))) {
        int lowerSize = inputStream.readInt();
        for (int i = 0; i < lowerSize; i++) {
          lowerHalf.offer(inputStream.readDouble());
        }
        int upperSize = inputStream.readInt();
        for (int i = 0; i < upperSize; i++) {
          upperHalf.offer(inputStream.readDouble());
        }
      } catch (IOException e) {
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
    validateInputType(arguments.getDataType(0));
    return new AggregateFunctionAnalysis.Builder().outputDataType(Type.DOUBLE).build();
  }

  @Override
  public void beforeStart(FunctionArguments arguments) throws UDFException {
    inputType = arguments.getDataType(0);
  }

  @Override
  public State createState() {
    return new MedianState();
  }

  @Override
  public void addInput(State state, Record input) {
    if (input.isNull(0)) {
      return;
    }
    MedianState medianState = (MedianState) state;
    switch (inputType) {
      case INT32:
        medianState.addValue(input.getInt(0));
        break;
      case INT64:
        medianState.addValue(input.getLong(0));
        break;
      case FLOAT:
        medianState.addValue(input.getFloat(0));
        break;
      case DOUBLE:
        medianState.addValue(input.getDouble(0));
        break;
      default:
        throw new UDFException("Median only accepts INT32, INT64, FLOAT, DOUBLE as input");
    }
  }

  @Override
  public void combineState(State state, State rhs) {
    MedianState medianState = (MedianState) state;
    MedianState rhsState = (MedianState) rhs;
    medianState.merge(rhsState);
  }

  @Override
  public void outputFinal(State state, ResultValue resultValue) {
    MedianState medianState = (MedianState) state;
    if (medianState.isEmpty()) {
      resultValue.setNull();
      return;
    }
    resultValue.setDouble(medianState.getMedian());
  }

  private void validateInputType(Type type) throws UDFArgumentNotValidException {
    if (type != Type.INT32 && type != Type.INT64 && type != Type.FLOAT && type != Type.DOUBLE) {
      throw new UDFArgumentNotValidException(
          "Median only accepts INT32, INT64, FLOAT, DOUBLE as input");
    }
  }
}
