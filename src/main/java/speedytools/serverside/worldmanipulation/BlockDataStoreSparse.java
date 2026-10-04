package speedytools.serverside.worldmanipulation;

import java.util.HashMap;

/**
 * User: The Grey Ghost
 * Date: 20/07/2014
 * Stores block IDs in bits 0-31, metadata in bits 32-35, and light in bits 36-43.
 */
public class BlockDataStoreSparse implements BlockDataStore
{
  public BlockDataStoreSparse(int i_xcount, int i_ycount, int i_zcount, int estimatedElementsUsed)
  {
    xCount = i_xcount;
    yCount = i_ycount;
    zCount = i_zcount;
    sparseData = new HashMap<Integer, Long>(estimatedElementsUsed);
  }
  /**
   * gets the blockID at a particular location.
   * error if the location is not stored in this fragment
   *
   * @param x x position relative to the block origin [0,0,0]
   * @param y y position relative to the block origin [0,0,0]
   * @param z z position relative to the block origin [0,0,0]
   */
  @Override
  public int getBlockID(int x, int y, int z) {
    assert (x >= 0 && x < xCount);
    assert (y >= 0 && y < yCount);
    assert (z >= 0 && z < zCount);
    final int offset = y * xCount * zCount + z * xCount + x;
    Long data = sparseData.get(offset);
    return data == null ? 0 : (int) (data & 0xffffffffL);
  }

  /**
   * sets the BlockID at a particular location
   *
   * @param x       x position relative to the block origin [0,0,0]
   * @param y       y position relative to the block origin [0,0,0]
   * @param z       z position relative to the block origin [0,0,0]
   * @param blockID
   */
  @Override
  public void setBlockID(int x, int y, int z, int blockID) {
    assert (x >= 0 && x < xCount);
    assert (y >= 0 && y < yCount);
    assert (z >= 0 && z < zCount);
    final int offset = y * xCount * zCount + z * xCount + x;
    Long data = sparseData.get(offset);
    if (blockID < 0) throw new IllegalArgumentException("Block ID must be non-negative");
    sparseData.put(offset, (long) blockID | (data == null ? 0L : (data & ~0xffffffffL)));
  }

  /**
   * gets the metadata at a particular location
   * error if the location is not stored in this fragment
   *
   * @param x x position relative to the block origin [0,0,0]
   * @param y y position relative to the block origin [0,0,0]
   * @param z z position relative to the block origin [0,0,0]
   */
  @Override
  public int getMetadata(int x, int y, int z) {
    assert (x >= 0 && x < xCount);
    assert (y >= 0 && y < yCount);
    assert (z >= 0 && z < zCount);
    final int offset = y * xCount * zCount + z * xCount + x;
    Long data = sparseData.get(offset);
    return data == null ? 0 : (int) ((data >>> 32) & 0x0f);
  }

  /**
   * sets the metadata at a particular location
   *
   * @param x        x position relative to the block origin [0,0,0]
   * @param y        y position relative to the block origin [0,0,0]
   * @param z        z position relative to the block origin [0,0,0]
   * @param metadata
   */
  @Override
  public void setMetadata(int x, int y, int z, int metadata) {
    assert (x >= 0 && x < xCount);
    assert (y >= 0 && y < yCount);
    assert (z >= 0 && z < zCount);
    final int offset = y * xCount * zCount + z * xCount + x;
    Long data = sparseData.get(offset);
    sparseData.put(offset, ((long) (metadata & 0x0f) << 32) | (data == null ? 0L : (data & ~(0x0fL << 32))));
  }

  /**
   * gets the light value at a particular location.
   * error if the location is not stored in this fragment
   *
   * @param x x position relative to the block origin [0,0,0]
   * @param y y position relative to the block origin [0,0,0]
   * @param z z position relative to the block origin [0,0,0]
   * @return lightvalue (sky << 4 | block)
   */
  @Override
  public byte getLightValue(int x, int y, int z) {
    assert (x >= 0 && x < xCount);
    assert (y >= 0 && y < yCount);
    assert (z >= 0 && z < zCount);
    final int offset = y * xCount * zCount + z * xCount + x;
    Long data = sparseData.get(offset);
    return data == null ? 0 : (byte)(data >>> 36);
  }

  /**
   * sets the light value at a particular location
   *
   * @param x          x position relative to the block origin [0,0,0]
   * @param y          y position relative to the block origin [0,0,0]
   * @param z          z position relative to the block origin [0,0,0]
   * @param lightValue lightvalue (sky << 4 | block)
   */
  @Override
  public void setLightValue(int x, int y, int z, byte lightValue) {
    assert (x >= 0 && x < xCount);
    assert (y >= 0 && y < yCount);
    assert (z >= 0 && z < zCount);
    final int offset = y * xCount * zCount + z * xCount + x;
    Long data = sparseData.get(offset);
    sparseData.put(offset, ((long) (lightValue & 0xff) << 36) | (data == null ? 0L : (data & ~(0xffL << 36))));
  }

  private HashMap<Integer, Long> sparseData;

  private int xCount;
  private int yCount;
  private int zCount;
}
