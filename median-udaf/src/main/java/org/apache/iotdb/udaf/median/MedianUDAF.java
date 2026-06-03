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

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.PriorityQueue;

public class MedianUDAF implements AggregateFunction {

  static class MedianState implements State {

    private PriorityQueue<Double> maxHeap;
    private PriorityQueue<Double> minHeap;

    MedianState() {
      maxHeap = new PriorityQueue<>(Collections.reverseOrder());
      minHeap = new PriorityQueue<>();
    }

    @Override
    public void reset() {
      maxHeap.clear();
      minHeap.clear();
    }

    @Override
    public byte[] serialize() {
      int maxSize = maxHeap.size();
      int minSize = minHeap.size();
      int totalDoubles = maxSize + minSize;
      ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 2 + Double.BYTES * totalDoubles);
      buffer.putInt(maxSize);
      buffer.putInt(minSize);
      for (Double v : maxHeap) {
        buffer.putDouble(v);
      }
      for (Double v : minHeap) {
        buffer.putDouble(v);
      }
      return buffer.array();
    }

    @Override
    public void deserialize(byte[] bytes) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      int maxSize = buffer.getInt();
      int minSize = buffer.getInt();
      maxHeap = new PriorityQueue<>(Collections.reverseOrder());
      minHeap = new PriorityQueue<>();
      for (int i = 0; i < maxSize; i++) {
        maxHeap.offer(buffer.getDouble());
      }
      for (int i = 0; i < minSize; i++) {
        minHeap.offer(buffer.getDouble());
      }
    }

    void addValue(double value) {
      if (maxHeap.isEmpty() || value <= maxHeap.peek()) {
        maxHeap.offer(value);
      } else {
        minHeap.offer(value);
      }
      rebalance();
    }

    void merge(MedianState other) {
      for (Double v : other.maxHeap) {
        addValue(v);
      }
      for (Double v : other.minHeap) {
        addValue(v);
      }
    }

    private void rebalance() {
      if (maxHeap.size() > minHeap.size() + 1) {
        minHeap.offer(maxHeap.poll());
      } else if (minHeap.size() > maxHeap.size()) {
        maxHeap.offer(minHeap.poll());
      }
    }

    double getMedian() {
      if (maxHeap.isEmpty()) {
        return 0.0;
      }
      if (maxHeap.size() == minHeap.size()) {
        return (maxHeap.peek() + minHeap.peek()) / 2.0;
      }
      return maxHeap.peek();
    }

    boolean isEmpty() {
      return maxHeap.isEmpty();
    }
  }

  @Override
  public void beforeStart(FunctionArguments arguments) throws UDFException {
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
      medianState.addValue(value);
    }
  }

  @Override
  public void combineState(State state, State rhs) {
    MedianState medianState = (MedianState) state;
    MedianState medianRhs = (MedianState) rhs;
    medianState.merge(medianRhs);
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
}