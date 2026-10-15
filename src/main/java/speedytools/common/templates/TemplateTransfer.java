package speedytools.common.templates;

import net.minecraft.nbt.*;
import java.io.*;
import java.util.zip.GZIPInputStream;

/** Bounded, ordered transfers; the ordinary packet envelope remains small. */
public final class TemplateTransfer
{
  public static final int MAX_BYTES = 8 * 1024 * 1024, CHUNK_BYTES = 16384;
  private final byte[] bytes;
  private int offset;

  public TemplateTransfer(int size)
  {
    if (size < 1 || size > MAX_BYTES) throw new IllegalArgumentException("Template transfer is too large.");
    bytes = new byte[size];
  }

  public void append(int position, byte[] chunk)
  {
    if (position != offset || chunk.length < 1 || chunk.length > CHUNK_BYTES || chunk.length > bytes.length - offset) {
      throw new IllegalArgumentException("Invalid template transfer chunk.");
    }
    System.arraycopy(chunk, 0, bytes, offset, chunk.length); offset += chunk.length;
  }

  public boolean complete() { return offset == bytes.length; }

  public NBTTagCompound read() throws IOException
  {
    if (!complete()) throw new IOException("Incomplete template transfer.");
    try (DataInputStream input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
      return CompressedStreamTools.func_152456_a(input, new NBTSizeTracker(32 * 1024 * 1024));
    }
  }

  /** Only portable schema fields cross a library boundary; no server IDs or permissions are trusted. */
  public static NBTTagCompound portable(NBTTagCompound source)
  {
    NBTTagCompound result = new NBTTagCompound();
    String[] fields = {"Format", "Id", "Owner", "OwnerName", "Name", "Revision", "SizeX", "SizeY", "SizeZ",
            "AnchorX", "AnchorY", "AnchorZ", "BlockCount", "Thumbnail", "Palette", "Blocks", "Tiles"};
    for (String field : fields) if (source.hasKey(field)) result.setTag(field, source.getTag(field).copy());
    return result;
  }

  public static byte[] encode(NBTTagCompound data) throws IOException
  {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    CompressedStreamTools.writeCompressed(data, output);
    if (output.size() > MAX_BYTES) throw new IOException("Template transfer is too large.");
    return output.toByteArray();
  }
}
