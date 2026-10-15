package speedytools.templates;

import net.minecraft.nbt.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import speedytools.serverside.templates.TemplateStore;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.Assert.*;

public class TemplateStoreTest
{
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  private NBTTagCompound template()
  {
    NBTTagCompound data = new NBTTagCompound();
    data.setString("Id", UUID.randomUUID().toString()); data.setString("Owner", UUID.randomUUID().toString());
    data.setString("Name", "Oak House"); data.setInteger("Format", 1);
    data.setInteger("SizeX", 2); data.setInteger("SizeY", 1); data.setInteger("SizeZ", 1);
    NBTTagList palette = new NBTTagList(); NBTTagCompound block = new NBTTagCompound();
    block.setString("Block", "minecraft:stone"); palette.appendTag(block);
    data.setTag("Palette", palette); data.setIntArray("Blocks", new int[] {0, 0});
    data.setTag("Tiles", new NBTTagList());
    return data;
  }

  @Test
  public void roundTripOverwriteAndRecoverAcrossRestart() throws Exception
  {
    Path folder = temporary.newFolder().toPath(); TemplateStore store = new TemplateStore(folder);
    NBTTagCompound data = template(); String id = data.getString("Id");
    store.commit(data, 0);
    data = store.read(id); data.setString("Name", "Updated House"); store.commit(data, 1);
    store = new TemplateStore(folder);
    assertEquals("Updated House", store.read(id).getString("Name"));
    assertEquals(2, store.info(id).getInteger("Revision"));
    NBTTagCompound previous = store.previous(id);
    assertEquals("Oak House", previous.getString("Name"));
    store.commit(previous, 2);
    assertEquals("Oak House", new TemplateStore(folder).read(id).getString("Name"));
    assertEquals(3, store.info(id).getInteger("Revision"));
  }

  @Test
  public void staleOverwriteDoesNotChangeCurrentOrRecoveryRevision() throws Exception
  {
    TemplateStore store = new TemplateStore(temporary.newFolder().toPath()); NBTTagCompound data = template();
    store.commit(data, 0); String id = data.getString("Id");
    NBTTagCompound stale = store.read(id), current = store.read(id);
    current.setString("Name", "New revision"); store.commit(current, 1);
    try { store.commit(stale, 1); fail("Stale edit accepted"); } catch (IllegalArgumentException expected) {}
    assertEquals("New revision", store.read(id).getString("Name"));
    assertEquals("Oak House", store.previous(id).getString("Name"));
  }

  @Test
  public void deleteRestoreAndPurgeRequireCurrentRevision() throws Exception
  {
    Path folder = temporary.newFolder().toPath(); TemplateStore store = new TemplateStore(folder);
    NBTTagCompound data = template(); store.commit(data, 0); String id = data.getString("Id");
    try { store.purge(id, 1); fail("Purged a live template"); } catch (IllegalArgumentException expected) {}
    data.setBoolean("Deleted", true); store.commit(data, 1);
    store = new TemplateStore(folder); assertTrue(store.info(id).getBoolean("Deleted"));
    data = store.read(id); data.setBoolean("Deleted", false); store.commit(data, 2);
    assertFalse(store.info(id).getBoolean("Deleted"));
    data.setBoolean("Deleted", true); store.commit(data, 3);
    try { store.purge(id, 2); fail("Purged a stale revision"); } catch (IllegalArgumentException expected) {}
    store.purge(id, 4); assertNull(new TemplateStore(folder).info(id));
  }

  @Test
  public void rejectsOwnershipChangesAndBadAnchorsWithoutChangingDisk() throws Exception
  {
    TemplateStore store = new TemplateStore(temporary.newFolder().toPath()); NBTTagCompound data = template();
    store.commit(data, 0); String id = data.getString("Id");
    NBTTagCompound bad = store.read(id); bad.setString("Owner", UUID.randomUUID().toString());
    try { store.commit(bad, 1); fail("Ownership changed"); } catch (IllegalArgumentException expected) {}
    bad = store.read(id); bad.setInteger("AnchorX", 2);
    try { store.commit(bad, 1); fail("Invalid anchor accepted"); } catch (IllegalArgumentException expected) {}
    assertEquals(1, store.read(id).getInteger("Revision"));
    assertFalse(TemplateStore.validID("../../world"));
    assertFalse(TemplateStore.validID(""));
  }

  @Test
  public void computerCopySurvivesWorldSwitchAndEditsStayIndependent() throws Exception
  {
    TemplateStore worldA = new TemplateStore(temporary.newFolder().toPath());
    Path computerFolder = temporary.newFolder().toPath();
    TemplateStore computer = new TemplateStore(computerFolder);
    TemplateStore worldB = new TemplateStore(temporary.newFolder().toPath());
    NBTTagCompound original = template(); worldA.commit(original, 0);
    byte[] bytes = speedytools.common.templates.TemplateTransfer.encode(worldA.read(original.getString("Id")));
    speedytools.common.templates.TemplateTransfer transfer = new speedytools.common.templates.TemplateTransfer(bytes.length);
    transfer.append(0, bytes);
    NBTTagCompound copy = speedytools.common.templates.TemplateTransfer.portable(transfer.read());
    copy.setString("Id", UUID.randomUUID().toString()); computer.commit(copy, 0);
    computer = new TemplateStore(computerFolder);
    NBTTagCompound imported = computer.read(copy.getString("Id"));
    imported.setString("Id", UUID.randomUUID().toString()); imported.setString("Owner", UUID.randomUUID().toString());
    worldB.commit(imported, 0);
    copy.setString("Name", "Computer edit"); computer.commit(copy, 1);
    assertEquals("Oak House", worldA.read(original.getString("Id")).getString("Name"));
    assertEquals("Oak House", worldB.read(imported.getString("Id")).getString("Name"));
    assertEquals("Computer edit", new TemplateStore(computerFolder).read(copy.getString("Id")).getString("Name"));
    assertEquals(1, worldB.info(imported.getString("Id")).getInteger("Revision"));
  }

  @Test
  public void summariesCannotModifyStoredData() throws Exception
  {
    TemplateStore store = new TemplateStore(temporary.newFolder().toPath()); NBTTagCompound data = template();
    store.commit(data, 0); String id = data.getString("Id");
    NBTTagCompound summary = store.info(id);
    assertFalse(summary.hasKey("Blocks")); assertFalse(summary.hasKey("Tiles"));
    summary.setString("Owner", "intruder");
    assertEquals(data.getString("Owner"), store.read(id).getString("Owner"));
  }
}
