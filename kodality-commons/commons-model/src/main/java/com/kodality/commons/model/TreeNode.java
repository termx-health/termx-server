package com.kodality.commons.model;

import java.util.List;

public interface TreeNode<T extends TreeNode, K> {
  K getId();

  K getParentKey();

  void setParentKey(K parent);

  List<T> getChildren();

  void setChildren(List<T> children);
}
