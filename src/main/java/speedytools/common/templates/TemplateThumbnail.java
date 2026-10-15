package speedytools.common.templates;

/** Fixed 8x8x8 colour samples, independent of rendering and registry state IDs. Zero means empty. */
public final class TemplateThumbnail
{
  public static byte[] sample(int sx, int sy, int sz, int[] cells, byte[] colours)
  {
    int volume = TemplateGeometry.volume(sx, sy, sz, 262144);
    if (cells.length != volume) throw new IllegalArgumentException("Invalid thumbnail volume");
    byte[] result = new byte[512];
    int nx = Math.min(8, sx), ny = Math.min(8, sy), nz = Math.min(8, sz);
    // Last non-air block wins within each sample: upper surfaces remain visible.
    for (int i = 0; i < cells.length; ++i) {
      if (cells[i] < 0 || cells[i] >= colours.length) throw new IllegalArgumentException("Invalid thumbnail palette");
      byte colour = colours[cells[i]];
      if (colour != 0) result[(i % sx) * nx / sx + 8 * ((i / sx % sz) * nz / sz) + 64 * ((i / (sx * sz)) * ny / sy)] = colour;
    }
    return result;
  }
}
