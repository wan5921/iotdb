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

package org.apache.iotdb.db.queryengine.plan.optimization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PathPatternOptimizer {

  private PathPatternOptimizer() {}

  public static List<String> mergePaths(List<String> paths) {
    Map<String, List<String>> prefixToSuffixes = new LinkedHashMap<>();
    for (String path : paths) {
      int lastDot = path.lastIndexOf('.');
      if (lastDot < 0) {
        continue;
      }
      String prefix = path.substring(0, lastDot);
      String suffix = path.substring(lastDot + 1);
      prefixToSuffixes.computeIfAbsent(prefix, k -> new ArrayList<>()).add(suffix);
    }

    List<String> merged = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : prefixToSuffixes.entrySet()) {
      if (entry.getValue().size() > 1) {
        merged.add(entry.getKey() + ".*");
      } else {
        merged.add(entry.getKey() + "." + entry.getValue().get(0));
      }
    }
    return merged;
  }
}