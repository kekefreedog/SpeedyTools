package speedytools.templates;

import org.junit.Test;
import speedytools.common.templates.TemplateThumbnail;
import static org.junit.Assert.*;

public class TemplateThumbnailTest
{
  @Test
  public void smallStructuresKeepTheirCoordinatesAndAirStaysEmpty()
  {
    byte[] thumbnail = TemplateThumbnail.sample(2, 1, 2, new int[] {1, 0, 0, 2}, new byte[] {0, 7, 8});
    assertEquals(512, thumbnail.length); assertEquals(7, thumbnail[0]); assertEquals(8, thumbnail[9]);
    int occupied = 0; for (byte value : thumbnail) if (value != 0) ++occupied;
    assertEquals(2, occupied);
  }

  @Test
  public void coarseCellsKeepUpperSurfaceColourAndIgnoreAir()
  {
    int[] blocks = new int[16]; blocks[0] = 1; blocks[1] = 2; blocks[14] = 1;
    byte[] thumbnail = TemplateThumbnail.sample(1, 16, 1, blocks, new byte[] {0, 10, 20});
    assertEquals(20, thumbnail[0]); assertEquals(10, thumbnail[7 * 64]);
    assertEquals(0, thumbnail[64]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidPaletteIndexIsRejected()
  {
    TemplateThumbnail.sample(1, 1, 1, new int[] {2}, new byte[] {0});
  }
}
