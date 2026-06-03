/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

  private PathPatternOptimizer() {}

  public static List<String> mergePaths(List<String> paths) {
    if (paths == null || paths.isEmpty()) {
      return paths;
    }

    Map<String, List<String>> prefixMap = new HashMap<>();
    for (String path : paths) {
      String[] parts = path.split("\\.");
      if (parts.length <= 1) {
        prefixMap.computeIfAbsent(path, k -> new ArrayList<>());
      } else {
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
          if (i > 0) {
            prefix.append(".");
          }
          prefix.append(parts[i]);
        }
        prefixMap.computeIfAbsent(prefix.toString(), k -> new ArrayList<>()).add(path);
      }
    }

    List<String> result = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : prefixMap.entrySet()) {
      List<String> group = entry.getValue();
      if (group.size() > 1) {
        result.add(entry.getKey() + ".*");
      } else {
        result.addAll(group);
      }
    }

    return result;
  }
}
