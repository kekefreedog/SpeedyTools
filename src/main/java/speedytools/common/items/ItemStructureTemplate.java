package speedytools.common.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import speedytools.SpeedyToolsMod;
import speedytools.common.templates.TemplateGeometry;

import java.util.List;

/** A single reusable blueprint item; large structure data stays on the server. */
public class ItemStructureTemplate extends Item
{
  public static final String NAME = "structuretemplate";

  public ItemStructureTemplate()
  {
    setMaxStackSize(1);
    setUnlocalizedName(NAME);
    setCreativeTab(CreativeTabs.tabTools);
  }

  @Override
  public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, BlockPos pos,
                           EnumFacing side, float hitX, float hitY, float hitZ)
  {
    if (world.isRemote) {
      if (player.isSneaking()) SpeedyToolsMod.proxy.openTemplateLibrary();
      else SpeedyToolsMod.proxy.templateClick(pos, side);
    }
    return true;
  }

  @Override
  public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
  {
    if (world.isRemote) SpeedyToolsMod.proxy.openTemplateLibrary();
    return stack;
  }

  @Override
  @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
  public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced)
  {
    NBTTagCompound t = stack.getTagCompound();
    boolean details = net.minecraft.client.gui.GuiScreen.isShiftKeyDown();
    if (t != null && t.hasKey("Id")) {
      if (t.getBoolean("Unavailable")) lines.add("Template unavailable");
      lines.add(t.getBoolean("Computer") ? "Storage: Computer" : "Storage: World");
      lines.add("Size: " + TemplateGeometry.dimensions(t));
      lines.add("Anchor: " + t.getInteger("AnchorX") + ", " + t.getInteger("AnchorY") + ", " + t.getInteger("AnchorZ"));
      if (details) {
        lines.add("Name: " + t.getString("Name"));
        lines.add("Owner: " + t.getString("OwnerName"));
        lines.add("Revision: " + t.getInteger("Revision"));
        if (t.getBoolean("Computer")) lines.add("Right click: import");
      }
    }
    lines.add("B: library");
    if (!details) { lines.add("Hold Shift for controls"); return; }
    lines.add("Right click: grab / release");
    lines.add("Hold 2s, release: build");
    lines.add("Left click: cancel");
    lines.add("R: rotate | V: mirror");
  }
}
