package speedytools.common.items;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import java.util.List;

/** Dedicated boundary-to-save tool; uses the template tool's server-authoritative workflow. */
public class ItemStructureCapture extends ItemStructureTemplate
{
  public static final String NAME = "structurecapture";
  public ItemStructureCapture() { setUnlocalizedName(NAME); setFull3D(); }

  @Override
  public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced)
  {
    lines.add("Right click: select two corners");
    lines.add("Then choose Save.");
    lines.add("B: library | C: cancel");
  }
}
