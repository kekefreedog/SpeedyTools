package speedytools.common.templates;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import speedytools.SpeedyToolsMod;
import speedytools.serverside.templates.TemplateServer;

import java.io.*;

/** Small, bounded messages. Preview blocks are streamed in separate chunks, never tile NBT. */
public final class TemplateNetwork
{
  public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("speedytemplates");

  public static void initialise()
  {
    CHANNEL.registerMessage(ServerHandler.class, Message.class, 0, Side.SERVER);
    CHANNEL.registerMessage(ClientHandler.class, Message.class, 0, Side.CLIENT);
  }

  public static NBTTagCompound request(String action)
  {
    NBTTagCompound tag = new NBTTagCompound();
    tag.setString("Action", action);
    tag.setInteger("Protocol", 1);
    return tag;
  }

  public static void send(NBTTagCompound tag) { CHANNEL.sendToServer(new Message(tag)); }

  public static class Message implements IMessage
  {
    public NBTTagCompound tag;
    public Message() {}
    public Message(NBTTagCompound value) { tag = value; }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
      tag = null;
      try {
        int length = buffer.readInt();
        if (length < 1 || length > 28672 || length != buffer.readableBytes()) return;
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        tag = CompressedStreamTools.func_152456_a(new DataInputStream(new ByteArrayInputStream(bytes)),
                new NBTSizeTracker(131072));
        if (tag.getInteger("Protocol") != 1) tag = null;
      } catch (IOException | RuntimeException ex) {
        tag = null;
      }
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
      try {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        if (bytes.size() > 28672) throw new IllegalArgumentException("Template message too large");
        buffer.writeInt(bytes.size());
        buffer.writeBytes(bytes.toByteArray());
      } catch (IOException ex) {
        throw new IllegalStateException(ex);
      }
    }
  }

  public static class ServerHandler implements IMessageHandler<Message, IMessage>
  {
    @Override
    public IMessage onMessage(final Message message, final MessageContext context)
    {
      if (message.tag != null) SpeedyToolsMod.proxy.enqueueMessageOnCorrectThread(context, new Runnable() {
        public void run() { TemplateServer.handle(context.getServerHandler().playerEntity, message.tag); }
      });
      return null;
    }
  }

  public static class ClientHandler implements IMessageHandler<Message, IMessage>
  {
    @Override
    public IMessage onMessage(final Message message, MessageContext context)
    {
      if (message.tag != null) SpeedyToolsMod.proxy.enqueueMessageOnCorrectThread(context, new Runnable() {
        public void run() { SpeedyToolsMod.proxy.templateMessage(message.tag); }
      });
      return null;
    }
  }
}
