package speedytools.clientside.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import speedytools.SpeedyToolsMod;
import speedytools.common.SpeedyToolsOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod options screen shown from the Mods list "Config" button.
 * Client-only: never referenced from common/serverside code.
 */
public class SpeedyToolsConfigGui extends GuiConfig
{
  public SpeedyToolsConfigGui(GuiScreen parentScreen)
  {
    super(parentScreen, getConfigElements(), SpeedyToolsMod.ID, false, false,
            GuiConfig.getAbridgedConfigPath(SpeedyToolsOptions.getConfiguration().toString()));
  }

  private static List<IConfigElement> getConfigElements()
  {
    List<IConfigElement> configElements = new ArrayList<IConfigElement>();
    configElements.addAll(new ConfigElement(SpeedyToolsOptions.getConfiguration().getCategory(SpeedyToolsOptions.CATEGORY_BACKUP))
            .getChildElements());
    return configElements;
  }
}
