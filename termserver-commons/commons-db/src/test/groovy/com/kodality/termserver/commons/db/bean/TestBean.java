package com.kodality.termserver.commons.db.bean;

import java.util.List;

public class TestBean extends ParentTestBean {
  private String foobar;
  private String barbaz;
  private List<String> bazogaz;

  private TestNode node;

  public String getFoobar() {
    return foobar;
  }

  public List<String> getBazogaz() {
    return bazogaz;
  }

  public void setBazogaz(List<String> bazogaz) {
    this.bazogaz = bazogaz;
  }

  public TestBean setFoobar(String foobar) {
    this.foobar = foobar;
    return this;
  }

  public String getBarbaz() {
    return barbaz;
  }

  public TestNode getNode() {
    return node;
  }

  public void setNode(TestNode node) {
    this.node = node;
  }

  public static class TestNode {
    private String a;
    private String b;

    public String getA() {
      return a;
    }

    public void setA(String a) {
      this.a = a;
    }

    public String getB() {
      return b;
    }

    public void setB(String b) {
      this.b = b;
    }
  }
}
