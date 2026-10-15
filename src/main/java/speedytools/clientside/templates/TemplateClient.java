package speedytools.clientside.templates;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import speedytools.clientside.rendering.SelectionBoxRenderer;
import speedytools.clientside.tools.SpeedyToolBoundary;
import speedytools.common.items.RegistryForItems;
import speedytools.common.templates.TemplateGeometry;
import speedytools.common.templates.TemplateNetwork;
import speedytools.common.utilities.Pair;
import speedytools.common.utilities.QuadOrientation;

/** Client-only selection and voxel preview. The server revalidates every action. */
public final class TemplateClient
{
  public static SpeedyToolBoundary boundary;
  public static BlockPos first, second, anchor;
  public static int selectionDimension;
  public static NBTTagCompound info;
  public static String status = "Select a boundary to save.";
  public static boolean includeAir;
  public static int rotation;
  public static boolean mirror;
  private static final KeyBinding library = new KeyBinding("key.template.library", Keyboard.KEY_B, "key.categories.speedytemplates");
  private static final KeyBinding rotate = new KeyBinding("key.template.rotate", Keyboard.KEY_R, "key.categories.speedytemplates");
  private static final KeyBinding flip = new KeyBinding("key.template.mirror", Keyboard.KEY_V, "key.categories.speedytemplates");
  private static final KeyBinding cancel = new KeyBinding("key.template.cancel", Keyboard.KEY_C, "key.categories.speedytemplates");
  private static WorldClient world;
  private static int mode, received, mesh;
  private static int[] states;
  private static String ticket = "", loadingId = "";
  private static boolean ready, dirty;
  private static BlockPos target, grabOrigin;
  private static Vec3 grabEye;
  private static boolean grabbed;
  private static ItemStack gestureItem;
  private static final TemplateGesture gesture = new TemplateGesture();
  private static final speedytools.clientside.userinput.PowerUpEffect power = new speedytools.clientside.userinput.PowerUpEffect();
  private static int animationTicks;
  private static long spinDownUntil;
  private static boolean submitted, buildSucceeded, mirrorBindingChecked;
  private static float buildPercent;
  private static final speedytools.clientside.rendering.RenderCursorStatus cursor = new speedytools.clientside.rendering.RenderCursorStatus(
      new speedytools.clientside.rendering.RenderCursorStatus.CursorRenderInfoUpdateLink() {
        public boolean refreshRenderInfo(speedytools.clientside.rendering.RenderCursorStatus.CursorRenderInfo state) {
          state.aSelectionIsDefined = ready && target != null;
          state.cursorType = speedytools.clientside.rendering.RenderCursorStatus.CursorRenderInfo.CursorType.COPY;
          state.chargePercent = (float)power.getPercentCompleted(); state.readinessPercent = ready ? 100 : 0;
          state.fullyChargedAndReady = gesture.charging() && state.chargePercent >= 100 && ready;
          state.taskCompletionPercent = buildPercent;
          state.animationState = gesture.charging()
              ? speedytools.clientside.rendering.RenderCursorStatus.CursorRenderInfo.AnimationState.SPIN_UP_CW
              : submitted ? speedytools.clientside.rendering.RenderCursorStatus.CursorRenderInfo.AnimationState.SPINNING_CW
              : System.nanoTime() < spinDownUntil
                  ? (buildSucceeded ? speedytools.clientside.rendering.RenderCursorStatus.CursorRenderInfo.AnimationState.SPIN_DOWN_CW_SUCCESS
                     : speedytools.clientside.rendering.RenderCursorStatus.CursorRenderInfo.AnimationState.SPIN_DOWN_CW_CANCELLED)
                  : speedytools.clientside.rendering.RenderCursorStatus.CursorRenderInfo.AnimationState.IDLE;
          return true;
        }
      });

  public static void initialise()
  {
    ClientRegistry.registerKeyBinding(library); ClientRegistry.registerKeyBinding(rotate);
    ClientRegistry.registerKeyBinding(flip); ClientRegistry.registerKeyBinding(cancel);
  }

  public static boolean holdingTool()
  {
    Minecraft mc = Minecraft.getMinecraft();
    return mc.thePlayer != null && mc.thePlayer.getHeldItem() != null
            && (mc.thePlayer.getHeldItem().getItem() == RegistryForItems.itemStructureTemplate
                || mc.thePlayer.getHeldItem().getItem() == RegistryForItems.itemStructureCapture);
  }

  public static void open()
  {
    if (holdingTool()) Minecraft.getMinecraft().displayGuiScreen(new TemplateLibraryScreen());
  }

  public static void selectCorners()
  {
    first = second = anchor = null; mode = 1; clearPreview();
    selectionDimension = Minecraft.getMinecraft().thePlayer.dimension;
    Minecraft.getMinecraft().displayGuiScreen(null);
    tell("Select two corners.");
  }

  public static void selectAnchor()
  {
    if (second == null) { tell("Select both corners first."); return; }
    mode = 3; Minecraft.getMinecraft().displayGuiScreen(null);
    tell("Select an anchor inside the boundary.");
  }

  public static void useBoundary()
  {
    Pair<BlockPos, BlockPos> corners = boundary == null ? null : boundary.getBoundaryCorners();
    if (corners == null) { tell("Create a boundary with the Arcane Claw first."); return; }
    first = corners.getFirst(); second = corners.getSecond();
    if (first == null || second == null) { tell("Complete both boundary corners first."); return; }
    anchor = minimum(); mode = 0;
    selectionDimension = Minecraft.getMinecraft().thePlayer.dimension;
    tell("Boundary selected.");
  }

  private static BlockPos minimum()
  {
    return new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
  }

  public static void click(BlockPos point, EnumFacing side)
  {
    if (!holdingTool()) return;
    if (mode == 1) { first = point; mode = 2; tell("Select the second corner."); return; }
    if (mode == 2) { second = point; anchor = minimum(); mode = 0; open(); return; }
    if (mode == 3) { anchor = point; mode = 0; open(); return; }
    ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
    if (held.getItem() == RegistryForItems.itemStructureCapture) {
      selectCorners(); first = point; mode = 2;
      tell("Select the second corner."); return;
    }
    if (held.hasTagCompound() && held.getTagCompound().getBoolean("Computer")) {
      try { ComputerTemplates.upload(held.getTagCompound(), true); }
      catch (java.io.IOException | RuntimeException ex) { tell(ex.getMessage()); }
      return;
    }
    if (!held.hasTagCompound() || !held.getTagCompound().hasKey("Id")) { open(); return; }
    BlockPos next = point.offset(side);
    String id = held.getTagCompound().getString("Id");
    if (info == null || !id.equals(info.getString("Id")) || !ready) {
      if (!id.equals(loadingId)) load(id);
      target = next;
      tell("Loading preview...");
      return;
    }
    if (target == null) {
      target = next; tell("Preview anchored.");
      return;
    }
    updateGrab(); grabbed = !grabbed;
    if (grabbed) { grabOrigin = target; grabEye = Minecraft.getMinecraft().thePlayer.getPositionEyes(1F); }
    tell(grabbed ? "Grabbed. Move to reposition." : "Preview fixed.");
  }

  private static boolean previewMatchesHeld()
  {
    if (!holdingTool() || !ready || info == null || target == null) return false;
    ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
    return held.getItem() == RegistryForItems.itemStructureTemplate && held.hasTagCompound()
        && !held.getTagCompound().getBoolean("Computer") && info.getString("Id").equals(held.getTagCompound().getString("Id"));
  }

  private static boolean place()
  {
    if (!previewMatchesHeld()) return false;
    updateGrab(); grabbed = false;
    NBTTagCompound request = TemplateNetwork.request("PLACE");
    request.setString("Ticket", ticket); request.setInteger("Rotation", rotation);
    request.setBoolean("Mirror", mirror); request.setBoolean("IncludeAir", includeAir);
    TemplateGeometry.putPosition(request, "Target", target);
    TemplateNetwork.send(request); ready = false; loadingId = ""; target = null; return true;
  }

  public static void load(String id)
  {
    clearPreview(); loadingId = id;
    NBTTagCompound request = TemplateNetwork.request("EQUIP"); request.setString("Id", id); TemplateNetwork.send(request);
  }

  public static void receive(NBTTagCompound message)
  {
    if (TemplateCatalogue.receive(message)) return;
    if (ComputerTemplates.receive(message)) return;
    String action = message.getString("Action");
    if ("BUILD_PROGRESS".equals(action)) {
      if (!submitted) return;
      buildPercent = Math.max(0, Math.min(100, message.getFloat("Progress")));
      if (message.getBoolean("Done")) {
        submitted = false; buildSucceeded = !message.getBoolean("Failed"); spinDownUntil = System.nanoTime() + 1500000000L;
      }
      return;
    }
    if ("STATUS".equals(action)) {
      tell(message.getString("Text"));
      if (message.getBoolean("Error")) { gesture.cancel(); power.abort(); submitted = false; buildSucceeded = false; spinDownUntil = System.nanoTime() + 700000000L; loadingId = ""; ready = false; target = null; }
    } else if ("INVALIDATE".equals(action)) {
      if (info != null && info.getString("Id").equals(message.getString("Id"))) {
        clearPreview(); tell("Template changed. Reload preview.");
      }
      if (Minecraft.getMinecraft().currentScreen instanceof TemplateLibraryScreen) {
        ((TemplateLibraryScreen)Minecraft.getMinecraft().currentScreen).refresh();
      }
    } else if ("BEGIN".equals(action)) {
      int count = message.getInteger("Count");
      if (count < 1 || count > 262144) return;
      NBTTagCompound incoming = message.getCompoundTag("Info");
      if (loadingId.isEmpty() || !loadingId.equals(incoming.getString("Id"))) return;
      try {
        if (TemplateGeometry.volume(incoming.getInteger("SizeX"), incoming.getInteger("SizeY"), incoming.getInteger("SizeZ"), 262144) != count) return;
      } catch (IllegalArgumentException ex) { clearPreview(); return; }
      clearMesh(); info = incoming;
      ticket = message.getString("Ticket"); states = new int[count]; received = 0; ready = false;
      loadingId = info.getString("Id");
    } else if ("CHUNK".equals(action) && ticket.equals(message.getString("Ticket")) && states != null) {
      int[] chunk = message.getIntArray("States"); int offset = message.getInteger("Offset");
      if (offset != received || chunk.length < 1 || offset + chunk.length > states.length) { clearPreview(); return; }
      System.arraycopy(chunk, 0, states, offset, chunk.length); received += chunk.length;
    } else if ("READY".equals(action) && ticket.equals(message.getString("Ticket")) && states != null && received == states.length) {
      ready = true; dirty = true; loadingId = "";
      tell("Preview ready: " + info.getString("Name") + ".");
    } else if ("LIST".equals(action) && Minecraft.getMinecraft().currentScreen instanceof TemplateLibraryScreen) {
      ((TemplateLibraryScreen)Minecraft.getMinecraft().currentScreen).receive(message);
    }
  }

  public static void tell(String text)
  {
    status = text;
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.thePlayer != null && !(mc.currentScreen instanceof TemplateLibraryScreen)) mc.thePlayer.addChatMessage(new ChatComponentText(text));
  }

  private static void clearMesh()
  {
    if (mesh != 0) GL11.glDeleteLists(mesh, 1);
    mesh = 0;
  }

  public static void clearPreview()
  {
    gesture.cancel(); power.abort(); grabbed = false; gestureItem = null;
    clearMesh(); info = null; states = null; target = null; ticket = ""; loadingId = ""; ready = false;
  }

  @SubscribeEvent
  public void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event)
  {
    if (event.itemStack.getItem() != RegistryForItems.itemStructureTemplate
        && event.itemStack.getItem() != RegistryForItems.itemStructureCapture) return;
    Minecraft mc = Minecraft.getMinecraft();
    int width = Math.min(160, Math.max(60, new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaledWidth() / 2 - 16));
    java.util.List<String> compact = new java.util.ArrayList<String>();
    for (int i = 0; i < event.toolTip.size(); ++i) {
      String line = event.toolTip.get(i);
      if (i == 0 && mc.fontRendererObj.getStringWidth(line) > width) {
        compact.add(mc.fontRendererObj.trimStringToWidth(line, width - mc.fontRendererObj.getStringWidth("...")) + "...");
      } else compact.addAll(mc.fontRendererObj.listFormattedStringToWidth(line, width));
    }
    event.toolTip.clear(); event.toolTip.addAll(compact);
  }

  @SubscribeEvent
  public void keys(InputEvent.KeyInputEvent event)
  {
    if (!holdingTool() || Minecraft.getMinecraft().currentScreen != null) return;
    if (library.isPressed()) open();
    if (rotate.isPressed()) { gesture.cancel(); power.abort(); rotation = (rotation + 1) & 3; dirty = true; }
    if (flip.isPressed()) { gesture.cancel(); power.abort(); mirror = !mirror; dirty = true; }
    if (cancel.isPressed()) cancelSelection();
  }

  @SubscribeEvent
  public void tick(TickEvent.ClientTickEvent event)
  {
    if (event.phase != TickEvent.Phase.END) return;
    WorldClient current = Minecraft.getMinecraft().theWorld;
    if (current != null && !mirrorBindingChecked) {
      mirrorBindingChecked = true;
      if (flip.getKeyCode() == Keyboard.KEY_M) {
        flip.setKeyCode(Keyboard.KEY_V); KeyBinding.resetKeyBindingArrayAndHash(); Minecraft.getMinecraft().gameSettings.saveOptions();
      }
    }
    if (current != world) {
      clearPreview(); first = second = anchor = null; mode = 0; world = current;
      rotation = 0; mirror = false; includeAir = false; ComputerTemplates.reset(); TemplateCatalogue.reset(); submitted = false;
    }
    ++animationTicks;
    if (!holdingTool() || Minecraft.getMinecraft().currentScreen != null
            || (gestureItem != null && gestureItem != Minecraft.getMinecraft().thePlayer.getHeldItem())) {
      gesture.cancel(); power.abort(); gestureItem = null; grabbed = false;
    }
    if (gesture.charging()) power.updateHolddownTime(gesture.held(System.nanoTime()));
    updateGrab();
    ComputerTemplates.tick(); TemplateCatalogue.tick();
  }

  private static void cancelSelection()
  {
    spinDownUntil = System.nanoTime() + 700000000L; submitted = false;
    clearPreview(); ComputerTemplates.reset(); first = second = anchor = null; mode = 0;
    tell("Preview cancelled.");
  }

  private static void updateGrab()
  {
    if (!grabbed || target == null || !holdingTool()) return;
    Vec3 eye = Minecraft.getMinecraft().thePlayer.getPositionEyes(1F);
    target = grabOrigin.add((int)Math.round(eye.xCoord - grabEye.xCoord),
        (int)Math.round(eye.yCoord - grabEye.yCoord), (int)Math.round(eye.zCoord - grabEye.zCoord));
  }

  @SubscribeEvent
  public void mouse(net.minecraftforge.client.event.MouseEvent event)
  {
    Minecraft mc = Minecraft.getMinecraft();
    if (!holdingTool() || mc.currentScreen != null) return;
    int attack = mc.gameSettings.keyBindAttack.getKeyCode() + 100;
    int use = mc.gameSettings.keyBindUseItem.getKeyCode() + 100;
    if (event.button == attack) {
      event.setCanceled(true);
      if (event.buttonstate) cancelSelection();
      return;
    }
    if (event.button != use) return;
    if (!event.buttonstate && gestureItem != mc.thePlayer.getHeldItem()) { gesture.cancel(); power.abort(); }
    event.setCanceled(true);
    long now = System.nanoTime();
    if (submitted) return;
    if (event.buttonstate) {
      gestureItem = mc.thePlayer.getHeldItem();
      boolean eligible = previewMatchesHeld() && mode == 0;
      gesture.press(now, eligible); buildSucceeded = false;
      if (eligible) power.initiate(now, now + speedytools.clientside.SpeedyToolsOptionsClient.getLongClickMinDurationNS(), 0);
    } else {
      TemplateGesture.Result result = gesture.release(now, speedytools.clientside.SpeedyToolsOptionsClient.getShortClickMaxDurationNS(),
          speedytools.clientside.SpeedyToolsOptionsClient.getLongClickMinDurationNS());
      if (!power.isIdle()) power.release(now); spinDownUntil = now + 700000000L;
      if (result == TemplateGesture.Result.PLACE) { submitted = place(); buildPercent = 0; }
      else if (result == TemplateGesture.Result.SHORT_CLICK) {
        if (mc.thePlayer.isSneaking()) open();
        else if (ready && target != null && mode == 0) click(target, EnumFacing.UP);
        else if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
          click(mc.objectMouseOver.getBlockPos(), mc.objectMouseOver.sideHit);
        } else open();
      }
      gestureItem = null;
    }
  }

  @SubscribeEvent
  public void overlay(net.minecraftforge.client.event.RenderGameOverlayEvent.Pre event)
  {
    if (!holdingTool() || Minecraft.getMinecraft().currentScreen != null
        || event.type != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;
    if (gesture.charging() || submitted || System.nanoTime() < spinDownUntil) {
      cursor.renderOverlay(event.resolution, animationTicks, event.partialTicks); event.setCanceled(true);
    }
  }

  @SubscribeEvent
  public void render(RenderWorldLastEvent event)
  {
    if (!holdingTool()) return;
    Minecraft mc = Minecraft.getMinecraft();
    double x = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks;
    double y = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks;
    double z = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks;
    GlStateManager.pushMatrix();
    GlStateManager.translate(-x, -y, -z);
    GlStateManager.disableTexture2D(); GlStateManager.disableLighting(); GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); GlStateManager.depthMask(false);
    try {
      if (first != null && second == null) drawAnchor(first);
      if (first != null && second != null) {
        BlockPos min = minimum();
        GlStateManager.color(0.3F, 0.9F, 1F, 0.8F);
        SelectionBoxRenderer.drawCube(new AxisAlignedBB(min.getX(), min.getY(), min.getZ(),
                Math.max(first.getX(), second.getX()) + 1, Math.max(first.getY(), second.getY()) + 1,
                Math.max(first.getZ(), second.getZ()) + 1));
      }
      if (anchor != null && target == null) drawAnchor(anchor);
      if (info != null && states != null && target != null && ready) {
        if (dirty || mesh == 0) buildMesh();
        GlStateManager.pushMatrix();
        GlStateManager.translate(target.getX(), target.getY(), target.getZ());
        GlStateManager.color(0.25F, 0.8F, 1F, 0.32F);
        GL11.glCallList(mesh);
        GlStateManager.popMatrix();
        BlockPos first = TemplateGeometry.point(info, target, rotation, mirror, 0);
        BlockPos last = TemplateGeometry.point(info, target, rotation, mirror, states.length - 1);
        GlStateManager.color(includeAir ? 1F : 0.3F, includeAir ? 0.35F : 0.9F, 0.9F, 0.9F);
        SelectionBoxRenderer.drawCube(new AxisAlignedBB(Math.min(first.getX(), last.getX()), first.getY(), Math.min(first.getZ(), last.getZ()),
                Math.max(first.getX(), last.getX()) + 1, last.getY() + 1, Math.max(first.getZ(), last.getZ()) + 1));
        drawAnchor(target);
      }
    } finally {
      GlStateManager.depthMask(true); GlStateManager.disableBlend(); GlStateManager.enableTexture2D();
      GlStateManager.enableLighting(); GlStateManager.color(1F, 1F, 1F, 1F); GlStateManager.popMatrix();
    }
  }

  private static void drawAnchor(BlockPos point)
  {
    GlStateManager.color(1F, 0.75F, 0.15F, 1F);
    SelectionBoxRenderer.drawCube(new AxisAlignedBB(point.getX() - 0.02, point.getY() - 0.02, point.getZ() - 0.02,
            point.getX() + 1.02, point.getY() + 1.02, point.getZ() + 1.02));
  }

  /** Surface voxels form a translucent ghost; fully enclosed voxels are omitted. */
  private static void buildMesh()
  {
    clearMesh(); mesh = GL11.glGenLists(1); dirty = false;
    int sx = info.getInteger("SizeX"), sz = info.getInteger("SizeZ");
    QuadOrientation q = TemplateGeometry.orientation(info, new BlockPos(0, 0, 0), rotation, mirror);
    GL11.glNewList(mesh, GL11.GL_COMPILE);
    Tessellator tessellator = Tessellator.getInstance(); WorldRenderer buffer = tessellator.getWorldRenderer();
    buffer.startDrawingQuads();
    for (int i = 0; i < states.length; ++i) {
      if (states[i] == 0) continue;
      int lx = i % sx, lz = i / sx % sz, ly = i / (sx * sz);
      // A cube is an intentionally lightweight approximation of stairs, fences and other partial blocks.
      boolean exposed = lx == 0 || lx == sx - 1 || lz == 0 || lz == sz - 1 || ly == 0 || i + sx * sz >= states.length
              || states[i - 1] == 0 || states[i + 1] == 0 || states[i - sx] == 0 || states[i + sx] == 0
              || states[i - sx * sz] == 0 || states[i + sx * sz] == 0;
      if (!exposed) continue;
      double x = q.calcWXfromXZ(lx, lz), y = ly - info.getInteger("AnchorY"), z = q.calcWZfromXZ(lx, lz);
      face(buffer, x,y,z, x,y+1,z, x+1,y+1,z, x+1,y,z);
      face(buffer, x,y,z+1, x+1,y,z+1, x+1,y+1,z+1, x,y+1,z+1);
      face(buffer, x,y,z, x,y,z+1, x,y+1,z+1, x,y+1,z);
      face(buffer, x+1,y,z, x+1,y+1,z, x+1,y+1,z+1, x+1,y,z+1);
      face(buffer, x,y,z, x+1,y,z, x+1,y,z+1, x,y,z+1);
      face(buffer, x,y+1,z, x,y+1,z+1, x+1,y+1,z+1, x+1,y+1,z);
    }
    tessellator.draw(); GL11.glEndList();
  }

  private static void face(WorldRenderer b, double x1,double y1,double z1, double x2,double y2,double z2,
                           double x3,double y3,double z3, double x4,double y4,double z4)
  {
    b.addVertex(x1,y1,z1); b.addVertex(x2,y2,z2); b.addVertex(x3,y3,z3); b.addVertex(x4,y4,z4);
  }
}
