package speedytools.common.templates;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

/** Posted on MinecraftForge.EVENT_BUS before any template blocks are changed.
 * Claim/protection mods can cancel the entire placement, including its air cells.
 */
@Cancelable
public class TemplatePlacementEvent extends Event
{
  public final EntityPlayerMP player;
  public final WorldServer world;
  public final BlockPos minimum;
  public final BlockPos maximum;
  public final String templateID;
  public final boolean undo;

  public TemplatePlacementEvent(EntityPlayerMP player, WorldServer world, BlockPos minimum, BlockPos maximum, String id)
  {
    this(player, world, minimum, maximum, id, false);
  }

  public TemplatePlacementEvent(EntityPlayerMP player, WorldServer world, BlockPos minimum, BlockPos maximum, String id, boolean undo)
  {
    this.player = player;
    this.world = world;
    this.minimum = minimum;
    this.maximum = maximum;
    this.templateID = id;
    this.undo = undo;
  }
}
