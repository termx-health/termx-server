package com.kodality.termserver.commons.db.bean


import spock.lang.Specification

import java.beans.Introspector
import java.lang.ref.SoftReference
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException

class BeanProcessorTest extends Specification {
  def "getWriteMethod() should find setter returning 'this'"() {
    def propDesc = Introspector.getBeanInfo(TestBean.class).propertyDescriptors.find({ it.name == 'foobar' })
    def bp = new BeanProcessor(TestBean.class)
    def method = bp.getWriteMethod(new TestBean(), propDesc, "test")
    expect:
    method != null
  }

  def "getWriteMethod() should find setter returning 'this' in parent class"() {
    def propDesc = Introspector.getBeanInfo(TestBean.class).propertyDescriptors.find({ it.name == 'parentFoobar' })
    def method = new BeanProcessor(TestBean.class).getWriteMethod(new TestBean(), propDesc, 10L)
    expect:
    method != null
  }

  def "getWriteMethod() should find setter by implementation"() {
    def method = new BeanProcessor(TestBean.class).getWriteMethod(new TestBean(), 'bazogaz', new ArrayList())
    expect:
    method != null
  }

  def "getGenerousColumnName() should return overridden or default generous name"(String columnName, String generousName) {
    setup:
    def bp = new BeanProcessor(TestBean.class).overrideColumnMapping("overridden_column", "fooBar")
    expect:
    bp.getGenerousColumnName(columnName) == generousName
    where:
    columnName          | generousName
    "some_name"         | "somename"
    "overridden_column" | "fooBar"
  }

  def "getWriteMethod() should look up method reference in cache"() {
    setup:
    def propDesc = Introspector.getBeanInfo(TestBean.class).propertyDescriptors.find({ it.name == 'foobar' })
    def bp = new BeanProcessor(TestBean.class)
    def cachedMethod = TestBean.class.getDeclaredMethod("getFoobar")
    bp.setterMethodCache.put(TestBean.class.name + "#setFoobar", new SoftReference<?>(cachedMethod))
    expect:
    bp.getWriteMethod(new TestBean(), propDesc, "test") == cachedMethod
  }

  def "getWriteMethod() should put method reference in to cache"() {
    setup:
    def propDesc = Introspector.getBeanInfo(TestBean.class).propertyDescriptors.find({ it.name == 'foobar' })
    def bp = new BeanProcessor(TestBean.class)
    when:
    def method = bp.getWriteMethod(new TestBean(), propDesc, "test")
    then:
    method != null
    bp.setterMethodCache.containsKey(TestBean.class.name + "#setFoobar")
  }

  def "getWriteMethod() should not cache null method reference"() {
    setup:
    def propDesc = Introspector.getBeanInfo(TestBean.class).propertyDescriptors.find({ it.name == 'barbaz' })
    def bp = new BeanProcessor(TestBean.class)
    when:
    def method = bp.getWriteMethod(new TestBean(), propDesc, "test")
    then:
    method == null
    !bp.setterMethodCache.containsKey(TestBean.class.name + "#setBarbaz")
  }

  def "row processor"() {
    setup:
    def bp = new BeanProcessor(TestBean.class)
    bp.addRowProcessor("node", new RowProcessor() {
      @Override
      Object process(ResultSet rs) throws SQLException {
        def node = new TestBean.TestNode()
        node.setA(rs.getString("node"))
        node.setB(rs.getString("bar"))
        return node
      }
    })
    def resultset = makeResultSet(["node", "bar"], ["fooVal", "barVal"])
    when:
    def bean = bp.toBean(resultset, TestBean.class);
    then:
    bean.getNode().getA() == "fooVal"
    bean.getNode().getB() == "barVal"
  }

  def "row processor with null"() {
    setup:
    def bp = new BeanProcessor(TestBean.class)
    bp.addRowProcessor("node", new RowProcessor() {
      @Override
      Object process(ResultSet rs) throws SQLException {
        return null;
      }
    })
    def resultset = makeResultSet(["node", "bar"], ["fooVal", "barVal"])
    when:
    def bean = bp.toBean(resultset, TestBean.class);
    then:
    bean.getNode() == null
  }

  def makeResultSet(List<String> aColumns, List... rows) {
    ResultSet result = Mock()
    int currentIndex = -1
    result.next() >> { ++currentIndex < rows.length }
    result./get(String|Short|Date|Int|Timestamp|Object)/(_) >> { def argument ->
      rows[currentIndex][aColumns.indexOf(argument[0])]
    }
    result.getMetaData() >> {
      def metadata = Mock(ResultSetMetaData)
      metadata.getColumnName(_) >> { int idx -> aColumns.get(idx) }
      metadata
    }

    return result
  }
}
