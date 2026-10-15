package speedytools.clientside.templates;

import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.lwjgl.input.Keyboard;
import speedytools.common.templates.TemplateGeometry;
import speedytools.common.templates.TemplateNetwork;

import java.io.IOException;
import java.util.*;

/** An inventory-style, paged library. Every management action targets an explicit ID and revision. */
public class TemplateLibraryScreen extends GuiScreen
{
  private final List<NBTTagCompound> entries = new ArrayList<NBTTagCompound>();
  private NBTTagCompound selected, pending;
  private GuiTextField search, name;
  private int left, top, panelWidth, cellWidth, page, total, requestSerial;
  private int filter;
  private static boolean computer;
  private boolean pendingComputer;
  private static final String[] FILTERS = {"Mine", "Shared", "All", "Trash"};
  private String lastQuery = "";

  @Override
  public void initGui()
  {
    Keyboard.enableRepeatEvents(true);
    panelWidth = Math.min(460, width - 12); cellWidth = (panelWidth - 12) / 8;
    left = (width - panelWidth) / 2; top = Math.max(4, (height - 236) / 2);
    String query = search == null ? "" : search.getText(), title = name == null ? "" : name.getText();
    search = new GuiTextField(0, fontRendererObj, left + 6, top + 20, panelWidth / 2 - 12, 16);
    name = new GuiTextField(1, fontRendererObj, left + panelWidth / 2 + 4, top + 20, panelWidth / 2 - 10, 16);
    search.setMaxStringLength(48); name.setMaxStringLength(48); search.setText(query); name.setText(title);
    buttonList.clear();
    String[] labels = {"Select corners", "Use boundary", "Set anchor", "Save new",
            "Equip", "Overwrite", "Rename", "Share / Private",
            "Delete", "Restore", "Previous", "Undo build",
            "Air: ignore", FILTERS[filter], "< Page", "Page >",
            "World", "Computer", "Copy to Computer"};
    int bw = (panelWidth - 12) / 4;
    for (int i = 0; i < labels.length; ++i) {
      buttonList.add(new GuiButton(i, left + 6 + (i % 4) * bw, top + 134 + (i / 4) * 17, (i == 18 ? bw * 2 : bw) - 3, 16, labels[i]));
    }
    refresh();
  }

  public void refresh()
  {
    if (search == null) return;
    if (computer) {
      try {
        List<NBTTagCompound> matches = new ArrayList<NBTTagCompound>();
        for (NBTTagCompound entry : ComputerTemplates.store().summaries()) {
          if (entry.getBoolean("Deleted") != "Trash".equals(FILTERS[filter])) continue;
          if (!entry.getString("Name").toLowerCase(Locale.ROOT).contains(search.getText().toLowerCase(Locale.ROOT))) continue;
          ComputerTemplates.thumbnail(entry); entry.setBoolean("CanEdit", true); matches.add(entry);
        }
        Collections.sort(matches, new Comparator<NBTTagCompound>() {
          public int compare(NBTTagCompound a, NBTTagCompound b) { return a.getString("Name").compareToIgnoreCase(b.getString("Name")); }
        });
        NBTTagCompound reply = new NBTTagCompound(); NBTTagList list = new NBTTagList();
        page = Math.max(0, Math.min(page, Math.max(0, (matches.size() - 1) / 24)));
        for (int i = page * 24; i < Math.min(matches.size(), (page + 1) * 24); ++i) list.appendTag(matches.get(i));
        reply.setInteger("Request", ++requestSerial); reply.setInteger("Page", page); reply.setInteger("Total", matches.size());
        reply.setTag("Entries", list); applyList(reply); lastQuery = search.getText();
      } catch (IOException | RuntimeException ex) { TemplateClient.tell("Cannot read Computer library: " + ex.getMessage()); }
      return;
    }
    NBTTagCompound request = TemplateNetwork.request("LIST");
    request.setString("Query", search.getText()); request.setString("Filter", FILTERS[filter]);
    request.setInteger("Page", page); request.setInteger("Request", ++requestSerial); TemplateNetwork.send(request);
    lastQuery = search.getText();
  }

  public void receive(NBTTagCompound message)
  {
    if (!computer) applyList(message);
  }

  private void applyList(NBTTagCompound message)
  {
    if (message.getInteger("Request") != requestSerial) return;
    entries.clear(); NBTTagList list = message.getTagList("Entries", 10);
    for (int i = 0; i < list.tagCount(); ++i) entries.add(list.getCompoundTagAt(i));
    page = message.getInteger("Page"); total = message.getInteger("Total");
    if (selected != null) {
      NBTTagCompound replacement = null;
      for (NBTTagCompound entry : entries) if (entry.getString("Id").equals(selected.getString("Id"))) replacement = entry;
      if (replacement == null || replacement.getInteger("Revision") != selected.getInteger("Revision")) {
        selected = null;
      } else selected = replacement;
    }
  }

  @Override
  protected void actionPerformed(GuiButton button) throws IOException
  {
    try {
      switch (button.id) {
        case 0: TemplateClient.selectCorners(); return;
        case 1: TemplateClient.useBoundary(); return;
        case 2: TemplateClient.selectAnchor(); return;
        case 3: save(false); return;
        case 4:
          if (selected != null) {
            if (computer) ComputerTemplates.upload(selected, true);
            else { TemplateClient.load(selected.getString("Id")); mc.displayGuiScreen(null); }
          }
          return;
        case 5: save(true); return;
        case 6: manage("RENAME", false); return;
        case 7: manage("SHARE", false); return;
        case 8: manage("DELETE", true); return;
        case 9: manage("RESTORE", false); return;
        case 10: manage(selected != null && selected.getBoolean("Deleted") ? "PURGE" : "PREVIOUS", true); return;
        case 11:
          pending = TemplateNetwork.request("UNDO"); pendingComputer = false;
          mc.displayGuiScreen(new GuiYesNo(this, "Undo your latest complex tool operation?", "This includes copies made with other SpeedyTools staffs.", 0));
          return;
        case 12: TemplateClient.includeAir = !TemplateClient.includeAir; return;
        case 13: filter = computer ? (filter == 3 ? 0 : 3) : (filter + 1) % FILTERS.length; page = 0; selected = null; refresh(); return;
        case 14: if (page > 0) { --page; selected = null; refresh(); } return;
        case 15: if ((page + 1) * 24 < total) { ++page; selected = null; refresh(); } return;
        case 16: case 17:
          computer = button.id == 17; filter = 0; page = 0; selected = null; entries.clear(); refresh(); return;
        case 18:
          if (selected != null) {
            if (computer) ComputerTemplates.upload(selected, false); else ComputerTemplates.export(selected);
          }
          return;
        default: return;
      }
    } catch (IOException | RuntimeException ex) { TemplateClient.tell(ex.getMessage()); }
  }

  private void save(boolean overwrite)
  {
    if (TemplateClient.first == null || TemplateClient.second == null || TemplateClient.anchor == null) {
      TemplateClient.tell("Select corners and anchor first."); return;
    }
    if (overwrite && selected == null) return;
    if (name.getText().trim().isEmpty()) { TemplateClient.tell("Enter a template name."); return; }
    NBTTagCompound request = TemplateNetwork.request("SAVE"); request.setString("Name", name.getText());
    request.setInteger("Dimension", TemplateClient.selectionDimension);
    TemplateGeometry.putPosition(request, "First", TemplateClient.first);
    TemplateGeometry.putPosition(request, "Second", TemplateClient.second);
    TemplateGeometry.putPosition(request, "Anchor", TemplateClient.anchor);
    if (overwrite) {
      request.setString("Id", selected.getString("Id")); request.setInteger("Revision", selected.getInteger("Revision"));
      pending = request; pendingComputer = computer;
      mc.displayGuiScreen(new GuiYesNo(this, "Overwrite " + selected.getString("Name") + "?", "The previous revision will be kept for recovery.", 0));
    } else dispatch(request, computer);
  }

  private void manage(String action, boolean confirm)
  {
    if (selected == null) return;
    NBTTagCompound request = TemplateNetwork.request(action);
    request.setString("Id", selected.getString("Id")); request.setInteger("Revision", selected.getInteger("Revision"));
    request.setString("Name", name.getText()); request.setBoolean("Shared", !selected.getBoolean("Shared"));
    if (confirm) {
      pending = request; pendingComputer = computer;
      String verb = "DELETE".equals(action) ? "Delete " : "PURGE".equals(action) ? "Permanently remove " : "Recover previous revision of ";
      String detail = "DELETE".equals(action) ? "Moves to Trash. Already-built structures stay in the world."
              : "PURGE".equals(action) ? "This removes the template and its recovery revision permanently."
              : "The current revision will become the recoverable previous revision.";
      mc.displayGuiScreen(new GuiYesNo(this, verb + selected.getString("Name") + "?", detail, 0));
    } else dispatch(request, computer);
  }

  @Override
  public void confirmClicked(boolean confirmed, int id)
  {
    NBTTagCompound request = pending; pending = null;
    mc.displayGuiScreen(this);
    if (confirmed && request != null) dispatch(request, pendingComputer);
  }

  private void dispatch(NBTTagCompound request, boolean local)
  {
    try {
      if (!local) TemplateNetwork.send(request);
      else if ("SAVE".equals(request.getString("Action"))) ComputerTemplates.capture(request);
      else ComputerTemplates.manage(request);
    } catch (IOException | RuntimeException ex) { TemplateClient.tell(ex.getMessage()); }
  }

  @Override
  protected void mouseClicked(int x, int y, int button) throws IOException
  {
    super.mouseClicked(x, y, button); search.mouseClicked(x, y, button); name.mouseClicked(x, y, button);
    int slot = slotAt(x, y);
    if (button == 0 && slot >= 0 && slot < entries.size()) {
      selected = entries.get(slot); name.setText(selected.getString("Name"));
    }
  }

  private int slotAt(int x, int y)
  {
    int localX = x - left - 6, localY = y - top - 40;
    if (localX < 0 || localX >= cellWidth * 8 || localY < 0 || localY >= 66) return -1;
    return localY / 22 * 8 + localX / cellWidth;
  }

  @Override
  protected void keyTyped(char key, int code) throws IOException
  {
    if (code == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
    if (code == Keyboard.KEY_TAB) { boolean focused = search.isFocused(); search.setFocused(!focused); name.setFocused(focused); return; }
    boolean changed = search.textboxKeyTyped(key, code); name.textboxKeyTyped(key, code);
    if (code == Keyboard.KEY_RETURN && !lastQuery.equals(search.getText())) { page = 0; refresh(); }
    else if (!changed && !name.isFocused()) super.keyTyped(key, code);
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTick)
  {
    drawDefaultBackground();
    drawRect(left, top, left + panelWidth, top + 236, 0xEE202731);
    drawString(fontRendererObj, "Template library: " + (computer ? "Computer" : "World"), left + 6, top + 4, 0xFFFFFF);
    drawString(fontRendererObj, "Search (Enter)", left + 6, top + 12, 0xAABBCD);
    drawString(fontRendererObj, "Template name", left + panelWidth / 2 + 4, top + 12, 0xAABBCD);
    search.drawTextBox(); name.drawTextBox();
    for (int i = 0; i < 24; ++i) {
      int x = left + 6 + (i % 8) * cellWidth, y = top + 40 + (i / 8) * 22;
      boolean active = i < entries.size() && selected != null && entries.get(i).getString("Id").equals(selected.getString("Id"));
      drawRect(x, y, x + cellWidth - 2, y + 20, active ? 0xFF41839F : 0xFF424C59);
      if (i < entries.size()) {
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(TemplateCatalogue.stack(entries.get(i), computer), x + (cellWidth - 16) / 2, y + 2);
        RenderHelper.disableStandardItemLighting();
      }
    }
    GlStateManager.disableLighting(); GlStateManager.disableDepth();
    String description = selected == null ? "Select a blueprint. " + total + " templates; page " + (page + 1)
            : selected.getString("Name") + " | " + TemplateGeometry.dimensions(selected) + " | r" + selected.getInteger("Revision");
    drawString(fontRendererObj, fontRendererObj.trimStringToWidth(description, panelWidth - 12), left + 6, top + 110, 0xFFFFFF);
    String detail = selected == null ? "Save new creates a separate template; Overwrite replaces the selected ID."
            : "Anchor " + selected.getInteger("AnchorX") + "," + selected.getInteger("AnchorY") + "," + selected.getInteger("AnchorZ")
            + " | " + selected.getString("OwnerName") + " | " + (computer ? "Computer" : selected.getBoolean("Shared") ? "Shared" : "Private");
    drawString(fontRendererObj, fontRendererObj.trimStringToWidth(detail, panelWidth - 12), left + 6, top + 121, 0xAABBCD);
    for (Object object : buttonList) {
      GuiButton button = (GuiButton)object;
      boolean edit = selected != null && selected.getBoolean("CanEdit"), deleted = selected != null && selected.getBoolean("Deleted");
      if (button.id >= 4 && button.id <= 10) button.enabled = button.id == 4 ? selected != null && !deleted
              : edit && (button.id == 10 || (button.id == 9 ? deleted : !deleted));
      if (button.id == 7 && computer) button.enabled = false;
      if (button.id == 4) button.displayString = computer ? "Import + Equip" : "Equip";
      if (button.id == 16) button.enabled = computer;
      if (button.id == 17) button.enabled = !computer;
      if (button.id == 18) { button.displayString = computer ? "Copy to World" : "Copy to Computer"; button.enabled = selected != null && !deleted; }
      if (button.id == 10) button.displayString = deleted ? "Purge" : "Previous";
      if (button.id == 2) button.enabled = TemplateClient.second != null;
      if (button.id == 12) button.displayString = TemplateClient.includeAir ? "Air: replace" : "Air: ignore";
      if (button.id == 13) button.displayString = computer && filter != 3 ? "All" : FILTERS[filter];
      if (button.id == 14) button.enabled = page > 0;
      if (button.id == 15) button.enabled = (page + 1) * 24 < total;
    }
    super.drawScreen(mouseX, mouseY, partialTick);
    drawString(fontRendererObj, fontRendererObj.trimStringToWidth(TemplateClient.status, panelWidth - 12), left + 6, top + 222, 0xEFC879);
    int slot = slotAt(mouseX, mouseY);
    if (slot >= 0 && slot < entries.size()) {
      NBTTagCompound entry = entries.get(slot);
      drawHoveringText(Arrays.asList(entry.getString("Name"), "Size X/Y/Z: " + TemplateGeometry.dimensions(entry),
              "Anchor: " + entry.getInteger("AnchorX") + ", " + entry.getInteger("AnchorY") + ", " + entry.getInteger("AnchorZ"),
              "Owner: " + entry.getString("OwnerName"), "Revision: " + entry.getInteger("Revision")), mouseX, mouseY);
    } else if (mouseY >= top + 219 && mouseY < top + 236 && mouseX >= left && mouseX < left + panelWidth) {
      drawHoveringText(fontRendererObj.listFormattedStringToWidth(TemplateClient.status, Math.min(280, width - 30)), mouseX, mouseY);
    }
    GlStateManager.enableDepth();
  }

  @Override
  public void updateScreen() { search.updateCursorCounter(); name.updateCursorCounter(); }

  @Override
  public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

  @Override
  public boolean doesGuiPauseGame() { return false; }
}
