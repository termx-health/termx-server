package com.kodality.commons.stream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

public final class Collectors {
  public Collectors() {}

  public static <T, K> Collector<T, ?, Map<K, T>> toMap(Function<? super T, ? extends K> keyMapper) {
    return toMap(keyMapper, x -> x);
  }

  // accepts null keys
  public static <T, K, U> Collector<T, ?, Map<K, U>> toMap(Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends U> valueMapper) {
    return java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), list -> {
      Map<K, U> result = new LinkedHashMap<>(list.size());
      list.forEach(v -> result.put(keyMapper.apply(v), valueMapper.apply(v)));
      return result;
    });
  }

  // accepts null keys
  public static <T, A> Collector<T, ?, Map<A, List<T>>> groupingBy(Function<? super T, ? extends A> classifier) {
    return java.util.stream.Collectors.toMap(
        classifier,
        el -> new ArrayList<>(List.of(el)),
        (List<T> a, List<T> b) -> {
          a.addAll(b);
          return a;
        });
  }
}
