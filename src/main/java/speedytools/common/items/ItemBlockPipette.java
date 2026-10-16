package speedytools.common.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.List;

/** Exchanges the held wand for the target's pick-block item, without breaking it. */
public class ItemBlockPipette extends Item
{
  public static final String NAME = "blockpipette";

  public ItemBlockPipette()
  {
    setMaxStackSize(1);
    setUnlocalizedName(NAME);
    setCreativeTab(CreativeTabs.tabTools);
    setFull3D();
  }

  @Override
  public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, BlockPos pos,
                                EnumFacing side, float hitX, float hitY, float hitZ)
  {
    // Let the client send its normal use packet. Only the server changes inventory.
    if (world.isRemote) return false;
    if (player.getHeldItem() != stack || world.isAirBlock(pos)) return true;

    MovingObjectPosition hit = new MovingObjectPosition(
        new Vec3(pos.getX() + hitX, pos.getY() + hitY, pos.getZ() + hitZ), side, pos);
    ItemStack picked = world.getBlockState(pos).getBlock().getPickBlock(hit, world, pos);
    if (picked == null || picked.getItem() == null || picked.stackSize <= 0) {
      player.addChatMessage(new ChatComponentText("This block has no item to sample."));
      return true;
    }

    ItemStack sample = picked.copy();
    sample.stackSize = 1;
    player.inventory.setInventorySlotContents(player.inventory.currentItem, sample);
    player.inventory.markDirty();
    player.inventoryContainer.detectAndSendChanges();
    return true;
  }

  @Override
  public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, BlockPos pos,
                           EnumFacing side, float hitX, float hitY, float hitZ)
  {
    return true;
  }

  @Override
  public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced)
  {
    lines.add("Right click a block to sample it.");
    lines.add("Replaces this wand with one block.");
    lines.add("The world block stays in place.");
  }
}
