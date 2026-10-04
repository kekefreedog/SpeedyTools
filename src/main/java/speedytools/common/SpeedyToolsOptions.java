package speedytools.common;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import speedytools.serverside.actions.SpeedyToolServerActions;

import java.io.File;

/**
 * User: The Grey Ghost
 * Date: 10/03/14
 */
public class SpeedyToolsOptions
{
  private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("speedytoolsmod.debug", "false"));

  public static final String CATEGORY_BACKUP = "backup";
  public static final String PROP_AUTO_BACKUP_ENABLED = "autoWorldBackupEnabled";
  public static final String PROP_RESET_BACKUP_THROTTLE = "resetBackupThrottleNow";

  private static Configuration configuration;
  private static boolean autoWorldBackupEnabled = false;

  /**
   * Creates (or loads) the mod's config file. Called once from CommonProxy::preInit with the
   * file FML suggests for this mod.
   */
  public static void initialiseConfiguration(File configFile)
  {
    configuration = new Configuration(configFile);
    configuration.load();
    reloadConfiguration();
  }

  /** The Configuration backing the in-game mod options screen (see clientside.gui.SpeedyToolsConfigGui). */
  public static Configuration getConfiguration()
  {
    return configuration;
  }

  /**
   * Re-reads option values from the Configuration object. Called on load, and again whenever the
   * player closes the in-game mod options screen (see clientside.gui.SpeedyToolsConfigEventHandler).
   * Also consumes the momentary "reset backup throttle" checkbox: if it was ticked, forces the next
   * clone-tool use to take a fresh backup regardless of the usual cooldown, then unticks itself.
   */
  public static void reloadConfiguration()
  {
    if (configuration == null) return;

    autoWorldBackupEnabled = configuration.getBoolean(PROP_AUTO_BACKUP_ENABLED, CATEGORY_BACKUP, false,
            "Automatically back up the world save before a clone tool (copy/move/delete/fill) is used. "
          + "Disabled by default. Enable this to protect your world if a tool goes wrong.");

    Property resetProperty = configuration.get(CATEGORY_BACKUP, PROP_RESET_BACKUP_THROTTLE, false,
            "Check this box and click Done to force a fresh world backup next time a clone tool is used, "
          + "ignoring the usual cooldown between backups. Automatically unchecks itself once applied.");

    if (resetProperty.getBoolean()) {
      SpeedyToolServerActions.resetBackupThrottle();
      resetProperty.set(false);
    }

    if (configuration.hasChanged()) {
      configuration.save();
    }
  }

  // if true - automatically back up the world save before a clone tool action is performed
  public static boolean getAutoWorldBackupEnabled() { return autoWorldBackupEnabled; }

  // speed for double-clicking with the mouse
  public static int getDoubleClickSpeedMS() {
    return 200;
  }

  // the maximum number of undo for the simple speedy tools
  public static int getMaxSimpleToolUndoCount() {return 5;}

  // the maximum number of undo for the complex speedy tools
  public static int getMaxComplexToolUndoCount() {return 5;}

  // if true - enabled the in-game testing tools
  public static boolean getTesterToolsEnabled() { return DEBUG;}

  // The length of maximum length of time per tick we will use for asynchronous tasks on the server
  public static long getMaxServerBusyTimeMS() {return 25;}

  // The length of maximum length of time per tick we will use for selection generation on the server
  public static long getMaxServerSelGenTimeMS() {return 25;}

  // The packet size of the fragments to use when sending a Selection to/from the server
  public static int getSelectionPacketFragmentSize() {return 3000;}

  // if true - logging of the network activity is active
  public static boolean getNetworkLoggingActive() { return false;}  // doesn't work any more
  public static File getNetworkLoggingDirectory()
  {
    return null;
//    if (Minecraft.getMinecraft() == null) return null;
//    return Minecraft.getMinecraft().mcDataDir;
  }
  public static int getNetworkLoggingPeriodInTicks() {return 20 * 10;}

  public static String nameForSavesBackupFolder() { return "build-faster-backups";}

}
