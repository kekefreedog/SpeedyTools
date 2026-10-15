package speedytools.templates;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import speedytools.common.templates.TemplateGeometry;
import speedytools.common.utilities.BlockRotateFlipHelper;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RegistryTestRunner.class)
public class TemplateRailTest
{
  // Physical connection endpoints; ascending directions identify the raised end.
  private static final int[][][] ENDS = {
      {{0,0,-1},{0,0,1}}, {{-1,0,0},{1,0,0}},
      {{-1,0,0},{1,1,0}}, {{-1,1,0},{1,0,0}},
      {{0,1,-1},{0,0,1}}, {{0,0,-1},{0,1,1}},
      {{0,0,1},{1,0,0}}, {{0,0,1},{-1,0,0}},
      {{0,0,-1},{-1,0,0}}, {{0,0,-1},{1,0,0}}
  };

  @BeforeClass public static void bootstrap() { TemplateCodecTest.initialiseBlocks(); }

  @Test
  public void allRailConnectionsFollowTemplateRotationAndMirror()
  {
    NBTTagCompound info = new NBTTagCompound();
    info.setInteger("SizeX", 3); info.setInteger("SizeY", 2); info.setInteger("SizeZ", 3);
    info.setInteger("AnchorX", 1); info.setInteger("AnchorZ", 1);
    BlockPos anchor = new BlockPos(120, 65, -200);
    Block[] rails = {Blocks.rail, Blocks.golden_rail, Blocks.detector_rail, Blocks.activator_rail};
    for (Block rail : rails) for (int shape = 0; shape < (rail == Blocks.rail ? 10 : 6); ++shape) {
      for (int powered : new int[] {0, 8}) {
        if (rail == Blocks.rail && powered != 0) continue;
        for (boolean mirror : new boolean[] {false, true}) for (int rotation = 0; rotation < 4; ++rotation) {
          int metadata = shape | powered, id = Block.getIdFromBlock(rail);
          if (mirror) metadata = BlockRotateFlipHelper.flip(id, metadata, BlockRotateFlipHelper.FlipDirection.WEST_EAST);
          for (int r = 0; r < rotation; ++r) metadata = BlockRotateFlipHelper.rotate90(id, metadata);
          Set<BlockPos> expected = new HashSet<BlockPos>(), actual = new HashSet<BlockPos>();
          for (int[] end : ENDS[shape]) {
            int index = end[0]+1 + 3*(end[2]+1) + 9*end[1];
            expected.add(TemplateGeometry.point(info, anchor, rotation, mirror, index));
          }
          for (int[] end : ENDS[rail == Blocks.rail ? metadata : metadata & 7]) actual.add(anchor.add(end[0], end[1], end[2]));
          assertEquals("Rail connections must move with the structure", expected, actual);
          if (rail != Blocks.rail) assertEquals(powered, metadata & 8);
        }
      }
    }
  }

  @Test
  public void northSouthMirrorReflectsConnectionsAndDoubleMirrorRestoresShape()
  {
    int id = Block.getIdFromBlock(Blocks.rail);
    for (int shape = 0; shape < 10; ++shape) {
      int reflected = BlockRotateFlipHelper.flip(id, shape, BlockRotateFlipHelper.FlipDirection.NORTH_SOUTH);
      Set<BlockPos> expected = new HashSet<BlockPos>(), actual = new HashSet<BlockPos>();
      for (int[] end : ENDS[shape]) expected.add(new BlockPos(end[0], end[1], -end[2]));
      for (int[] end : ENDS[reflected]) actual.add(new BlockPos(end[0], end[1], end[2]));
      assertEquals(expected, actual);
      assertEquals(shape, BlockRotateFlipHelper.flip(id, reflected, BlockRotateFlipHelper.FlipDirection.NORTH_SOUTH));
    }
  }

  @Test
  public void fourQuarterTurnsRestoreEveryRailShape()
  {
    for (int shape = 0; shape < 10; ++shape) {
      net.minecraft.block.state.IBlockState state = Blocks.rail.getStateFromMeta(shape);
      for (int r = 0; r < 4; ++r) state = BlockRotateFlipHelper.rotate90(state);
      assertEquals(shape, Blocks.rail.getMetaFromState(state));
    }
  }
}
