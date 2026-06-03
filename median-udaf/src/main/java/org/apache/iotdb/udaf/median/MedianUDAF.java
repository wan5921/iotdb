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

    PriorityQueue<Double> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
    PriorityQueue<Double> minHeap = new PriorityQueue<>();

    @Override
    public void reset() {
      maxHeap.clear();
      minHeap.clear();
    }

    public void add(double num) {
      if (maxHeap.isEmpty() || num <= maxHeap.peek()) {
        maxHeap.offer(num);
      } else {
        minHeap.offer(num);
      }
      balance();
    }

    private void balance() {
      if (maxHeap.size() > minHeap.size() + 1) {
        minHeap.offer(maxHeap.poll());
      } else if (minHeap.size() > maxHeap.size()) {
        maxHeap.offer(minHeap.poll());
      }
    }

    @Override
    public byte[] serialize() {
      try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
          DataOutputStream dos = new DataOutputStream(bos)) {
        dos.writeInt(maxHeap.size());
        for (double val : maxHeap) {
          dos.writeDouble(val);
        }
        dos.writeInt(minHeap.size());
        for (double val : minHeap) {
          dos.writeDouble(val);
        }
        dos.flush();
        return bos.toByteArray();
      } catch (IOException e) {
        throw new UDFException("Failed to serialize MedianState", e);
      }
    }

    @Override
    public void deserialize(byte[] bytes) {
      reset();
      try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
          DataInputStream dis = new DataInputStream(bis)) {
        int maxHeapSize = dis.readInt();
        for (int i = 0; i < maxHeapSize; i++) {
          maxHeap.offer(dis.readDouble());
        }
        int minHeapSize = dis.readInt();
        for (int i = 0; i < minHeapSize; i++) {
          minHeap.offer(dis.readDouble());
        }
      } catch (IOException e) {
        throw new UDFException("Failed to deserialize MedianState", e);
      }
    }

    public boolean isEmpty() {
      return maxHeap.isEmpty() && minHeap.isEmpty();
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
  public void beforeStart(FunctionArguments arguments) throws UDFException {
    this.inputType = arguments.getDataType(0);
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
      switch (inputType) {
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
      medianState.add(value);
    }
  }

  @Override
  public void combineState(State state, State rhs) {
    MedianState medianState = (MedianState) state;
    MedianState medianRhs = (MedianState) rhs;
    for (double val : medianRhs.maxHeap) {
      medianState.add(val);
    }
    for (double val : medianRhs.minHeap) {
      medianState.add(val);
    }
  }

  @Override
  public void outputFinal(State state, ResultValue resultValue) {
    MedianState medianState = (MedianState) state;
    if (medianState.isEmpty()) {
      resultValue.setNull();
      return;
    }
    
    double median;
    if (medianState.maxHeap.size() > medianState.minHeap.size()) {
      median = medianState.maxHeap.peek();
    } else {
      median = (medianState.maxHeap.peek() + medianState.minHeap.peek()) / 2.0;
    }
    resultValue.setDouble(median);
  }
}
