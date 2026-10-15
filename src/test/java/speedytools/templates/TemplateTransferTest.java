package speedytools.templates;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import speedytools.common.templates.TemplateTransfer;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import static org.junit.Assert.*;

public class TemplateTransferTest
{
  @Test
  public void multiPacketTransferPreservesTileData() throws Exception
  {
    NBTTagCompound source = new NBTTagCompound(); byte[] inventory = new byte[100000];
    new Random(1234).nextBytes(inventory); source.setByteArray("Inventory", inventory);
    byte[] bytes = TemplateTransfer.encode(source);
    TemplateTransfer transfer = new TemplateTransfer(bytes.length);
    for (int i = 0; i < bytes.length; i += TemplateTransfer.CHUNK_BYTES) {
      transfer.append(i, Arrays.copyOfRange(bytes, i, Math.min(bytes.length, i + TemplateTransfer.CHUNK_BYTES)));
    }
    assertTrue(transfer.complete()); assertArrayEquals(inventory, transfer.read().getByteArray("Inventory"));
  }

  @Test
  public void rejectsInvalidLengthsOffsetsReplaysAndIncompleteReads() throws Exception
  {
    for (int size : new int[] {-1, 0, TemplateTransfer.MAX_BYTES + 1}) {
      try { new TemplateTransfer(size); fail("Invalid size accepted"); } catch (IllegalArgumentException expected) {}
    }
    TemplateTransfer transfer = new TemplateTransfer(10);
    try { transfer.read(); fail("Incomplete transfer read"); } catch (IOException expected) {}
    try { transfer.append(1, new byte[1]); fail("Wrong offset accepted"); } catch (IllegalArgumentException expected) {}
    transfer.append(0, new byte[5]);
    try { transfer.append(0, new byte[5]); fail("Replay accepted"); } catch (IllegalArgumentException expected) {}
    try { transfer.append(5, new byte[6]); fail("Overflow accepted"); } catch (IllegalArgumentException expected) {}
    assertFalse(transfer.complete());
  }

  @Test
  public void portableCopyDropsPermissionsAndUnknownFieldsAndIsIndependent()
  {
    NBTTagCompound source = new NBTTagCompound(); source.setBoolean("Shared", true); source.setBoolean("CanEdit", true);
    source.setString("Unknown", "untrusted"); source.setIntArray("Blocks", new int[] {1, 2});
    NBTTagCompound copy = TemplateTransfer.portable(source);
    assertFalse(copy.hasKey("Shared")); assertFalse(copy.hasKey("CanEdit")); assertFalse(copy.hasKey("Unknown"));
    copy.getIntArray("Blocks")[0] = 9; assertEquals(1, source.getIntArray("Blocks")[0]);
  }

  @Test
  public void rejectsCompressedNbtBeyondTheReadBudget() throws Exception
  {
    NBTTagCompound source = new NBTTagCompound(); source.setByteArray("Payload", new byte[33 * 1024 * 1024]);
    byte[] bytes = TemplateTransfer.encode(source); TemplateTransfer transfer = new TemplateTransfer(bytes.length);
    for (int i = 0; i < bytes.length; i += TemplateTransfer.CHUNK_BYTES) {
      transfer.append(i, Arrays.copyOfRange(bytes, i, Math.min(bytes.length, i + TemplateTransfer.CHUNK_BYTES)));
    }
    try { transfer.read(); fail("Decompression budget ignored"); } catch (IOException | RuntimeException expected) {}
  }
}
