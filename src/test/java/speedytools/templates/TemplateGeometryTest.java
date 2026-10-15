package speedytools.templates;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import speedytools.common.templates.TemplateGeometry;

import static org.junit.Assert.*;

public class TemplateGeometryTest
{
  @Test
  public void allRotationsAndMirrorsKeepOffCentreAnchorFixed()
  {
    for (int sx : new int[] {1, 6, 7, 254}) for (int sz : new int[] {1, 4, 9}) {
      NBTTagCompound info = new NBTTagCompound();
      info.setInteger("SizeX", sx); info.setInteger("SizeY", 4); info.setInteger("SizeZ", sz);
      int ax = sx - 1, ay = 2, az = sz / 2;
      info.setInteger("AnchorX", ax); info.setInteger("AnchorY", ay); info.setInteger("AnchorZ", az);
      BlockPos target = new BlockPos(-29999000, 70, 29999000);
      for (int rotation = 0; rotation < 4; ++rotation) for (boolean mirror : new boolean[] {false, true}) {
        int index = ax + sx * az + sx * sz * ay;
        assertEquals(target, TemplateGeometry.point(info, target, rotation, mirror, index));
        for (int x = 0; x < sx; ++x) for (int z = 0; z < sz; ++z) {
          int dx = (x - ax) * (mirror ? -1 : 1), dz = z - az;
          for (int r = 0; r < rotation; ++r) { int old = dx; dx = -dz; dz = old; }
          assertEquals(new BlockPos(target.getX() + dx, target.getY() - ay, target.getZ() + dz),
                  TemplateGeometry.point(info, target, rotation, mirror, x + sx * z));
        }
      }
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void reservesSpaceForUndoBorder() { TemplateGeometry.volume(255, 1, 1, 65536); }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsOverVolumeLimit() { TemplateGeometry.volume(100, 100, 100, 65536); }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsOverflowingDimensions() { TemplateGeometry.volume(Integer.MAX_VALUE, Integer.MAX_VALUE, 2, 65536); }
}
