package speedytools.templates;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import speedytools.serverside.templates.TemplateCodec;
import speedytools.serverside.worldmanipulation.WorldFragment;

import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(RegistryTestRunner.class)
public class TemplateCodecTest
{
  @BeforeClass
  public static void initialiseBlocks()
  {
    net.minecraftforge.fml.common.Loader.injectData("11", "14", "3", "1450", "1.8", "9.10",
        new java.io.File("."), java.util.Collections.emptyList());
    Bootstrap.register();
  }

  private NBTTagCompound capture()
  {
    WorldFragment source = new WorldFragment(3, 1, 1);
    source.setBlockID(0, 0, 0, Block.getIdFromBlock(Blocks.oak_stairs)); source.setMetadata(0, 0, 0, 2);
    source.setBlockID(1, 0, 0, Block.getIdFromBlock(Blocks.chest)); source.setMetadata(1, 0, 0, 3);
    source.setBlockID(2, 0, 0, Block.getIdFromBlock(Blocks.air)); source.setMetadata(2, 0, 0, 0);
    NBTTagCompound tile = new NBTTagCompound(); tile.setString("id", "Chest"); tile.setString("CustomName", "Supplies");
    tile.setInteger("x", 100); tile.setInteger("y", 80); tile.setInteger("z", -30);
    source.setTileEntityData(1, 0, 0, tile);
    NBTTagCompound data = new NBTTagCompound(); data.setString("Id", UUID.randomUUID().toString());
    data.setString("Owner", UUID.randomUUID().toString()); data.setString("Name", "Test"); data.setInteger("Revision", 1);
    TemplateCodec.encode(source, data);
    return data;
  }

  @Test
  public void preservesNamedBlocksMetadataAndIndependentTileNbt()
  {
    NBTTagCompound data = capture(); WorldFragment restored = TemplateCodec.decode(data, true);
    assertEquals(Block.getIdFromBlock(Blocks.oak_stairs), restored.getBlockID(0, 0, 0));
    assertEquals(2, restored.getMetadata(0, 0, 0)); assertEquals(3, restored.getMetadata(1, 0, 0));
    NBTTagCompound tile = restored.getTileEntityData(1, 0, 0);
    assertEquals("Supplies", tile.getString("CustomName")); assertEquals(1, tile.getInteger("x"));
    tile.setString("CustomName", "Changed after loading");
    assertEquals("Supplies", data.getTagList("Tiles", 10).getCompoundTagAt(0).getCompoundTag("Data").getString("CustomName"));
  }

  @Test
  public void airModeChangesThePlacementMask()
  {
    NBTTagCompound data = capture();
    assertTrue(TemplateCodec.decode(data, true).getVoxel(2, 0, 0));
    assertFalse(TemplateCodec.decode(data, false).getVoxel(2, 0, 0));
    assertTrue(TemplateCodec.decode(data, false).getVoxel(1, 0, 0));
  }

  @Test(expected = IllegalArgumentException.class)
  public void missingRegistryEntryFailsInsteadOfPlacingAir()
  {
    NBTTagCompound data = capture();
    data.getTagList("Palette", 10).getCompoundTagAt(0).setString("Block", "missingmod:removed_block");
    TemplateCodec.decode(data, false);
  }
}
