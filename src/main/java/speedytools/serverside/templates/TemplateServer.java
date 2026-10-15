package speedytools.serverside.templates;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import speedytools.common.items.RegistryForItems;
import speedytools.common.selections.VoxelSelection;
import speedytools.common.selections.VoxelSelectionWithOrigin;
import speedytools.common.templates.*;
import speedytools.common.utilities.ErrorLog;
import speedytools.common.utilities.QuadOrientation;
import speedytools.common.utilities.ResultWithReason;
import speedytools.serverside.ServerSide;
import speedytools.serverside.worldmanipulation.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** All authority and world access lives on the server main thread. */
public final class TemplateServer
{
  private static TemplateStore store;
  private static Job job;
  private static final Map<UUID, Session> sessions = new HashMap<UUID, Session>();
  private static boolean enabled = true, creativeOnly = true;
  private static int maxBlocks = 65536, maxPerPlayer = 128, maxTotal = 2048, captureDistance = 256;
  private static int ticks;
  private static boolean computerImports = true, operatorImports = true;

  private TemplateServer() {}

  public static void configure(File directory)
  {
    Configuration config = new Configuration(new File(directory, "speedytools-templates.cfg"));
    config.load();
    enabled = config.getBoolean("enabled", "templates", true, "Enable the structure template tool.");
    creativeOnly = config.getBoolean("creativeOrOperatorOnly", "templates", true,
            "Require creative mode or operator status for captures, changes, builds and undo. Builds do not consume materials.");
    maxBlocks = config.getInt("maxBlocks", "templates", 65536, 1, 262144, "Maximum selection volume, including air.");
    maxPerPlayer = config.getInt("maxPerPlayer", "templates", 128, 1, 1024, "Maximum templates per owner, including trash.");
    maxTotal = config.getInt("maxTotal", "templates", 2048, 1, 8192, "Maximum templates in this world, including trash.");
    captureDistance = config.getInt("captureDistance", "templates", 256, 8, 512, "Maximum distance from the player to capture corners.");
    computerImports = config.getBoolean("allowComputerImports", "templates", true, "Allow uploads from computer libraries.");
    operatorImports = config.getBoolean("operatorOnlyImports", "templates", true, "Require operator status for multiplayer imports: uploaded block entity data may contain commands or inventories.");
    if (config.hasChanged()) config.save();
  }

  private static TemplateStore store() throws IOException
  {
    if (store == null) store = new TemplateStore(MinecraftServer.getServer().worldServerForDimension(0)
            .getSaveHandler().getWorldDirectory().toPath().resolve("speedytools-templates"));
    return store;
  }

  public static boolean isBusy() { return job != null; }

  private static boolean operator(EntityPlayerMP player) { return player.canUseCommand(2, "speedytemplates"); }

  private static void requireBuilder(EntityPlayerMP player)
  {
    if (creativeOnly && !player.capabilities.isCreativeMode && !operator(player)) {
      throw new IllegalArgumentException("Template changes and building require creative mode or operator permission.");
    }
  }

  private static boolean owns(EntityPlayerMP player, NBTTagCompound data)
  {
    return player.getUniqueID().toString().equals(data.getString("Owner")) || operator(player);
  }

  private static boolean visible(EntityPlayerMP player, NBTTagCompound data)
  {
    return data != null && (owns(player, data) || (!data.getBoolean("Deleted") && data.getBoolean("Shared")));
  }

  private static NBTTagCompound accessible(EntityPlayerMP player, String id, boolean edit) throws IOException
  {
    NBTTagCompound data = store().info(id);
    if (!visible(player, data) || (edit && !owns(player, data))) {
      throw new IllegalArgumentException("Template unavailable or permission denied.");
    }
    return data;
  }

  private static void revision(NBTTagCompound info, NBTTagCompound request)
  {
    if (info.getInteger("Revision") != request.getInteger("Revision")) {
      throw new IllegalArgumentException("Template changed. Refresh and review the new revision.");
    }
  }

  public static void handle(EntityPlayerMP player, NBTTagCompound request)
  {
    boolean catalogue = "CATALOGUE".equals(request.getString("Action")) && player.capabilities.isCreativeMode;
    if (!enabled || player.isDead || (!holdingTool(player) && !catalogue)) return;
    Session session = sessions.get(player.getUniqueID());
    if (session == null) { session = new Session(); sessions.put(player.getUniqueID(), session); }
    if (session.window != ticks / 20) { session.window = ticks / 20; session.requests = 0; }
    if (++session.requests > 12) return;
    try {
      String action = request.getString("Action");
      if (catalogue || "LIST".equals(action)) { list(player, request); return; }
      if ("PREVIEW".equals(action) || "EQUIP".equals(action)) { preview(player, session, request); return; }
      if ("EXPORT".equals(action)) { export(player, session, request); return; }
      requireBuilder(player);
      if ("IMPORT_BEGIN".equals(action)) { beginImport(player, session, request); return; }
      if ("IMPORT_CHUNK".equals(action)) { importChunk(player, session, request); return; }
      if ("SAVE".equals(action)) { save(player, request); return; }
      if ("PLACE".equals(action)) { place(player, session, request); return; }
      if ("UNDO".equals(action)) { undo(player); return; }
      if (!Arrays.asList("RENAME", "SHARE", "DELETE", "RESTORE", "PREVIOUS", "PURGE").contains(action)) return;
      if (job != null) throw new IllegalArgumentException("Wait for the current template operation to finish.");
      NBTTagCompound info = accessible(player, request.getString("Id"), true);
      revision(info, request);
      if ("PURGE".equals(action)) {
        store().purge(info.getString("Id"), info.getInteger("Revision"));
        invalidate(info.getString("Id")); status(player, "Template permanently removed.", false); return;
      }
      NBTTagCompound data = store().read(info.getString("Id"));
      if ("RESTORE".equals(action)) {
        if (!data.getBoolean("Deleted")) throw new IllegalArgumentException("Template is not in trash.");
        data.setBoolean("Deleted", false);
      } else {
        if (data.getBoolean("Deleted")) throw new IllegalArgumentException("Restore this template first.");
        if ("RENAME".equals(action)) data.setString("Name", cleanName(request.getString("Name")));
        if ("SHARE".equals(action)) data.setBoolean("Shared", request.getBoolean("Shared"));
        if ("DELETE".equals(action)) data.setBoolean("Deleted", true);
        if ("PREVIOUS".equals(action)) {
          data = store().previous(info.getString("Id"));
          data.setBoolean("Deleted", false);
          data.setBoolean("Shared", info.getBoolean("Shared"));
        }
      }
      store().commit(data, info.getInteger("Revision"));
      invalidate(data.getString("Id"));
      status(player, "Template updated.", false);
    } catch (IOException | RuntimeException ex) {
      status(player, ex instanceof IOException ? "Save failed." : ex.getMessage(), true);
      if (ex instanceof IOException) ErrorLog.defaultLog().log(org.apache.logging.log4j.Level.WARN, ex, "Template storage failed.");
    }
  }

  private static String transferID(NBTTagCompound request)
  {
    String token = request.getString("Transfer");
    if (!TemplateStore.validID(token)) throw new IllegalArgumentException("Invalid transfer ID.");
    return token;
  }

  private static void export(EntityPlayerMP player, Session session, NBTTagCompound request) throws IOException
  {
    NBTTagCompound info = accessible(player, request.getString("Id"), false);
    revision(info, request);
    if (info.getBoolean("Deleted")) throw new IllegalArgumentException("Restore this template first.");
    download(player, session, store().read(info.getString("Id")), transferID(request));
  }

  private static void download(EntityPlayerMP player, Session session, NBTTagCompound data, String token) throws IOException
  {
    session.upload = null;
    session.download = TemplateTransfer.encode(data); session.transferOffset = 0;
    session.transferToken = token; session.world = player.getServerForPlayer(); session.expires = ticks + 2400;
    NBTTagCompound reply = TemplateNetwork.request("EXPORT_BEGIN");
    reply.setString("Transfer", token); reply.setInteger("Length", session.download.length); send(player, reply);
  }

  private static void requireImport(EntityPlayerMP player)
  {
    requireBuilder(player);
    if (!computerImports) throw new IllegalArgumentException("Computer imports are disabled on this server.");
    if (operatorImports && !MinecraftServer.getServer().isSinglePlayer() && !operator(player)) {
      throw new IllegalArgumentException("This server requires operator permission to import computer templates.");
    }
  }

  private static void beginImport(EntityPlayerMP player, Session session, NBTTagCompound request)
  {
    requireImport(player);
    if (session.upload != null && ticks <= session.expires) throw new IllegalArgumentException("An upload is already in progress.");
    session.transferToken = transferID(request);
    session.upload = new TemplateTransfer(request.getInteger("Length")); session.download = null;
    session.world = player.getServerForPlayer(); session.expires = ticks + 2400;
    session.equipImport = request.getBoolean("Equip");
    NBTTagCompound reply = TemplateNetwork.request("IMPORT_READY"); reply.setString("Transfer", session.transferToken); send(player, reply);
  }

  private static void importChunk(EntityPlayerMP player, Session session, NBTTagCompound request) throws IOException
  {
    requireImport(player);
    if (session.upload == null || ticks > session.expires || session.world != player.getServerForPlayer()
            || !session.transferToken.equals(request.getString("Transfer"))) throw new IllegalArgumentException("Upload expired; try again.");
    try {
      session.upload.append(request.getInteger("Offset"), request.getByteArray("Bytes"));
      if (!session.upload.complete()) return;
      NBTTagCompound data = session.upload.read(); session.upload = null;
      TemplateStore.validate(data);
      data = TemplateTransfer.portable(data);
      TemplateGeometry.volume(data.getInteger("SizeX"), data.getInteger("SizeY"), data.getInteger("SizeZ"), maxBlocks);
      checkRestrictedBlocks(player, TemplateCodec.palette(data));
      int owned = 0;
      Collection<NBTTagCompound> all = store().summaries();
      for (NBTTagCompound entry : all) if (player.getUniqueID().toString().equals(entry.getString("Owner"))) ++owned;
      if (owned >= maxPerPlayer || all.size() >= maxTotal) throw new IllegalArgumentException("World template quota reached (trash also counts).");
      data.setString("Name", cleanName(data.getString("Name")));
      data.setString("Id", UUID.randomUUID().toString()); data.setString("Owner", player.getUniqueID().toString());
      data.setString("OwnerName", player.getName()); data.setBoolean("Shared", false); data.setBoolean("Deleted", false);
      data.removeTag("CanEdit"); data.removeTag("Unavailable");
      data.setByteArray("Thumbnail", TemplateCodec.thumbnail(data));
      store().commit(data, 0); invalidate(data.getString("Id"));
      NBTTagCompound reply = TemplateNetwork.request("IMPORTED"); reply.setString("Transfer", session.transferToken);
      reply.setString("Id", data.getString("Id")); reply.setBoolean("Equip", session.equipImport); send(player, reply);
      status(player, "Saved to World (private).", false);
    } catch (IOException | RuntimeException ex) { session.upload = null; throw ex; }
  }

  private static String cleanName(String value)
  {
    String name = value.trim();
    if (name.isEmpty() || name.length() > 48 || name.matches(".*[\\p{Cntrl}\\u00a7].*")) {
      throw new IllegalArgumentException("Use a name of 1 to 48 characters, without formatting codes.");
    }
    return name;
  }

  private static void list(EntityPlayerMP player, NBTTagCompound request) throws IOException
  {
    boolean catalogue = "CATALOGUE".equals(request.getString("Action"));
    int pageSize = catalogue ? 8 : 24;
    String filter = catalogue ? "All" : request.getString("Filter");
    String query = request.getString("Query");
    if (query.length() > 48) query = query.substring(0, 48);
    List<NBTTagCompound> matches = new ArrayList<NBTTagCompound>();
    for (NBTTagCompound info : store().summaries()) {
      if (!visible(player, info)) continue;
      if (info.getBoolean("Deleted") != "Trash".equals(filter)) continue;
      if ("Mine".equals(filter) && !player.getUniqueID().toString().equals(info.getString("Owner"))) continue;
      if ("Shared".equals(filter) && !info.getBoolean("Shared")) continue;
      if (!info.getString("Name").toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
      info.setBoolean("CanEdit", owns(player, info));
      matches.add(info);
    }
    Collections.sort(matches, new Comparator<NBTTagCompound>() {
      public int compare(NBTTagCompound a, NBTTagCompound b) { return a.getString("Name").compareToIgnoreCase(b.getString("Name")); }
    });
    int page = Math.max(0, Math.min(request.getInteger("Page"), Math.max(0, (matches.size() - 1) / pageSize)));
    NBTTagCompound reply = TemplateNetwork.request(catalogue ? "CATALOGUE" : "LIST");
    reply.setInteger("Page", page); reply.setInteger("Total", matches.size());
    reply.setInteger("Request", request.getInteger("Request"));
    reply.setInteger("MaxBlocks", maxBlocks);
    NBTTagList entries = new NBTTagList();
    for (int i = page * pageSize; i < Math.min(matches.size(), (page + 1) * pageSize); ++i) {
      NBTTagCompound entry = matches.get(i);
      if (entry.getByteArray("Thumbnail").length != 512) {
        try {
          entry.setByteArray("Thumbnail", TemplateCodec.thumbnail(store().read(entry.getString("Id"))));
          store().cacheThumbnail(entry.getString("Id"), entry.getInteger("Revision"), entry.getByteArray("Thumbnail"));
        }
        catch (RuntimeException ex) { /* Keep unavailable mod templates browsable with a paper icon. */ }
      }
      entries.appendTag(entry);
    }
    reply.setTag("Entries", entries);
    send(player, reply);
  }

  private static void preview(EntityPlayerMP player, Session session, NBTTagCompound request) throws IOException
  {
    NBTTagCompound info = accessible(player, request.getString("Id"), false);
    if (info.getBoolean("Deleted")) throw new IllegalArgumentException("Template is in trash.");
    NBTTagCompound data = store().read(info.getString("Id"));
    IBlockState[] palette = TemplateCodec.palette(data);
    checkRestrictedBlocks(player, palette);
    int[] cells = data.getIntArray("Blocks");
    session.preview = new int[cells.length];
    for (int i = 0; i < cells.length; ++i) session.preview[i] = Block.getStateId(palette[cells[i]]);
    session.info = info;
    session.offset = 0;
    session.ticket = UUID.randomUUID().toString();
    session.world = player.getServerForPlayer();
    session.ready = false;
    if (player.getHeldItem().getItem() == RegistryForItems.itemStructureCapture) {
      player.inventory.setInventorySlotContents(player.inventory.currentItem, new ItemStack(RegistryForItems.itemStructureTemplate));
    }
    player.getHeldItem().setTagCompound((NBTTagCompound)info.copy());
    player.inventory.markDirty();
    NBTTagCompound reply = TemplateNetwork.request("BEGIN");
    reply.setTag("Info", info); reply.setString("Ticket", session.ticket);
    reply.setInteger("Count", cells.length);
    send(player, reply);
  }

  private static void checkRestrictedBlocks(EntityPlayerMP player, IBlockState[] palette)
  {
    if (operator(player)) return;
    for (IBlockState state : palette) if (state.getBlock() == net.minecraft.init.Blocks.command_block) {
      throw new IllegalArgumentException("Templates containing command blocks require operator permission.");
    }
  }

  private static void idle()
  {
    if (job != null || ServerSide.getSpeedyToolServerActions().isAsynchronousActionInProgress()) {
      throw new IllegalArgumentException("The server is busy building. Try again when it finishes.");
    }
  }

  private static void save(EntityPlayerMP player, NBTTagCompound request) throws IOException
  {
    idle();
    String name = cleanName(request.getString("Name"));
    boolean computer = request.getBoolean("Computer");
    String downloadToken = computer ? transferID(request) : null;
    BlockPos a = TemplateGeometry.position(request, "First"), b = TemplateGeometry.position(request, "Second");
    if (request.getInteger("Dimension") != player.dimension) throw new IllegalArgumentException("Selection belongs to another dimension.");
    if (a.distanceSq(player.posX, player.posY, player.posZ) > (long)captureDistance * captureDistance
            || b.distanceSq(player.posX, player.posY, player.posZ) > (long)captureDistance * captureDistance) {
      throw new IllegalArgumentException("Move closer to the selection before saving.");
    }
    BlockPos minimum = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    int sx = Math.abs(a.getX() - b.getX()) + 1, sy = Math.abs(a.getY() - b.getY()) + 1, sz = Math.abs(a.getZ() - b.getZ()) + 1;
    TemplateGeometry.volume(sx, sy, sz, maxBlocks);
    NBTTagCompound data = new NBTTagCompound();
    int expected = 0;
    String id = computer ? "" : request.getString("Id");
    if (!id.isEmpty()) {
      data = accessible(player, id, true); revision(data, request);
      if (data.getBoolean("Deleted")) throw new IllegalArgumentException("Restore this template first.");
      expected = data.getInteger("Revision");
    } else {
      int owned = 0, total = 0;
      for (NBTTagCompound info : store().summaries()) {
        ++total;
        if (player.getUniqueID().toString().equals(info.getString("Owner"))) ++owned;
      }
      if (!computer && (owned >= maxPerPlayer || total >= maxTotal)) throw new IllegalArgumentException("Template storage quota reached (trash also counts).");
      data.setString("Id", UUID.randomUUID().toString());
      data.setString("Owner", player.getUniqueID().toString());
      data.setString("OwnerName", player.getName());
    }
    data.removeTag("CanEdit");
    data.setString("Name", name); data.setInteger("SizeX", sx); data.setInteger("SizeY", sy); data.setInteger("SizeZ", sz);
    BlockPos anchor = TemplateGeometry.position(request, "Anchor");
    data.setInteger("AnchorX", anchor.getX() - minimum.getX());
    data.setInteger("AnchorY", anchor.getY() - minimum.getY());
    data.setInteger("AnchorZ", anchor.getZ() - minimum.getZ());
    if (data.getInteger("AnchorX") < 0 || data.getInteger("AnchorX") >= sx || data.getInteger("AnchorY") < 0
            || data.getInteger("AnchorY") >= sy || data.getInteger("AnchorZ") < 0 || data.getInteger("AnchorZ") >= sz) {
      throw new IllegalArgumentException("Choose an anchor inside the selection.");
    }
    job = new Job(player, data, minimum, expected);
    job.downloadToken = downloadToken;
    status(player, "Checking selection...", false);
  }

  private static void place(EntityPlayerMP player, Session session, NBTTagCompound request) throws IOException
  {
    idle();
    if (!session.ready || session.info == null || !session.ticket.equals(request.getString("Ticket"))
            || session.world != player.getServerForPlayer()) throw new IllegalArgumentException("Load a fresh preview before building.");
    NBTTagCompound info = accessible(player, session.info.getString("Id"), false);
    revision(info, session.info);
    if (info.getBoolean("Deleted")) throw new IllegalArgumentException("Template unavailable.");
    BlockPos target = TemplateGeometry.position(request, "Target");
    if (target.distanceSq(player.posX, player.posY, player.posZ) > 64 * 64) throw new IllegalArgumentException("Placement target is too far away.");
    NBTTagCompound data = store().read(info.getString("Id"));
    TemplateGeometry.volume(data.getInteger("SizeX"), data.getInteger("SizeY"), data.getInteger("SizeZ"), maxBlocks);
    job = new Job(player, data, target, request.getInteger("Rotation") & 3, request.getBoolean("Mirror"), request.getBoolean("IncludeAir"));
    session.ready = false; // one confirmation per preview; replayed packets cannot stamp twice
    status(player, "Checking destination...", false);
  }

  private static void undo(EntityPlayerMP player)
  {
    idle();
    VoxelSelectionWithOrigin region = ServerSide.getWorldHistory().getRegionForNextComplexUndo(player, player.getServerForPlayer());
    if (region == null) throw new IllegalArgumentException("No completed construction to undo in this dimension.");
    job = new Job(player, region);
    status(player, "Undoing...", false);
  }

  private static void checkCell(EntityPlayerMP player, WorldServer world, BlockPos point)
  {
    if (point.getY() < 0 || point.getY() >= 256 || !world.getWorldBorder().contains(point)) {
      throw new IllegalArgumentException("Selection extends outside the world bounds.");
    }
    if (!world.isBlockLoaded(point)) throw new IllegalArgumentException("All affected chunks must be loaded. Move closer and try again.");
    if (!world.isBlockModifiable(player, point) || MinecraftServer.getServer().isBlockProtected(world, point, player)
            || !player.canPlayerEdit(point, EnumFacing.UP, player.getHeldItem())) {
      throw new IllegalArgumentException("You cannot build in this region.");
    }
  }

  public static void tick()
  {
    ++ticks;
    if (!enabled || MinecraftServer.getServer() == null) return;
    if (job != null) {
      try {
        if (job.advance()) {
          if (!job.failed) status(job.player, "Template operation completed.", false);
          if (!job.capture && !job.undo) buildProgress(job, true, job.failed);
          job = null;
        } else if (ticks % 4 == 0 && !job.capture && !job.undo) buildProgress(job, false, false);
      } catch (IOException | RuntimeException ex) {
        ErrorLog.defaultLog().log(org.apache.logging.log4j.Level.WARN, ex, "Template operation failed.");
        if (!job.capture && !job.undo) buildProgress(job, true, true);
        status(job.player, "Template operation stopped: " + ex.getMessage(), true);
        if (job.token != null && !job.token.isTaskComplete()) {
          job.failed = true;
          job.token.abortProcessing();
        } else job = null;
      }
    }
    Set<UUID> online = new HashSet<UUID>();
    for (Object entry : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
      EntityPlayerMP player = (EntityPlayerMP)entry;
      online.add(player.getUniqueID());
      Session session = sessions.get(player.getUniqueID());
      if (session != null) {
        if (ticks > session.expires || !holdingTool(player) || session.world != player.getServerForPlayer()) {
          session.upload = null; session.download = null;
        }
        if (session.download != null) {
          int end = Math.min(session.download.length, session.transferOffset + TemplateTransfer.CHUNK_BYTES);
          NBTTagCompound packet = TemplateNetwork.request("EXPORT_CHUNK");
          packet.setString("Transfer", session.transferToken); packet.setInteger("Offset", session.transferOffset);
          packet.setByteArray("Bytes", Arrays.copyOfRange(session.download, session.transferOffset, end));
          send(player, packet); session.transferOffset = end;
          if (end == session.download.length) session.download = null;
        }
      }
      if (session != null && session.preview != null) {
        if (!holdingTool(player) || session.world != player.getServerForPlayer()) {
          session.preview = null; session.ready = false;
        } else {
          for (int chunk = 0; chunk < 2 && session.preview != null; ++chunk) {
            int end = Math.min(session.preview.length, session.offset + 1024);
            NBTTagCompound reply = TemplateNetwork.request("CHUNK");
            reply.setString("Ticket", session.ticket); reply.setInteger("Offset", session.offset);
            reply.setIntArray("States", Arrays.copyOfRange(session.preview, session.offset, end));
            send(player, reply); session.offset = end;
            if (end == session.preview.length) {
              session.preview = null; session.ready = true;
              NBTTagCompound done = TemplateNetwork.request("READY"); done.setString("Ticket", session.ticket); send(player, done);
            }
          }
        }
      }
      if (ticks % 20 == 0 && store != null && holdingTool(player)) {
        ItemStack item = player.getHeldItem(); NBTTagCompound old = item.getTagCompound();
        if (old != null && old.hasKey("Id") && !old.getBoolean("Computer")) {
          NBTTagCompound current = store.info(old.getString("Id"));
          if (visible(player, current) && !current.getBoolean("Deleted")) {
            if (old.getInteger("Revision") != current.getInteger("Revision") || old.getBoolean("Unavailable")) {
              item.setTagCompound(current); player.inventory.markDirty();
            }
          } else if (!old.getBoolean("Unavailable")) { old.setBoolean("Unavailable", true); player.inventory.markDirty(); }
        }
      }
    }
    sessions.keySet().retainAll(online);
  }

  private static void buildProgress(Job current, boolean done, boolean failed)
  {
    NBTTagCompound reply = TemplateNetwork.request("BUILD_PROGRESS");
    reply.setBoolean("Done", done); reply.setBoolean("Failed", failed);
    reply.setFloat("Progress", done ? 100 : current.token == null ? 0 : (float)(100 * current.token.getFractionComplete()));
    send(current.player, reply);
  }

  private static boolean holdingTool(EntityPlayerMP player)
  {
    return player.getHeldItem() != null && (player.getHeldItem().getItem() == RegistryForItems.itemStructureTemplate
            || player.getHeldItem().getItem() == RegistryForItems.itemStructureCapture);
  }

  private static void invalidate(String id)
  {
    for (Session session : sessions.values()) {
      if (session.info != null && id.equals(session.info.getString("Id"))) { session.ready = false; session.preview = null; }
    }
    NBTTagCompound message = TemplateNetwork.request("INVALIDATE"); message.setString("Id", id);
    TemplateNetwork.CHANNEL.sendToAll(new TemplateNetwork.Message(message));
  }

  private static void send(EntityPlayerMP player, NBTTagCompound data)
  {
    TemplateNetwork.CHANNEL.sendTo(new TemplateNetwork.Message(data), player);
  }

  private static void status(EntityPlayerMP player, String text, boolean error)
  {
    NBTTagCompound message = TemplateNetwork.request("STATUS");
    message.setString("Text", text == null ? "Template operation failed." : text);
    message.setBoolean("Error", error); send(player, message);
  }

  /** Called before server world saves. Finish an already-started write; discard pending captures/preflight. */
  public static void stopping()
  {
    if (job != null && job.token != null && !job.capture) {
      job.token.setTimeOfInterrupt(AsynchronousToken.INFINITE_TIMEOUT);
      while (!job.token.isTaskComplete()) job.token.continueProcessing();
    }
    reset();
  }

  public static void reset() { job = null; store = null; sessions.clear(); ticks = 0; }

  private static class Session
  {
    int window, requests, offset;
    TemplateTransfer upload;
    byte[] download;
    int transferOffset, expires;
    String transferToken = "";
    boolean equipImport;
    int[] preview;
    String ticket = "";
    NBTTagCompound info;
    WorldServer world;
    boolean ready;
  }

  private static class Job
  {
    final EntityPlayerMP player;
    final WorldServer world;
    NBTTagCompound data;
    BlockPos origin;
    boolean capture, includeAir, mirror, undo, failed;
    String downloadToken;
    int expectedRevision, rotation, checked;
    BlockPos checkOrigin;
    int checkX, checkY, checkZ;
    UniqueTokenID undoID;
    WorldFragment fragment;
    AsynchronousToken token;

    Job(EntityPlayerMP player, NBTTagCompound data, BlockPos origin, int expected)
    {
      this.player = player; this.world = player.getServerForPlayer(); this.data = data;
      this.origin = origin; this.expectedRevision = expected; this.capture = true;
    }

    Job(EntityPlayerMP player, NBTTagCompound data, BlockPos target, int rotation, boolean mirror, boolean includeAir)
    {
      this.player = player; this.world = player.getServerForPlayer(); this.data = data;
      this.origin = target; this.rotation = rotation; this.mirror = mirror; this.includeAir = includeAir;
    }

    Job(EntityPlayerMP player, VoxelSelectionWithOrigin region)
    {
      this.player = player; this.world = player.getServerForPlayer(); this.undo = true;
      this.undoID = ServerSide.getWorldHistory().getTransactionIDForNextComplexUndo(player, world);
      this.checkOrigin = new BlockPos(region.getWxOrigin(), Math.max(0, region.getWyOrigin()), region.getWzOrigin());
      this.checkX = region.getxSize(); this.checkZ = region.getzSize();
      this.checkY = Math.min(256, region.getWyOrigin() + region.getySize()) - checkOrigin.getY();
    }

    boolean advance() throws IOException
    {
      long deadline = System.nanoTime() + 3000000L;
      if (failed) {
        token.setTimeOfInterrupt(deadline); token.continueProcessing(); return token.isTaskComplete();
      }
      if (token == null) {
        if (player.isDead || player.getServerForPlayer() != world || !holdingTool(player)
                || !MinecraftServer.getServer().getConfigurationManager().playerEntityList.contains(player)) {
          throw new IllegalArgumentException("Player disconnected, changed dimension or put away the tool.");
        }
        requireBuilder(player);
        int sx = undo ? checkX : data.getInteger("SizeX"), sy = undo ? checkY : data.getInteger("SizeY"), sz = undo ? checkZ : data.getInteger("SizeZ");
        int volume = sx * sy * sz;
        if (checkOrigin == null) {
          if (capture) { checkOrigin = origin; checkX = sx; checkY = sy; checkZ = sz; }
          else {
            BlockPos first = TemplateGeometry.point(data, origin, rotation, mirror, 0);
            BlockPos last = TemplateGeometry.point(data, origin, rotation, mirror, volume - 1);
            if (first.getY() < 0 || last.getY() >= 256) throw new IllegalArgumentException("Template extends outside world height.");
            checkOrigin = new BlockPos(Math.min(first.getX(), last.getX()) - 1, Math.max(0, first.getY() - 1), Math.min(first.getZ(), last.getZ()) - 1);
            checkX = Math.abs(last.getX() - first.getX()) + 3;
            checkZ = Math.abs(last.getZ() - first.getZ()) + 3;
            checkY = Math.min(256, last.getY() + 2) - checkOrigin.getY();
          }
        }
        int checkVolume = checkX * checkY * checkZ;
        while (checked < checkVolume && System.nanoTime() < deadline) {
          BlockPos point = checkOrigin.add(checked % checkX, checked / (checkX * checkZ), checked / checkX % checkZ);
          checkCell(player, world, point); ++checked;
        }
        if (checked < checkVolume) return false;
        if (capture) {
          fragment = new WorldFragment(sx, sy, sz);
          VoxelSelection mask = new VoxelSelection(sx, sy, sz); mask.setAll();
          token = fragment.readFromWorldAsynchronous(world, origin.getX(), origin.getY(), origin.getZ(), mask);
        } else if (undo) {
          if (MinecraftForge.EVENT_BUS.post(new TemplatePlacementEvent(player, world, checkOrigin,
                  checkOrigin.add(checkX - 1, checkY - 1, checkZ - 1), "", true))) {
            throw new IllegalArgumentException("Undo denied by server protection.");
          }
          token = ServerSide.getWorldHistory().performComplexUndoAsynchronous(player, world, undoID);
          if (token == null) throw new IllegalArgumentException("Undo is no longer available.");
        } else {
          NBTTagCompound current = accessible(player, data.getString("Id"), false); revision(current, data);
          if (current.getBoolean("Deleted")) throw new IllegalArgumentException("Template unavailable.");
          if (MinecraftForge.EVENT_BUS.post(new TemplatePlacementEvent(player, world, checkOrigin,
                  checkOrigin.add(checkX - 1, checkY - 1, checkZ - 1), data.getString("Id")))) {
            throw new IllegalArgumentException("Placement denied by server protection.");
          }
          ResultWithReason backup = ServerSide.getSpeedyToolServerActions().prepareForTemplateAction(player);
          if (!backup.succeeded()) throw new IllegalArgumentException("World backup failed.");
          fragment = TemplateCodec.decode(data, includeAir);
          checkRestrictedBlocks(player, TemplateCodec.palette(data));
          QuadOrientation orientation = TemplateGeometry.orientation(data, origin, rotation, mirror);
          token = ServerSide.getWorldHistory().writeToWorldWithUndoAsynchronous(player, world, fragment,
                  0, origin.getY() - data.getInteger("AnchorY"), 0, orientation, new UniqueTokenID());
          if (token == null) throw new IllegalArgumentException("Another world operation is active.");
        }
      }
      token.setTimeOfInterrupt(deadline); token.continueProcessing();
      if (!token.isTaskComplete()) return false;
      if (capture) {
        if (player.isDead || player.getServerForPlayer() != world
                || !MinecraftServer.getServer().getConfigurationManager().playerEntityList.contains(player)) {
          throw new IllegalArgumentException("Capture discarded because the player left this world.");
        }
        requireBuilder(player);
        if (expectedRevision != 0) accessible(player, data.getString("Id"), true);
        TemplateCodec.encode(fragment, data);
        checkRestrictedBlocks(player, TemplateCodec.palette(data));
        if (downloadToken != null) {
          data.setInteger("Revision", 1);
          download(player, sessions.get(player.getUniqueID()), data, downloadToken);
        } else {
          store().commit(data, expectedRevision); invalidate(data.getString("Id"));
        }
      }
      return true;
    }
  }
}
