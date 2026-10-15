package speedytools.clientside.templates;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.block.material.MapColor;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ISmartItemModel;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import javax.vecmath.Vector3f;
import java.util.*;

/** Cached, reduced voxel models render natively in creative slots, the library and hotbar. */
public class TemplateItemModel implements ISmartItemModel
{
  private IBakedModel fallback;
  private final Map<String, IBakedModel> cache = new LinkedHashMap<String, IBakedModel>(128, 0.75F, true) {
    protected boolean removeEldestEntry(Map.Entry<String, IBakedModel> entry) { return size() > 128; }
  };

  @SubscribeEvent
  public void bake(ModelBakeEvent event)
  {
    ModelResourceLocation wandKey = new ModelResourceLocation("speedytoolsmod:structurecapture", "inventory");
    ModelResourceLocation iconKey = new ModelResourceLocation("speedytoolsmod:structurecapture_icon", "inventory");
    IBakedModel wand = (IBakedModel)event.modelRegistry.getObject(wandKey);
    IBakedModel icon = (IBakedModel)event.modelRegistry.getObject(iconKey);
    if (wand != null && icon != null) event.modelRegistry.putObject(wandKey, new CaptureWandModel(wand, icon));
    ModelResourceLocation key = new ModelResourceLocation("speedytoolsmod:structuretemplate", "inventory");
    IBakedModel base = (IBakedModel)event.modelRegistry.getObject(key);
    if (base != null) {
      TemplateItemModel model = new TemplateItemModel(); model.fallback = base;
      event.modelRegistry.putObject(key, model);
    }
  }

  @Override
  public IBakedModel handleItemState(ItemStack stack)
  {
    NBTTagCompound tag = stack.getTagCompound();
    if (tag == null || tag.getByteArray("Thumbnail").length != 512) return fallback;
    int sx = tag.getInteger("SizeX"), sy = tag.getInteger("SizeY"), sz = tag.getInteger("SizeZ");
    if (sx < 1 || sy < 1 || sz < 1 || Math.max(sx, Math.max(sy, sz)) > 254) return fallback;
    byte[] cells = tag.getByteArray("Thumbnail");
    String key = tag.getString("Id") + ":" + tag.getInteger("Revision") + ":" + sx + ":" + sy + ":" + sz + ":" + Arrays.hashCode(cells);
    IBakedModel result = cache.get(key);
    if (result != null) return result;
    Minecraft mc = Minecraft.getMinecraft();
    TextureAtlasSprite texture = mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/wool_colored_white");
    IBakedModel stone = mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(Blocks.stone.getDefaultState());
    FaceBakery bakery = new FaceBakery(); List<BakedQuad> quads = new ArrayList<BakedQuad>();
    int nx = Math.min(8, sx), ny = Math.min(8, sy), nz = Math.min(8, sz);
    float scale = 14F / Math.max(sx, Math.max(sy, sz));
    float dx = scale * sx / nx, dy = scale * sy / ny, dz = scale * sz / nz;
    float ox = (16 - scale * sx) / 2, oy = (16 - scale * sy) / 2, oz = (16 - scale * sz) / 2;
    for (int y = 0; y < ny; ++y) for (int z = 0; z < nz; ++z) for (int x = 0; x < nx; ++x) {
      int colour = cells[x + 8*z + 64*y] & 255;
      if (colour == 0) continue;
      MapColor map = colour <= MapColor.mapColorArray.length ? MapColor.mapColorArray[colour - 1] : null;
      int rgb = map == null ? 0xAAAAAA : map.colorValue;
      for (EnumFacing face : EnumFacing.values()) {
        int ax = x + face.getFrontOffsetX(), ay = y + face.getFrontOffsetY(), az = z + face.getFrontOffsetZ();
        if (ax >= 0 && ax < nx && ay >= 0 && ay < ny && az >= 0 && az < nz && cells[ax + 8*az + 64*ay] != 0) continue;
        BlockPartFace part = new BlockPartFace(null, -1, "", new BlockFaceUV(new float[] {0, 0, 16, 16}, 0));
        BakedQuad quad = bakery.makeBakedQuad(new Vector3f(ox+x*dx, oy+y*dy, oz+z*dz),
                new Vector3f(ox+(x+1)*dx, oy+(y+1)*dy, oz+(z+1)*dz), part, texture, face, ModelRotation.X0_Y0, null, false, false);
        float shade = face == EnumFacing.UP ? 1F : face == EnumFacing.DOWN ? 0.5F : face.getAxis() == EnumFacing.Axis.X ? 0.7F : 0.85F;
        int r = (int)((rgb >> 16 & 255)*shade), g = (int)((rgb >> 8 & 255)*shade), b = (int)((rgb & 255)*shade);
        int[] vertices = quad.getVertexData();
        for (int v = 0; v < 4; ++v) vertices[v * (vertices.length / 4) + 3] = 0xFF000000 | b << 16 | g << 8 | r;
        quads.add(quad);
      }
    }
    List<List<BakedQuad>> faces = new ArrayList<List<BakedQuad>>();
    for (int i = 0; i < 6; ++i) faces.add(Collections.<BakedQuad>emptyList());
    result = quads.isEmpty() ? fallback : new SimpleBakedModel(quads, faces, false, true, texture, stone.getItemCameraTransforms());
    cache.put(key, result); return result;
  }

  public List getFaceQuads(EnumFacing face) { return fallback.getFaceQuads(face); }
  public List getGeneralQuads() { return fallback.getGeneralQuads(); }
  public boolean isAmbientOcclusion() { return false; }
  public boolean isGui3d() { return false; }
  public boolean isBuiltInRenderer() { return false; }
  public TextureAtlasSprite getTexture() { return fallback.getTexture(); }
  public ItemCameraTransforms getItemCameraTransforms() { return fallback.getItemCameraTransforms(); }
}
