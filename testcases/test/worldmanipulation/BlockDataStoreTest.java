package test.worldmanipulation;

import org.junit.Assert;
import org.junit.Test;
import speedytools.serverside.worldmanipulation.BlockDataStore;
import speedytools.serverside.worldmanipulation.BlockDataStoreArray;
import speedytools.serverside.worldmanipulation.BlockDataStoreSparse;

public class BlockDataStoreTest
{
  @Test
  public void arrayPreservesBlockData() {
    checkRoundTrips(new BlockDataStoreArray(2, 2, 2));
  }

  @Test
  public void sparsePreservesBlockData() {
    checkRoundTrips(new BlockDataStoreSparse(2, 2, 2, 8));
  }

  private void checkRoundTrips(BlockDataStore store) {
    Assert.assertEquals(0, store.getBlockID(1, 1, 1));
    Assert.assertEquals(0, store.getMetadata(1, 1, 1));
    Assert.assertEquals(0, store.getLightValue(1, 1, 1));
    for (int id = 0; id <= 65535; ++id) {
      checkBlock(store, id);
    }
    int[] largeIDs = {65536, 1048575, 1048576, 16777215, 1073741824, Integer.MAX_VALUE};
    for (int id : largeIDs) {
      checkBlock(store, id);
    }
    try {
      store.setBlockID(1, 1, 1, -1);
      Assert.fail("Negative block IDs must be rejected");
    } catch (IllegalArgumentException expected) {
      // Invalid IDs must not overwrite the previous block.
      Assert.assertEquals(0, store.getBlockID(1, 1, 1));
    }
  }

  private void checkBlock(BlockDataStore store, int id) {
    for (int metadata = 0; metadata < 16; ++metadata) {
      store.setBlockID(1, 1, 1, id);
      store.setMetadata(1, 1, 1, metadata);
      store.setLightValue(1, 1, 1, (byte) (metadata * 17));
      checkValues(store, id, metadata);
      store.setBlockID(1, 1, 1, 0);
      checkValues(store, 0, metadata);
      store.setBlockID(1, 1, 1, id);
      checkValues(store, id, metadata);
      Assert.assertEquals(0, store.getBlockID(0, 0, 0));
      Assert.assertEquals(0, store.getMetadata(0, 0, 0));
      Assert.assertEquals(0, store.getLightValue(0, 0, 0));
    }
    store.setBlockID(1, 1, 1, 0);
  }

  private void checkValues(BlockDataStore store, int id, int metadata) {
    Assert.assertEquals(id, store.getBlockID(1, 1, 1));
    Assert.assertEquals(metadata, store.getMetadata(1, 1, 1));
    Assert.assertEquals((byte) (metadata * 17), store.getLightValue(1, 1, 1));
  }
}
