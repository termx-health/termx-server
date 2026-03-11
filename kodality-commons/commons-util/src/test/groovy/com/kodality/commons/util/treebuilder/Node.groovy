package com.kodality.commons.util.treebuilder

import com.kodality.commons.model.TreeNode

class Node implements TreeNode<Node, Long> {
  Long id
  Long parent
  List<Node> children

  @Override
  Long getId() {
    return this.id
  }

  @Override
  Long getParentKey() {
    return this.parent
  }

  @Override
  void setParentKey(Long parent) {
    this.parent = parent
  }

  @Override
  List<Node> getChildren() {
    return this.children
  }

  @Override
  void setChildren(List<Node> children) {
    this.children = children
  }
}
