package life.catalogue.api.vocab;

import org.junit.Test;

import static org.junit.Assert.*;

public class SpeciesInteractionTypeTest {

  @Test
  public void inverse() {
    int symetric = 0;
    for (SpeciesInteractionType t : SpeciesInteractionType.values()) {
      System.out.println(t);
      assertTrue(SpeciesInteractionType.INVERSE.containsKey(t));
      if (t.isSymmetric()) {
        assertEquals(t, t.getInverse());
        symetric++;
      } else {
        assertNotEquals(t, t.getInverse());
        assertEquals(t, t.getInverse().getInverse());
      }
    }
    assertEquals(7, symetric);
  }


  @Test
  public void gfdgfd() {
    for (SpeciesInteractionType t : SpeciesInteractionType.values()) {
      //   INTERACTS_WITH("interacts", SpeciesInteractionType.INTERACTS_WITH),
      System.out.println(String.format("%s(\"%s\", SpeciesInteractionType.%s),", t.name(), t.name().toLowerCase(), t.name()));
    }
  }

}