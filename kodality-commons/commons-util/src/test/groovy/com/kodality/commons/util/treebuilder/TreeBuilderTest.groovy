package com.kodality.commons.util.treebuilder

import com.kodality.commons.util.TreeBuilder
import spock.lang.Specification

class TreeBuilderTest extends Specification {

  def firstLevelItem = new Node()
  def secondLevelItem = new Node()
  def thirdLevelItem = new Node()

  def "return locations tree"() {
    setup:
    firstLevelItem.setId(1L)
    secondLevelItem.setId(2L)
    secondLevelItem.setParent(1L)
    thirdLevelItem.setId(3L)
    thirdLevelItem.setParent(2L)

    def items = [firstLevelItem, secondLevelItem, thirdLevelItem]
    def tree = TreeBuilder.buildTree(items)

    expect:
    tree[0].children[0].id == secondLevelItem.id
    tree[0].children[0].children[0].id == thirdLevelItem.id
    tree[0].children[0].children[0].children == null
  }


  def "return empty list if first level parent has missed"() {
    setup:
    firstLevelItem.setId(1L)
    secondLevelItem.setId(2L)
    secondLevelItem.setParent(1L)
    thirdLevelItem.setId(3L)
    thirdLevelItem.setParent(2L)

    def items = [secondLevelItem, thirdLevelItem ]
    def tree = TreeBuilder.buildTree(items)

    expect:
    tree.size() == 0
  }

  def "return list with only first level accounts if middle level account has missed"() {
    def anotherFirstLevelItem = new Node()
    setup:
    firstLevelItem.setId(1L)
    secondLevelItem.setId(2L)
    secondLevelItem.setParent(1L)
    thirdLevelItem.setId(3L)
    thirdLevelItem.setParent(2L)
    anotherFirstLevelItem.setId(5L)

    def items = [firstLevelItem, thirdLevelItem, anotherFirstLevelItem]
    def tree = TreeBuilder.buildTree(items)

    expect:
    tree.size() == 2
    tree[0].getChildren() == null
  }

}
