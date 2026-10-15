package speedytools.serverside.templates;

import net.minecraft.nbt.*;
import speedytools.common.templates.TemplateGeometry;
import speedytools.common.utilities.ErrorLog;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** One atomic file per template; IDs are UUIDs, never user-provided filenames. */
public class TemplateStore
{
  public static final long MAX_FILE_BYTES = 8 * 1024 * 1024;
  private final Path folder;
  private final Map<String, NBTTagCompound> index = new TreeMap<String, NBTTagCompound>();

  public TemplateStore(Path folder) throws IOException
  {
    this.folder = folder;
    Files.createDirectories(folder);
    try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.nbt")) {
      for (Path file : files) {
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - 4);
        if (!validID(id)) continue;
        try {
          NBTTagCompound data = readFile(file);
          validate(data);
          if (!id.equals(data.getString("Id"))) throw new IOException("Mismatched template ID");
          index.put(id, summary(data));
        } catch (IOException | RuntimeException ex) {
          ErrorLog.defaultLog().log(org.apache.logging.log4j.Level.WARN, "Cannot load template %s: %s", file, ex.getMessage());
        }
      }
    }
  }

  public static boolean validID(String id)
  {
    return id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  private Path path(String id)
  {
    if (!validID(id)) throw new IllegalArgumentException("Invalid template ID.");
    return folder.resolve(id + ".nbt");
  }

  public Collection<NBTTagCompound> summaries()
  {
    List<NBTTagCompound> result = new ArrayList<NBTTagCompound>();
    for (NBTTagCompound info : index.values()) result.add((NBTTagCompound)info.copy());
    return result;
  }

  public NBTTagCompound info(String id)
  {
    NBTTagCompound value = index.get(id);
    return value == null ? null : (NBTTagCompound)value.copy();
  }

  /** Cache derived thumbnails for old files without creating an edit/recovery revision. */
  public void cacheThumbnail(String id, int revision, byte[] thumbnail)
  {
    NBTTagCompound current = index.get(id);
    if (current != null && current.getInteger("Revision") == revision && thumbnail.length == 512) {
      current.setByteArray("Thumbnail", thumbnail.clone());
    }
  }

  public NBTTagCompound read(String id) throws IOException
  {
    if (!index.containsKey(id)) throw new IllegalArgumentException("Template unavailable.");
    NBTTagCompound result = readFile(path(id));
    validate(result);
    return result;
  }

  public NBTTagCompound previous(String id) throws IOException
  {
    path(id);
    Path previous = folder.resolve(id + ".previous");
    if (!Files.exists(previous)) throw new IllegalArgumentException("No previous revision is available.");
    NBTTagCompound result = readFile(previous);
    validate(result);
    if (!id.equals(result.getString("Id"))) throw new IOException("Invalid previous revision");
    return result;
  }

  public void purge(String id, int expectedRevision) throws IOException
  {
    NBTTagCompound current = info(id);
    if (current == null || !current.getBoolean("Deleted") || current.getInteger("Revision") != expectedRevision) {
      throw new IllegalArgumentException("Refresh and select a template in Trash before permanently removing it.");
    }
    Path removed = folder.resolve(id + ".purged");
    Files.move(path(id), removed, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    index.remove(id);
    try {
      Files.deleteIfExists(folder.resolve(id + ".previous")); Files.deleteIfExists(removed);
    } catch (IOException ex) {
      ErrorLog.defaultLog().log(org.apache.logging.log4j.Level.WARN, "Template was purged, but residual files need cleanup: %s", removed);
    }
  }

  public void commit(NBTTagCompound data, int expectedRevision) throws IOException
  {
    String id = data.getString("Id");
    Path destination = path(id);
    NBTTagCompound current = index.get(id);
    int revision = current == null ? 0 : current.getInteger("Revision");
    if (revision != expectedRevision) throw new IllegalArgumentException("Template changed. Refresh and review it before saving.");
    if (current != null && !current.getString("Owner").equals(data.getString("Owner"))) {
      throw new IllegalArgumentException("Template ownership cannot change.");
    }
    data.setInteger("Revision", Math.addExact(revision, 1));
    data.setLong("Modified", System.currentTimeMillis());
    validate(data);
    Path temporary = Files.createTempFile(folder, "template-", ".tmp");
    try {
      try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
        // CompressedStreamTools closes its stream; sync via a separate descriptor below.
        CompressedStreamTools.writeCompressed(data, output);
      }
      if (Files.size(temporary) > MAX_FILE_BYTES) throw new IOException("Template exceeds the file size limit.");
      readFile(temporary); // Verify that this revision fits the decompressed NBT budget before replacing anything.
      try (RandomAccessFile file = new RandomAccessFile(temporary.toFile(), "rw")) { file.getFD().sync(); }
      if (current != null) {
        Path previousTemp = Files.createTempFile(folder, "previous-", ".tmp");
        try {
          Files.copy(destination, previousTemp, StandardCopyOption.REPLACE_EXISTING);
          Files.move(previousTemp, folder.resolve(id + ".previous"),
                  StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(previousTemp); }
      }
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      index.put(id, summary(data));
    } finally { Files.deleteIfExists(temporary); }
  }

  private static NBTTagCompound readFile(Path file) throws IOException
  {
    if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("Template file too large");
    try (DataInputStream input = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
      return CompressedStreamTools.func_152456_a(input, new NBTSizeTracker(32 * 1024 * 1024));
    }
  }

  public static NBTTagCompound summary(NBTTagCompound data)
  {
    NBTTagCompound info = (NBTTagCompound)data.copy();
    info.removeTag("Palette");
    info.removeTag("Blocks");
    info.removeTag("Tiles");
    return info;
  }

  public static void validate(NBTTagCompound data)
  {
    if (data.getInteger("Format") != 1 || !validID(data.getString("Id")) || !validID(data.getString("Owner"))
            || data.getInteger("Revision") < 1 || data.getString("Name").trim().isEmpty()
            || data.getString("Name").length() > 48) throw new IllegalArgumentException("Invalid template metadata.");
    int sx = data.getInteger("SizeX"), sy = data.getInteger("SizeY"), sz = data.getInteger("SizeZ");
    int volume = TemplateGeometry.volume(sx, sy, sz, 262144);
    if (data.getInteger("AnchorX") < 0 || data.getInteger("AnchorX") >= sx
            || data.getInteger("AnchorY") < 0 || data.getInteger("AnchorY") >= sy
            || data.getInteger("AnchorZ") < 0 || data.getInteger("AnchorZ") >= sz) {
      throw new IllegalArgumentException("Anchor must be inside the selection.");
    }
    NBTTagList palette = data.getTagList("Palette", 10);
    int[] blocks = data.getIntArray("Blocks");
    if (blocks.length != volume || palette.tagCount() == 0 || palette.tagCount() > 65536) {
      throw new IllegalArgumentException("Invalid template block data.");
    }
    for (int value : blocks) if (value < 0 || value >= palette.tagCount()) {
      throw new IllegalArgumentException("Invalid template palette index.");
    }
    NBTTagList tiles = data.getTagList("Tiles", 10);
    if (tiles.tagCount() > volume) throw new IllegalArgumentException("Too many tile entities.");
    for (int i = 0; i < tiles.tagCount(); ++i) {
      int cell = tiles.getCompoundTagAt(i).getInteger("Cell");
      if (cell < 0 || cell >= volume) throw new IllegalArgumentException("Invalid tile entity position.");
    }
  }
}
