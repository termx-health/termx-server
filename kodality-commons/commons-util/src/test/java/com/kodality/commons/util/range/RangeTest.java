package com.kodality.commons.util.range;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RangeTest {

  @Test
  public void contains() {
    assertFalse(new IntRange("(,)").contains(null));
    assertFalse(new IntRange("(,20)").contains(null));
    assertFalse(new IntRange("(10,20)").contains(null));

    assertTrue(new IntRange("(,)").contains(2));
    assertTrue(new IntRange("(,20)").contains(2));
    assertFalse(new IntRange("(,20)").contains(20));
    assertTrue(new IntRange("(,20]").contains(20));
    assertFalse(new IntRange("(,20]").contains(21));

    assertFalse(new IntRange("(10,20)").contains(2));
    assertTrue(new IntRange("(10,20)").contains(12));
    assertFalse(new IntRange("(10,20)").contains(10));
    assertTrue(new IntRange("[10,20]").contains(10));
    assertFalse(new IntRange("[10,20]").contains(9));
  }

  @Test
  public void containsRange() {
    assertTrue(new IntRange("(2,5)").containsRange(new IntRange("(3,4)")));
    assertTrue(new IntRange("(,5)").containsRange(new IntRange("(,4)")));
    assertTrue(new IntRange("(4,)").containsRange(new IntRange("(5,)")));
  }

  @Test
  public void intersects() {
    checkIntersects("(2,4)", "(3,5)", true);
    checkIntersects("(2,3)", "(4,5)", false);
    checkIntersects("(2,3]", "[3,4)", true);
    checkIntersects("(2,3)", "[3,5)", false);
    checkIntersects("(1,2)", "(,)", true);
    checkIntersects("(1,4)", "(2,)", true);
    checkIntersects("(,2)", "(1,4)", true);
    checkIntersects("(,3)", "(4,)", false);
    checkIntersects("(,3)", "(,2)", true);
  }
  
  @Test
  public void testEmpty() {
    assertFalse(new IntRange("(,)").isEmpty());
    assertFalse(new IntRange("(1,)").isEmpty());
    assertFalse(new IntRange("(,1)").isEmpty());
    assertFalse(new IntRange("[,]").isEmpty());
    assertFalse(new IntRange("[1,]").isEmpty());
    assertFalse(new IntRange("[,1]").isEmpty());
    assertFalse(new IntRange("[1,1]").isEmpty());
    assertTrue(new IntRange("[1,1)").isEmpty());
    assertTrue(new IntRange("(1,1]").isEmpty());
    assertTrue(new IntRange("(1,1)").isEmpty());
    assertFalse(new IntRange("(1,2)").isEmpty());
  }

  private void checkIntersects(String range1, String range2, boolean intersects) {
    String message;
    if (intersects) {
      message = String.format("Range '%s' and '%s' expected to intersect with each other, but they not", range1, range2);
    } else {
      message = String.format("Range '%s' and '%s' expected to be non-intersecting, but they are", range1, range2);
    }

    IntRange r1 = new IntRange(range1);
    IntRange r2 = new IntRange(range2);
    assertEquals(message, intersects, r1.intersects(r2));
    assertEquals(message, intersects, r2.intersects(r1));
  }

  @Test
  public void hasAnyIntersections() {
    assertFalse(Range.hasAnyIntersection(toRangeList("(1,2)", "(3,4)", "(6,)")));
    assertTrue(Range.hasAnyIntersection(toRangeList("(1,2)", "(3,4)", "(6,)", "(0,2)")));
  }

  private List<IntRange> toRangeList(String ...ranges)  {
    return Stream.of(ranges)
        .map(IntRange::new)
        .collect(Collectors.toList());
  }

}
