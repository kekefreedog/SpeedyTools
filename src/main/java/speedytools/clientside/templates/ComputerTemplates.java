package speedytools.clientside.templates;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import speedytools.common.templates.TemplateNetwork;
import speedytools.common.templates.TemplateTransfer;
import speedytools.serverside.templates.TemplateStore;
import java.io.IOException;
import java.util.*;

/** Client disk library and explicitly requested transfers. Never accesses a server filesystem. */
public final class ComputerTemplates
{
  private static TemplateStore store;
  private static String token = "", destination = "";
  private static int expected, offset, age;
  private static byte[] upload;
  private static TemplateTransfer download;
  private static boolean uploading;

  public static TemplateStore store() throws IOException
  {
    if (store == null) store = new TemplateStore(Minecraft.getMinecraft().mcDataDir.toPath().resolve("speedytools-templates"));
    return store;
  }

  public static void thumbnail(NBTTagCompound info) throws IOException
  {
    if (info.getByteArray("Thumbnail").length == 512) return;
    try {
      byte[] samples = speedytools.serverside.templates.TemplateCodec.thumbnail(store().read(info.getString("Id")));
      info.setByteArray("Thumbnail", samples); store().cacheThumbnail(info.getString("Id"), info.getInteger("Revision"), samples);
    } catch (RuntimeException ex) { /* Missing registry entries retain the paper fallback. */ }
  }

  public static boolean isTransferring() { return !token.isEmpty(); }

  public static void reset()
  {
    token = ""; upload = null; download = null; uploading = false; age = 0;
  }

  private static String begin() throws IOException
  {
    if (!token.isEmpty()) throw new IOException("A template transfer is already in progress.");
    token = UUID.randomUUID().toString(); age = 0; return token;
  }

  public static void capture(NBTTagCompound request) throws IOException
  {
    String transfer = begin();
    destination = request.getString("Id"); expected = request.getInteger("Revision");
    request.setString("Transfer", transfer); request.setBoolean("Computer", true);
    TemplateNetwork.send(request); TemplateClient.tell("Capturing to Computer...");
  }

  public static void export(NBTTagCompound info) throws IOException
  {
    String transfer = begin(); destination = ""; expected = 0;
    NBTTagCompound request = TemplateNetwork.request("EXPORT");
    request.setString("Id", info.getString("Id")); request.setInteger("Revision", info.getInteger("Revision"));
    request.setString("Transfer", transfer); TemplateNetwork.send(request);
    TemplateClient.tell("Copying to Computer...");
  }

  public static void upload(NBTTagCompound info, boolean equip) throws IOException
  {
    NBTTagCompound data = store().read(info.getString("Id"));
    if (data.getBoolean("Deleted")) throw new IOException("Restore this template first.");
    byte[] bytes = TemplateTransfer.encode(data);
    String transfer = begin(); upload = bytes; offset = 0; uploading = false;
    NBTTagCompound request = TemplateNetwork.request("IMPORT_BEGIN");
    request.setString("Transfer", transfer); request.setInteger("Length", bytes.length); request.setBoolean("Equip", equip);
    TemplateNetwork.send(request); TemplateClient.tell("Requesting import...");
  }

  public static void tick()
  {
    if (token.isEmpty()) return;
    if (++age > 2400 || !TemplateClient.holdingTool()) { reset(); TemplateClient.tell("Template transfer cancelled or timed out."); return; }
    // Five packets/second leaves room for library requests under the server request limit.
    if (uploading && upload != null && age % 4 == 0) {
      int end = Math.min(upload.length, offset + TemplateTransfer.CHUNK_BYTES);
      NBTTagCompound request = TemplateNetwork.request("IMPORT_CHUNK");
      request.setString("Transfer", token); request.setInteger("Offset", offset);
      request.setByteArray("Bytes", Arrays.copyOfRange(upload, offset, end)); TemplateNetwork.send(request); offset = end;
      if (end == upload.length) { upload = null; uploading = false; }
    }
  }

  public static boolean receive(NBTTagCompound message)
  {
    String action = message.getString("Action");
    if ("STATUS".equals(action) && message.getBoolean("Error")) { reset(); return false; }
    if (!action.startsWith("EXPORT_") && !"IMPORT_READY".equals(action) && !"IMPORTED".equals(action)) return false;
    if (token.isEmpty() || !token.equals(message.getString("Transfer"))) return true;
    try {
      if ("EXPORT_BEGIN".equals(action)) {
        if (download != null || upload != null) throw new IOException("Unexpected transfer response.");
        download = new TemplateTransfer(message.getInteger("Length"));
      } else if ("EXPORT_CHUNK".equals(action) && download != null) {
        download.append(message.getInteger("Offset"), message.getByteArray("Bytes"));
        if (download.complete()) {
          NBTTagCompound data = download.read(); TemplateStore.validate(data);
          data = TemplateTransfer.portable(data);
          if (destination.isEmpty()) {
            if (store().summaries().size() >= 2048) throw new IOException("Computer library quota reached (trash also counts).");
            data.setString("Id", UUID.randomUUID().toString());
          } else {
            NBTTagCompound current = store().info(destination);
            if (current == null || current.getBoolean("Deleted")) throw new IOException("Computer template is no longer available.");
            data.setString("Id", destination); data.setString("Owner", current.getString("Owner"));
            data.setString("OwnerName", current.getString("OwnerName"));
          }
          data.setBoolean("Shared", false); data.setBoolean("Deleted", false);
          store().commit(data, expected); reset(); refresh(); TemplateClient.tell("Saved to Computer.");
        }
      } else if ("IMPORT_READY".equals(action) && upload != null) uploading = true;
      else if ("IMPORTED".equals(action)) {
        reset(); refresh();
        if (message.getBoolean("Equip")) { TemplateClient.load(message.getString("Id")); Minecraft.getMinecraft().displayGuiScreen(null); }
      }
    } catch (IOException | RuntimeException ex) { reset(); TemplateClient.tell("Template transfer failed: " + ex.getMessage()); }
    return true;
  }

  public static void refresh()
  {
    if (Minecraft.getMinecraft().currentScreen instanceof TemplateLibraryScreen) {
      ((TemplateLibraryScreen)Minecraft.getMinecraft().currentScreen).refresh();
    }
  }

  public static void manage(NBTTagCompound request) throws IOException
  {
    String id = request.getString("Id"), action = request.getString("Action");
    NBTTagCompound data = store().read(id);
    int revision = request.getInteger("Revision");
    if (data.getInteger("Revision") != revision) throw new IOException("Computer template changed; refresh first.");
    if ("PURGE".equals(action)) store().purge(id, revision);
    else {
      if ("RESTORE".equals(action)) data.setBoolean("Deleted", false);
      else {
        if (data.getBoolean("Deleted")) throw new IOException("Restore this template first.");
        if ("DELETE".equals(action)) data.setBoolean("Deleted", true);
        else if ("RENAME".equals(action)) data.setString("Name", request.getString("Name").trim());
        else if ("PREVIOUS".equals(action)) { data = store().previous(id); data.setBoolean("Deleted", false); }
        else throw new IOException("Unsupported computer action.");
      }
      store().commit(data, revision);
    }
    refresh(); TemplateClient.tell("Computer template updated.");
  }
}
