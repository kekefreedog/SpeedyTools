package speedytools.common.utilities;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for rotating / flipping blocks
 * Authored by TheGreyGhost 17 Mar 2015
 */

public final class BlockRotateFlipHelper
{

  public enum FlipDirection
  {
    NORTH_SOUTH,
    WEST_EAST,
  }

  private BlockRotateFlipHelper() {
  }

  /**
   * Rotate a block by 90 degrees clockwise(north->east->south->west->north);
   * Transforms rail SHAPE or a block's FACING property; other blocks are unaffected.
   * @param iBlockState the starting blockstate
   * @return the rotated blockstate
   */
  public static IBlockState rotate90(IBlockState iBlockState)
  {
    if (iBlockState.getBlock() instanceof BlockRailBase) return transformRail(iBlockState, RAIL_CLOCKWISE);
    PropertyDirection propertyDirection = getPropertyDirection(iBlockState);
    if (propertyDirection == null) return iBlockState; // block doesn't have this property

    EnumFacing currentFacing = (EnumFacing)iBlockState.getValue(propertyDirection);
    int currentHorizontalIndex = currentFacing.getHorizontalIndex();
    if (currentHorizontalIndex < 0) return iBlockState;  // points up or down

    int newHorizontalIndex  = (currentHorizontalIndex + 1) & 3;
    EnumFacing newFacing = EnumFacing.getHorizontal(newHorizontalIndex);
    return iBlockState.withProperty(propertyDirection, newFacing);
  }

  /**
   * Rotate a block by 90 degrees clockwise(north->east->south->west->north);
   * Transforms rail SHAPE or a block's FACING property; other blocks are unaffected.
   * @param blockID the internal ID for this block
   * @param metadata the metadata value for this block
   * @return the rotated metadata value
   */
  public static int rotate90(int blockID, int metadata)
  {
    Boolean hasPropertyDirection = hasPropertyDirectionCache.get(blockID);
    if (hasPropertyDirection == null) {
      hasPropertyDirection = setPropertyDirectionCache(blockID, metadata);
    }
    if (!hasPropertyDirection) {
      return metadata;
    }

    Block block = Block.getBlockById(blockID);
    IBlockState iBlockState = block.getStateFromMeta(metadata);
    iBlockState = rotate90(iBlockState);
    return block.getMetaFromState(iBlockState);
  }

  /**
   * Mirror image a block
   * Can't do this perfectly because blocks only have a facing, not a left/right mirror image.
   * Make the assumption that a block typically has left/right symmetry in the direction it is facing
   * eg steps when facing EAST are north/south symmetrical but not east-west symmetrical.
   * Hence, if the steps are facing east or west, and you flip in the EAST_WEST direction, it is the same as rotating
   * the steps twice by 90 degrees.
   * If the steps are facing north or south, and you flip in the EAST_WEST direction, then nothing happens.
   * @param blockID the internal ID of this block
   * @param metadata the starting metadata of the block
   * @param flipDirection the axis of flipping - eg EAST_WEST means that the east becomes west
   * @return the flipped blockstate
   */
  public static int flip(int blockID, int metadata, FlipDirection flipDirection)
  {
    Boolean hasPropertyDirection = hasPropertyDirectionCache.get(blockID);
    if (hasPropertyDirection == null) {
      hasPropertyDirection = setPropertyDirectionCache(blockID, metadata);
    }
    if (!hasPropertyDirection) {
      return metadata;
    }

    Block block = Block.getBlockById(blockID);
    IBlockState iBlockState = block.getStateFromMeta(metadata);
    iBlockState = flip(iBlockState, flipDirection);
    return block.getMetaFromState(iBlockState);
  }

  /**
   * Mirror image a block
   * Can't do this perfectly because blocks only have a facing, not a left/right mirror image.
   * Make the assumption that a block typically has left/right symmetry in the direction it is facing
   * eg steps when facing EAST are north/south symmetrical but not east-west symmetrical.
   * Hence, if the steps are facing east or west, and you flip in the EAST_WEST direction, it is the same as rotating
   * the steps twice by 90 degrees.
   * If the steps are facing north or south, and you flip in the EAST_WEST direction, then nothing happens.
   * @param iBlockState the starting blockstate
   * @param flipDirection the axis of flipping - eg EAST_WEST means that the east becomes west
   * @return the flipped blockstate
   */
  public static IBlockState flip(IBlockState iBlockState, FlipDirection flipDirection)
  {
    if (iBlockState.getBlock() instanceof BlockRailBase) return transformRail(iBlockState,
        flipDirection == FlipDirection.WEST_EAST ? RAIL_MIRROR_X : RAIL_MIRROR_Z);
    PropertyDirection propertyDirection = getPropertyDirection(iBlockState);
    if (propertyDirection == null) return iBlockState; // block doesn't have this property

    EnumFacing currentFacing = (EnumFacing)iBlockState.getValue(propertyDirection);

    if (   flipDirection == FlipDirection.WEST_EAST && (currentFacing == EnumFacing.EAST || currentFacing == EnumFacing.WEST)
        || flipDirection == FlipDirection.NORTH_SOUTH && (currentFacing == EnumFacing.SOUTH || currentFacing == EnumFacing.NORTH )) {
      return  rotate90(rotate90(iBlockState));
    } else {
      return iBlockState;
    }
  }

  // EnumRailDirection metadata order: NS, EW, ascending E/W/N/S, curves SE/SW/NW/NE.
  // WorldFragment mirrors X first, then rotates clockwise, exactly like its voxel coordinates.
  private static final int[] RAIL_CLOCKWISE = {1, 0, 5, 4, 2, 3, 7, 8, 9, 6};
  private static final int[] RAIL_MIRROR_X = {0, 1, 3, 2, 4, 5, 7, 6, 9, 8};
  private static final int[] RAIL_MIRROR_Z = {0, 1, 2, 3, 5, 4, 9, 8, 7, 6};

  private static IBlockState transformRail(IBlockState state, int[] mapping)
  {
    IProperty shape = ((BlockRailBase)state.getBlock()).getShapeProperty();
    Object value = state.getValue(shape);
    // Custom rails may define a different shape enum; do not reinterpret their metadata.
    if (!(value instanceof BlockRailBase.EnumRailDirection)) return state;
    BlockRailBase.EnumRailDirection direction = (BlockRailBase.EnumRailDirection)value;
    // Only SHAPE changes: powered/detector/activator flags remain intact.
    return state.withProperty(shape, BlockRailBase.EnumRailDirection.byMetadata(mapping[direction.getMetadata()]));
  }

  // retrieves the PropertyDirection for the given block, or null if it doesn't have one.
  //  uses caching
  private static PropertyDirection getPropertyDirection(IBlockState iBlockState) {
    PropertyDirection propertyDirection = null;

    if (!propertyDirectionCache.containsKey(iBlockState.getBlock())) {
      for (Object property : iBlockState.getProperties().keySet()) {
        if (property instanceof PropertyDirection) {
          propertyDirection = (PropertyDirection) property;
          break;
        }
      }
      propertyDirectionCache.put(iBlockState.getBlock(), propertyDirection);
    } else {
      propertyDirection = propertyDirectionCache.get(iBlockState.getBlock());
    }
    return propertyDirection;
  }

  // Caches support for a direction property or rail shape.
  private static boolean setPropertyDirectionCache(int blockID, int metadata)
  {
    Block block = Block.getBlockById(blockID);
    IBlockState iBlockState = block.getStateFromMeta(metadata);
    boolean hasPropertyDirection = block instanceof BlockRailBase;
    for (Object property : iBlockState.getProperties().keySet()) {
      if (property instanceof PropertyDirection) {
        hasPropertyDirection = true;
        break;
      }
    }
    hasPropertyDirectionCache.put(blockID, hasPropertyDirection);
    return hasPropertyDirection;
  }

  private static Map<Block, PropertyDirection> propertyDirectionCache = new HashMap<Block, PropertyDirection>();
  // cache of the direction property for each block; null = block has none
  private static Map<Integer, Boolean> hasPropertyDirectionCache = new HashMap<Integer, Boolean>();

}

