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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathPatternOptimizer {

  public static List<String> mergePaths(List<String> paths) {
    if (paths == null || paths.isEmpty()) {
      return paths;
    }

    Map<String, List<String>> prefixMap = new HashMap<>();
    for (String path : paths) {
      int lastDotIndex = path.lastIndexOf('.');
      if (lastDotIndex != -1) {
        String prefix = path.substring(0, lastDotIndex);
        prefixMap.computeIfAbsent(prefix, k -> new ArrayList<>()).add(path);
      } else {
        prefixMap.computeIfAbsent(path, k -> new ArrayList<>()).add(path);
      }
    }

    List<String> mergedPaths = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : prefixMap.entrySet()) {
      if (entry.getValue().size() > 1 && entry.getKey().contains(".")) {
        mergedPaths.add(entry.getKey() + ".*");
      } else {
        mergedPaths.addAll(entry.getValue());
      }
    }
    return mergedPaths;
  }
}
