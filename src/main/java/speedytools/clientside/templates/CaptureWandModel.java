package speedytools.clientside.templates;

import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.IPerspectiveAwareModel;
import org.apache.commons.lang3.tuple.Pair;
import javax.vecmath.Matrix4f;
import java.util.List;

/** Flat pixel icon in inventory slots, solid wand in held/world views. */
public final class CaptureWandModel implements IPerspectiveAwareModel
{
  private final IBakedModel wand, icon;
  public CaptureWandModel(IBakedModel wand, IBakedModel icon) { this.wand = wand; this.icon = icon; }

  public Pair<IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type)
  {
    IBakedModel selected = type == ItemCameraTransforms.TransformType.GUI ? icon : wand;
    // Delegate vanilla pose application; the selected models are not this wrapper.
    return Pair.of(ForgeHooksClient.handleCameraTransforms(selected, type), null);
  }

  public List getFaceQuads(EnumFacing face) { return wand.getFaceQuads(face); }
  public List getGeneralQuads() { return wand.getGeneralQuads(); }
  public boolean isAmbientOcclusion() { return wand.isAmbientOcclusion(); }
  // Forge queries this before selecting the GUI perspective.
  public boolean isGui3d() { return icon.isGui3d(); }
  public boolean isBuiltInRenderer() { return false; }
  public TextureAtlasSprite getTexture() { return wand.getTexture(); }
  public ItemCameraTransforms getItemCameraTransforms() { return wand.getItemCameraTransforms(); }
}
