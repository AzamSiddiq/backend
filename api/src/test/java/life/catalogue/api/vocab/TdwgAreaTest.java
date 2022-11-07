package life.catalogue.api.vocab;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class TdwgAreaTest {

  @Test
  public void build() {
    Set<String> ids = new HashSet<>();
    for (var a : TdwgArea.build()) {
      ids.add(a.getId());
      assertNotNull(a.getId());
      assertNotNull(a.getName());
      if (a.getLevel() == 1) {
        assertNull(a.getParent());
      } else {
        assertNotNull(a.getParent());
        var p = TdwgArea.of(a.getParent());
        assertEquals(a.getLevel()-1, p.getLevel());
        assertEquals(a.getParent(), p.getId());
      }
    }
    assertEquals(1039, ids.size());
    assertEquals(ids.size(), TdwgArea.AREAS.size());
  }

  @Test
  public void testLink() throws Exception {
    for (var tdwg : TdwgArea.AREAS) {
      assertNotNull(tdwg.getLink());
    }
  }
}