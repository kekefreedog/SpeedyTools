package speedytools.serverside.worldmanipulation;

/**
 * User: The Grey Ghost
 * Date: 20/07/2014
 * Stores the Block Data (ID, metadata, lightvalue) as arrays
 */
public class BlockDataStoreArray implements BlockDataStore
{
  public BlockDataStoreArray(int i_xcount, int i_ycount, int i_zcount)
  {
    xCount = i_xcount;
    yCount = i_ycount;
    zCount = i_zcount;
    int numberOfBlocks = xCount * yCount * zCount;
    blockIDs = new int[numberOfBlocks];
    metadataValues = new byte[numberOfBlocks];
    lightValues = new byte[numberOfBlocks];
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
    return blockIDs[offset];
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
    if (blockID < 0) throw new IllegalArgumentException("Block ID must be non-negative");
    blockIDs[offset] = blockID;
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
    return metadataValues[offset] & 0x0f;
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
    metadataValues[offset] = (byte) (metadata & 0x0f);
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
    return lightValues[offset];
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
    lightValues[offset] = lightValue;
  }

  private int blockIDs[];
  private byte metadataValues[];
  private byte lightValues[];

  private int xCount;
  private int yCount;
  private int zCount;
}
