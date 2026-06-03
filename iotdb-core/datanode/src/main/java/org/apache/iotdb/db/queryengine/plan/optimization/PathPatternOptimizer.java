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

  private PathPatternOptimizer() {
  }

  public static List<String> mergePaths(List<String> paths) {
    if (paths == null || paths.isEmpty()) {
      return new ArrayList<>();
    }

    Map<String, List<String>> prefixToSuffixes = new HashMap<>();

    for (String path : paths) {
      String[] parts = path.split("\\.");
      if (parts.length <= 1) {
        prefixToSuffixes.computeIfAbsent(path, k -> new ArrayList<>()).add("");
        continue;
      }

      String prefix = parts[0];
      for (int i = 1; i < parts.length - 1; i++) {
        prefix = prefix + "." + parts[i];
      }

      String suffix = parts[parts.length - 1];
      prefixToSuffixes.computeIfAbsent(prefix, k -> new ArrayList<>()).add(suffix);
    }

    List<String> mergedPaths = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : prefixToSuffixes.entrySet()) {
      String prefix = entry.getKey();
      List<String> suffixes = entry.getValue();

      if (suffixes.contains("*")) {
        mergedPaths.add(prefix + ".*");
      } else if (suffixes.size() > 1) {
        mergedPaths.add(prefix + ".*");
      } else {
        for (String suffix : suffixes) {
          if (suffix.isEmpty()) {
            mergedPaths.add(prefix);
          } else {
            mergedPaths.add(prefix + "." + suffix);
          }
        }
      }
    }

    return mergedPaths;
  }
}
