# Median UDAF for IoTDB 2.7.1

This module provides a median aggregation function for Apache IoTDB 2.7.1.

## Building

To build the jar file:

```bash
mvn clean package -DskipTests
```

To build the jar with all dependencies:

```bash
mvn clean package -DskipTests -Pget-jar-with-dependencies
```

## Usage

1. Deploy the jar file to IoTDB's `udf` directory.
2. Restart IoTDB server.
3. Register the function in IoTDB:

```sql
CREATE FUNCTION median AS 'org.apache.iotdb.udaf.median.MedianUDAF';
```

4. Use the function in queries:

```sql
-- Create database
CREATE DATABASE test;

USE test;

-- Create table
CREATE TABLE t1(device_id STRING TAG, s1 INT32 FIELD, s2 DOUBLE FIELD);

-- Insert data
INSERT INTO t1(time, device_id, s1, s2) VALUES 
(1, 'd1', 1, 1.5), 
(2, 'd1', 2, 2.5), 
(3, 'd1', 3, 3.5), 
(4, 'd1', 4, 4.5), 
(5, 'd1', 5, 5.5),
(6, 'd2', 10, 10.5),
(7, 'd2', 20, 20.5),
(8, 'd2', 30, 30.5);

-- Query median
SELECT device_id, median(s1) as median_s1, median(s2) as median_s2 
FROM t1 
GROUP BY device_id;

-- Query overall median
SELECT median(s1) as overall_median_s1, median(s2) as overall_median_s2 
FROM t1;
```

## Function Details

- Input types: INT32, INT64, FLOAT, DOUBLE
- Output type: DOUBLE
- Description: Computes the median value of the input column. If the number of values is even, returns the average of the two middle values.
