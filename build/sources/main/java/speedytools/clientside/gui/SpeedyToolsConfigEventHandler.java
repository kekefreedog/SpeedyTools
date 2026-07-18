package speedytools.clientside.gui;

import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import speedytools.SpeedyToolsMod;
import speedytools.common.SpeedyToolsOptions;

/**
 * Reloads SpeedyToolsOptions whenever the player closes the in-game mod options screen, so option
 * changes (and the momentary "reset backup throttle" checkbox) take effect immediately without a
 * restart. Client-only: never referenced from common/serverside code.
 */
public class SpeedyToolsConfigEventHandler
{
  @SubscribeEvent
  public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event)
  {
    if (event.modID.equals(SpeedyToolsMod.ID)) {
      SpeedyToolsOptions.reloadConfiguration();
    }
  }
}
