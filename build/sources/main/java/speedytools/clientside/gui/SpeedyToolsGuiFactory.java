package speedytools.clientside.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Set;

/**
 * Registered as the mod's guiFactory (see @Mod on SpeedyToolsMod). This is what makes a "Config"
 * button appear next to the mod in the Mods list, opening SpeedyToolsConfigGui.
 * Client-only: never referenced from common/serverside code.
 */
public class SpeedyToolsGuiFactory implements IModGuiFactory
{
  @Override
  public void initialize(Minecraft minecraftInstance) {}

  @Override
  public Class<? extends GuiScreen> mainConfigGuiClass()
  {
    return SpeedyToolsConfigGui.class;
  }

  @Override
  public Set<RuntimeOptionCategoryElement> runtimeGuiCategories()
  {
    return null;
  }

  @Override
  public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element)
  {
    return null;
  }
}
