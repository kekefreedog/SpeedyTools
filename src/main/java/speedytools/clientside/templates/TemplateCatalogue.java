package speedytools.clientside.templates;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import speedytools.clientside.ClientSide;
import speedytools.common.items.RegistryForItems;
import speedytools.common.templates.TemplateNetwork;
import speedytools.serverside.templates.TemplateCodec;
import java.util.*;
import java.lang.reflect.Method;

/** Read-only creative catalogue; item NBT never grants server access or placement permission. */
public final class TemplateCatalogue
{
  private static final List<NBTTagCompound> world = new ArrayList<NBTTagCompound>();
  private static final List<NBTTagCompound> incoming = new ArrayList<NBTTagCompound>();
  private static int tick, page, serial, waitingSince;
  private static boolean waiting, wasOpen;
  private static List<NBTTagCompound> local = new ArrayList<NBTTagCompound>();

  public static ItemStack stack(NBTTagCompound info, boolean computer)
  {
    ItemStack item = new ItemStack(RegistryForItems.itemStructureTemplate);
    NBTTagCompound tag = (NBTTagCompound)info.copy(); tag.setBoolean("Computer", computer);
    item.setTagCompound(tag); item.setStackDisplayName(info.getString("Name") + (computer ? " [Computer]" : " [World]"));
    return item;
  }

  public static void append(List items)
  {
    for (NBTTagCompound info : world) items.add(stack(info, false));
    for (NBTTagCompound info : local) items.add(stack(info, true));
  }

  public static void reset()
  {
    world.clear(); incoming.clear(); local.clear(); waiting = false; wasOpen = false; page = 0; ++serial;
  }

  public static void tick()
  {
    ++tick;
    Minecraft mc = Minecraft.getMinecraft();
    boolean open = mc.currentScreen instanceof GuiContainerCreative;
    if (waiting && tick - waitingSince > 100) { waiting = false; page = 0; incoming.clear(); }
    if (open && !waiting && (!wasOpen || tick % 100 == 0 || page > 0) && tick % 10 == 0) {
      if (page == 0) {
        incoming.clear();
        try {
          List<NBTTagCompound> current = new ArrayList<NBTTagCompound>();
          for (NBTTagCompound info : ComputerTemplates.store().summaries()) if (!info.getBoolean("Deleted")) {
            ComputerTemplates.thumbnail(info);
            current.add(info);
          }
          if (!current.equals(local)) { local = current; refresh(); }
        } catch (java.io.IOException ex) { TemplateClient.tell("Cannot read Computer library: " + ex.getMessage()); }
      }
      NBTTagCompound request = TemplateNetwork.request("CATALOGUE"); request.setInteger("Page", page);
      request.setInteger("Request", ++serial); TemplateNetwork.send(request); waiting = true; waitingSince = tick; wasOpen = true;
    }
    if (!open) wasOpen = false;
  }

  public static boolean receive(NBTTagCompound message)
  {
    if (!"CATALOGUE".equals(message.getString("Action"))) return false;
    if (!waiting || message.getInteger("Request") != serial) return true;
    waiting = false;
    if (message.getInteger("Page") != page) { page = 0; incoming.clear(); return true; }
    NBTTagList entries = message.getTagList("Entries", 10);
    for (int i = 0; i < entries.tagCount(); ++i) incoming.add(entries.getCompoundTagAt(i));
    if (++page * 8 >= Math.min(8192, message.getInteger("Total"))) {
      page = 0;
      if (!world.equals(incoming)) { world.clear(); world.addAll(incoming); refresh(); }
    }
    return true;
  }

  private static void refresh()
  {
    Minecraft mc = Minecraft.getMinecraft();
    if (!(mc.currentScreen instanceof GuiContainerCreative)) return;
    GuiContainerCreative gui = (GuiContainerCreative)mc.currentScreen;
    if (gui.getSelectedTabIndex() != ClientSide.tabSpeedyTools.getTabIndex()) return;
    // This pinned Minecraft version has one private tab-selection method taking CreativeTabs.
    // Match its signature so both MCP and reobfuscated runtime names work.
    for (Method method : GuiContainerCreative.class.getDeclaredMethods()) {
      if (java.lang.reflect.Modifier.isPrivate(method.getModifiers()) && method.getReturnType() == Void.TYPE
              && Arrays.equals(method.getParameterTypes(), new Class<?>[] {CreativeTabs.class})) {
        try { method.setAccessible(true); method.invoke(gui, ClientSide.tabSpeedyTools); }
        catch (ReflectiveOperationException ex) { TemplateClient.tell("Reopen Build Faster to refresh."); }
        return;
      }
    }
  }
}
