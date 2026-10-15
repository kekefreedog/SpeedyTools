package speedytools.common.templates;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import speedytools.common.utilities.QuadOrientation;

/** Shared integer geometry: the saved anchor always lands on the target block. */
public final class TemplateGeometry
{
  private TemplateGeometry() {}

  public static int volume(int x, int y, int z, int maximum)
  {
    long volume = (long)x * y * z;
    // WorldHistory needs a one-block undo border on both sides (its maximum is 256).
    if (x < 1 || y < 1 || z < 1 || x > 254 || y > 254 || z > 254 || volume > maximum) {
      throw new IllegalArgumentException("Selection exceeds the template size limit (" + maximum + " blocks).");
    }
    return (int)volume;
  }

  public static QuadOrientation orientation(NBTTagCompound info, BlockPos target, int rotation, boolean mirror)
  {
    int sx = info.getInteger("SizeX"), sz = info.getInteger("SizeZ");
    int ax = info.getInteger("AnchorX"), az = info.getInteger("AnchorZ");
    QuadOrientation relative = new QuadOrientation(0, 0, sx, sz, mirror, (byte)(rotation & 3));
    return new QuadOrientation(target.getX() - relative.calcWXfromXZ(ax, az),
            target.getZ() - relative.calcWZfromXZ(ax, az), sx, sz, mirror, (byte)(rotation & 3));
  }

  public static BlockPos point(NBTTagCompound info, BlockPos target, int rotation, boolean mirror, int index)
  {
    int sx = info.getInteger("SizeX"), sz = info.getInteger("SizeZ");
    QuadOrientation q = orientation(info, target, rotation, mirror);
    return new BlockPos(q.calcWXfromXZ(index % sx, index / sx % sz),
            target.getY() - info.getInteger("AnchorY") + index / (sx * sz),
            q.calcWZfromXZ(index % sx, index / sx % sz));
  }

  public static void putPosition(NBTTagCompound tag, String prefix, BlockPos p)
  {
    tag.setInteger(prefix + "X", p.getX());
    tag.setInteger(prefix + "Y", p.getY());
    tag.setInteger(prefix + "Z", p.getZ());
  }

  public static BlockPos position(NBTTagCompound tag, String prefix)
  {
    return new BlockPos(tag.getInteger(prefix + "X"), tag.getInteger(prefix + "Y"), tag.getInteger(prefix + "Z"));
  }

  public static String dimensions(NBTTagCompound tag)
  {
    return tag.getInteger("SizeX") + " x " + tag.getInteger("SizeY") + " x " + tag.getInteger("SizeZ");
  }
}
