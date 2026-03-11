package com.kodality.commons.model;

import org.junit.Assert;
import org.junit.Test;

public class HumanNameTest {

  @Test
  public void testHumanName() {
    HumanName hn = new HumanName();
    Assert.assertNull(hn.getText());
    Assert.assertNull(hn.getGiven());

    hn = new HumanName("hz");
    Assert.assertEquals("hz", hn.getText());
    Assert.assertNull(hn.getGiven());
    Assert.assertNull(hn.getFamily());

    hn = new HumanName("hz nezivestnovich");
    Assert.assertEquals("hz nezivestnovich", hn.getText());
    Assert.assertEquals("hz", hn.getGiven());
    Assert.assertEquals("nezivestnovich", hn.getFamily());

    hn = new HumanName("nezivestnovich, hz");
    Assert.assertEquals("nezivestnovich, hz", hn.getText());
    Assert.assertEquals("hz", hn.getGiven());
    Assert.assertEquals("nezivestnovich", hn.getFamily());

    hn = new HumanName("hz", "nezivestnovich");
    Assert.assertEquals("hz nezivestnovich", hn.getText());
    Assert.assertEquals("hz", hn.getGiven());
    Assert.assertEquals("nezivestnovich", hn.getFamily());
  }

}
