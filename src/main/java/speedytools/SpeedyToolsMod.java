package speedytools;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import speedytools.common.CommonProxy;


@Mod(modid="speedytoolsmod", name="Build Faster Mod", version="4.6.5", acceptableRemoteVersions="[" + SpeedyToolsMod.VERSION + "]", guiFactory="speedytools.clientside.gui.SpeedyToolsGuiFactory")
public class SpeedyToolsMod {
  public static final String ID = "speedytoolsmod";
  public static final String VERSION = "4.6.5";

  // The instance of your mod that Forge uses.
  @Mod.Instance("speedytoolsmod")
  public static speedytools.SpeedyToolsMod instance;

  // Says where the client and server 'proxy' code is loaded.
  @SidedProxy(clientSide="speedytools.clientside.CombinedClientProxy", serverSide="speedytools.serverside.DedicatedServerProxy")
  public static CommonProxy proxy;

  @Mod.EventHandler
  public void preInit(FMLPreInitializationEvent event) {
    proxy.preInit(event);
  }

  @Mod.EventHandler
  public void load(FMLInitializationEvent event) {
    proxy.load();
  }

  @Mod.EventHandler
  public void postInit(FMLPostInitializationEvent event) {
    proxy.postInit();
  }

  @Mod.EventHandler
  public void serverStarting(net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent event) {
    speedytools.serverside.templates.TemplateServer.reset();
  }

  @Mod.EventHandler
  public void serverStopping(net.minecraftforge.fml.common.event.FMLServerStoppingEvent event) {
    speedytools.serverside.templates.TemplateServer.stopping();
  }

  @Mod.EventHandler
  public void serverStopped(net.minecraftforge.fml.common.event.FMLServerStoppedEvent event) {
    speedytools.serverside.templates.TemplateServer.reset();
  }

  /**
   * Prepend the name with the mod ID, suitable for textures.
   * @param name
   * @return eg "speedytoolsmod:myblockname"
   */
  public static String prependModID(String name) {return ID + ":" + name;}

}
