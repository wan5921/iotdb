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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PathPatternOptimizer {

  private static final String ONE_LEVEL_WILDCARD = "*";
  private static final String PATH_SEPARATOR = ".";

  private PathPatternOptimizer() {}

  public static List<String> mergePaths(List<String> paths) {
    if (paths == null || paths.isEmpty()) {
      return new ArrayList<>();
    }

    Set<String> mergedPaths = new LinkedHashSet<>();
    for (String path : paths) {
      if (path != null && !path.isEmpty()) {
        mergedPaths.add(path);
      }
    }

    boolean changed;
    do {
      changed = mergeSiblingPaths(mergedPaths);
      changed = removeCoveredPaths(mergedPaths) || changed;
    } while (changed);

    return new ArrayList<>(mergedPaths);
  }

  private static boolean mergeSiblingPaths(Set<String> paths) {
    Map<String, List<String>> pathsByParent = new LinkedHashMap<>();
    for (String path : paths) {
      if (path.endsWith(PATH_SEPARATOR + ONE_LEVEL_WILDCARD)) {
        continue;
      }
      String parent = getParent(path);
      if (parent != null) {
        pathsByParent.computeIfAbsent(parent, key -> new ArrayList<>()).add(path);
      }
    }

    boolean changed = false;
    for (Map.Entry<String, List<String>> entry : pathsByParent.entrySet()) {
      List<String> siblings = entry.getValue();
      if (siblings.size() > 1) {
        paths.removeAll(siblings);
        paths.add(entry.getKey() + PATH_SEPARATOR + ONE_LEVEL_WILDCARD);
        changed = true;
      }
    }
    return changed;
  }

  private static boolean removeCoveredPaths(Set<String> paths) {
    boolean changed = false;
    List<String> snapshot = new ArrayList<>(paths);
    Iterator<String> iterator = paths.iterator();
    while (iterator.hasNext()) {
      String currentPath = iterator.next();
      for (String candidate : snapshot) {
        if (!candidate.equals(currentPath) && covers(candidate, currentPath)) {
          iterator.remove();
          changed = true;
          break;
        }
      }
    }
    return changed;
  }

  private static String getParent(String path) {
    int lastSeparatorIndex = path.lastIndexOf(PATH_SEPARATOR);
    if (lastSeparatorIndex <= 0) {
      return null;
    }
    return path.substring(0, lastSeparatorIndex);
  }

  private static boolean covers(String pattern, String path) {
    String[] patternNodes = pattern.split("\\.");
    String[] pathNodes = path.split("\\.");
    if (patternNodes.length != pathNodes.length) {
      return false;
    }
    for (int i = 0; i < patternNodes.length; i++) {
      if (!ONE_LEVEL_WILDCARD.equals(patternNodes[i]) && !patternNodes[i].equals(pathNodes[i])) {
        return false;
      }
    }
    return true;
  }
}
