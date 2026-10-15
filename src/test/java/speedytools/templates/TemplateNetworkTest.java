package speedytools.templates;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import speedytools.common.templates.TemplateNetwork;

import static org.junit.Assert.*;

public class TemplateNetworkTest
{
  @Test
  public void messageRoundTripAndInvalidLength()
  {
    NBTTagCompound data = new NBTTagCompound(); data.setInteger("Protocol", 1); data.setString("Action", "SAVE");
    TemplateNetwork.Message original = new TemplateNetwork.Message(data);
    ByteBuf buffer = Unpooled.buffer();
    try {
      original.toBytes(buffer); TemplateNetwork.Message decoded = new TemplateNetwork.Message(); decoded.fromBytes(buffer);
      assertEquals("SAVE", decoded.tag.getString("Action"));
      buffer.clear(); buffer.writeInt(Integer.MAX_VALUE); decoded = new TemplateNetwork.Message(); decoded.fromBytes(buffer);
      assertNull(decoded.tag);
    } finally { buffer.release(); }
  }

  @Test
  public void fullLibraryPageWithThumbnailsFitsThePacketBudget()
  {
    NBTTagCompound message = new NBTTagCompound(); message.setInteger("Protocol", 1); message.setString("Action", "LIST");
    net.minecraft.nbt.NBTTagList entries = new net.minecraft.nbt.NBTTagList();
    for (int i = 0; i < 24; ++i) {
      NBTTagCompound entry = new NBTTagCompound();
      entry.setString("Id", java.util.UUID.randomUUID().toString()); entry.setString("Owner", java.util.UUID.randomUUID().toString());
      entry.setString("Name", new String(new char[48]).replace('\0', 'X')); entry.setString("OwnerName", "SixteenCharacter");
      for (String key : new String[] {"SizeX", "SizeY", "SizeZ", "AnchorX", "AnchorY", "AnchorZ", "Revision", "Format", "BlockCount"}) entry.setInteger(key, 8);
      entry.setBoolean("Shared", true); entry.setBoolean("CanEdit", true); entry.setBoolean("Deleted", false);
      entry.setLong("Modified", Long.MAX_VALUE); entry.setByteArray("Thumbnail", new byte[512]); entries.appendTag(entry);
    }
    message.setTag("Entries", entries);
    ByteBuf buffer = Unpooled.buffer();
    try {
      new TemplateNetwork.Message(message).toBytes(buffer);
      assertTrue(buffer.readableBytes() <= 28676);
      TemplateNetwork.Message decoded = new TemplateNetwork.Message(); decoded.fromBytes(buffer);
      assertNotNull(decoded.tag); assertEquals(24, decoded.tag.getTagList("Entries", 10).tagCount());
    } finally { buffer.release(); }
  }

  @Test
  public void rejectsWrongProtocolAndTruncatedPayload()
  {
    ByteBuf buffer = Unpooled.buffer();
    try {
      NBTTagCompound data = new NBTTagCompound(); data.setInteger("Protocol", 999);
      new TemplateNetwork.Message(data).toBytes(buffer);
      TemplateNetwork.Message decoded = new TemplateNetwork.Message(); decoded.fromBytes(buffer); assertNull(decoded.tag);
      buffer.clear(); buffer.writeInt(20); buffer.writeByte(0);
      decoded = new TemplateNetwork.Message(); decoded.fromBytes(buffer); assertNull(decoded.tag);
    } finally { buffer.release(); }
  }
}
