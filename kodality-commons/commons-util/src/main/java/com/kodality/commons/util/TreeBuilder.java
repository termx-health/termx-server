package com.kodality.commons.util;

import com.kodality.commons.model.TreeNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TreeBuilder {

  public static <T extends TreeNode<T, K>, K> List<T> buildTree(List<T> flat) {
    return buildTree(flat.stream());
  }

  public static <T extends TreeNode<T, K>, K> List<T> buildTree(Stream<T> flat) {
    Map<K, List<T>> map = group(flat);
    List<T> root = map.getOrDefault(null, new ArrayList<>());
    root.forEach(c -> expand(c, map));
    return root;
  }

  private static <T extends TreeNode<T, K>, K> void expand(T treeNode, Map<K, List<T>> map) {
    List<T> children = map.get(treeNode.getId());
    if (children == null) {
      return;
    }
    treeNode.setChildren(children);
    children.forEach(c -> expand(c, map));
  }

  private static <T extends TreeNode<T, K>, K> Map<K, List<T>> group(Stream<T> flat) {
    Map<K, List<T>> r = flat.collect(groupingBy(TreeNode::getParentKey));
    r.values().forEach(rr -> rr.forEach(rrr -> rrr.setParentKey(null)));
    return r;
  }

  /**
   * because #Collectors.groupingBy does not eat null keys
   */
  private static <T extends TreeNode<T, K>, K> Collector<T, ?, Map<K, List<T>>> groupingBy(Function<T, K> keyFn) {
    return Collectors.toMap(keyFn, x -> {
      List<T> list = new ArrayList<>();
      list.add(x);
      return list;
    }, (left, right) -> {
      left.addAll(right);
      return left;
    }, HashMap::new);
  }

}


