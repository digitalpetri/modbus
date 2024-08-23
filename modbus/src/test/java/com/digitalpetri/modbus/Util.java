package com.digitalpetri.modbus;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Util {

  static Stream<byte[]> partitions(byte[] source, int partitionSize) {
    int size = source.length;

    if (size == 0) {
      return Stream.empty();
    }

    int fullChunks = (size - 1) / partitionSize;

    return IntStream.range(0, fullChunks + 1).mapToObj(n -> {
      int fromIndex = n * partitionSize;
      int toIndex = n == fullChunks
          ? size
          : (n + 1) * partitionSize;

      return Arrays.copyOfRange(source, fromIndex, toIndex);
    });
  }

}
