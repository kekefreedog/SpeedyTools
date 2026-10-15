package speedytools.serverside.templates;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import speedytools.serverside.worldmanipulation.WorldFragment;

import java.util.*;

/** Versioned palette format uses registry names, so numeric registry IDs may change after restart. */
public final class TemplateCodec
{
  private TemplateCodec() {}

  public static void encode(WorldFragment fragment, NBTTagCompound data)
  {
    int sx = fragment.getxCount(), sy = fragment.getyCount(), sz = fragment.getzCount();
    data.setInteger("Format", 1);
    data.setInteger("SizeX", sx); data.setInteger("SizeY", sy); data.setInteger("SizeZ", sz);
    int[] cells = new int[sx * sy * sz];
    NBTTagList palette = new NBTTagList(), tiles = new NBTTagList();
    Map<String, Integer> indices = new HashMap<String, Integer>();
    int count = 0;
    for (int i = 0; i < cells.length; ++i) {
      int x = i % sx, z = i / sx % sz, y = i / (sx * sz);
      Block block = Block.getBlockById(fragment.getBlockID(x, y, z));
      String name = String.valueOf(Block.blockRegistry.getNameForObject(block));
      int meta = fragment.getMetadata(x, y, z);
      String key = name + ":" + meta;
      Integer index = indices.get(key);
      if (index == null) {
        index = palette.tagCount(); indices.put(key, index);
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Block", name); entry.setInteger("Meta", meta); palette.appendTag(entry);
      }
      cells[i] = index;
      if (!"minecraft:air".equals(name)) ++count;
      NBTTagCompound tile = fragment.getTileEntityData(x, y, z);
      if (tile != null) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("Cell", i);
        NBTTagCompound copy = (NBTTagCompound)tile.copy();
        copy.setInteger("x", x); copy.setInteger("y", y); copy.setInteger("z", z);
        entry.setTag("Data", copy); tiles.appendTag(entry);
      }
    }
    data.setTag("Palette", palette); data.setIntArray("Blocks", cells); data.setTag("Tiles", tiles);
    data.setInteger("BlockCount", count);
    data.setByteArray("Thumbnail", thumbnail(data));
  }

  public static byte[] thumbnail(NBTTagCompound data)
  {
    IBlockState[] states = palette(data);
    byte[] colours = new byte[states.length];
    for (int i = 0; i < states.length; ++i) {
      if (states[i].getBlock() == net.minecraft.init.Blocks.air) continue;
      net.minecraft.block.material.MapColor colour = states[i].getBlock().getMapColor(states[i]);
      colours[i] = (byte)(1 + (colour == null ? 11 : colour.colorIndex));
    }
    return speedytools.common.templates.TemplateThumbnail.sample(data.getInteger("SizeX"), data.getInteger("SizeY"),
            data.getInteger("SizeZ"), data.getIntArray("Blocks"), colours);
  }

  public static IBlockState[] palette(NBTTagCompound data)
  {
    NBTTagList entries = data.getTagList("Palette", 10);
    IBlockState[] result = new IBlockState[entries.tagCount()];
    for (int i = 0; i < result.length; ++i) {
      NBTTagCompound entry = entries.getCompoundTagAt(i);
      ResourceLocation name = new ResourceLocation(entry.getString("Block"));
      if (!Block.blockRegistry.containsKey(name)) throw new IllegalArgumentException("Missing block: " + name);
      Block block = (Block)Block.blockRegistry.getObject(name);
      result[i] = block.getStateFromMeta(entry.getInteger("Meta"));
    }
    return result;
  }

  public static WorldFragment decode(NBTTagCompound data, boolean includeAir)
  {
    TemplateStore.validate(data);
    int sx = data.getInteger("SizeX"), sy = data.getInteger("SizeY"), sz = data.getInteger("SizeZ");
    WorldFragment result = new WorldFragment(sx, sy, sz);
    IBlockState[] palette = palette(data);
    int[] cells = data.getIntArray("Blocks");
    for (int i = 0; i < cells.length; ++i) {
      IBlockState state = palette[cells[i]];
      if (!includeAir && state.getBlock() == net.minecraft.init.Blocks.air) continue;
      int x = i % sx, z = i / sx % sz, y = i / (sx * sz);
      result.setBlockID(x, y, z, Block.getIdFromBlock(state.getBlock()));
      result.setMetadata(x, y, z, state.getBlock().getMetaFromState(state));
    }
    NBTTagList tiles = data.getTagList("Tiles", 10);
    for (int i = 0; i < tiles.tagCount(); ++i) {
      NBTTagCompound entry = tiles.getCompoundTagAt(i);
      int cell = entry.getInteger("Cell"), x = cell % sx, z = cell / sx % sz, y = cell / (sx * sz);
      if (result.getVoxel(x, y, z)) result.setTileEntityData(x, y, z, (NBTTagCompound)entry.getCompoundTag("Data").copy());
    }
    return result;
  }
}
