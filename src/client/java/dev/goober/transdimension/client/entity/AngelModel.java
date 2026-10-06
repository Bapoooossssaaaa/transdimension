package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * An angel: a glowing orb like the wild fairies (FairyLightModel), but bigger, white and gold, inside a soft golden glow,
 * with a golden halo turning slowly over it and two great white feathered wings beating slowly at its sides. Drawn
 * see-through (the glow is half transparent) and full bright.
 *
 * <p>Texture (64x64, tools/generate_textures.py {@code angel_texture()}): orb 0,0 · glow 32,0 · halo 0,16 / 0,20 /
 * 20,16 · flight feathers 0,32 (a 14x16 plane) · coverts 32,32 (a 10x8 plane). Everything hangs off "angel", the orb's
 * middle, {@link #ORB_Y} pixels down the model (the ground is 24).
 */
public class AngelModel extends EntityModel<LivingEntityRenderState> {
	private static final float ORB_Y = 12.0F;
	private static final float HALO_Y = -10.0F;

	private final ModelPart angel;
	private final ModelPart halo;
	private final ModelPart rightWing;
	private final ModelPart leftWing;

	public AngelModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
		this.angel = root.getChild("angel");
		this.halo = this.angel.getChild("halo");
		this.rightWing = this.angel.getChild("right_wing");
		this.leftWing = this.angel.getChild("left_wing");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition angel = mesh.getRoot().addOrReplaceChild("angel", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F)
				.texOffs(32, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(1.6F)), PartPose.offset(0.0F, ORB_Y, 0.0F));
		// The halo: an octagonal ring of gold over the orb.
		angel.addOrReplaceChild("halo", CubeListBuilder.create()
				.texOffs(0, 16).addBox(-4.0F, 0.0F, -6.0F, 8.0F, 1.0F, 1.0F)
				.texOffs(0, 16).addBox(-4.0F, 0.0F, 5.0F, 8.0F, 1.0F, 1.0F)
				.texOffs(0, 20).addBox(-6.0F, 0.0F, -4.0F, 1.0F, 1.0F, 8.0F)
				.texOffs(0, 20).addBox(5.0F, 0.0F, -4.0F, 1.0F, 1.0F, 8.0F)
				.texOffs(20, 16).addBox(-5.0F, 0.0F, -5.0F, 1.0F, 1.0F, 1.0F)
				.texOffs(20, 16).addBox(4.0F, 0.0F, -5.0F, 1.0F, 1.0F, 1.0F)
				.texOffs(20, 16).addBox(-5.0F, 0.0F, 4.0F, 1.0F, 1.0F, 1.0F)
				.texOffs(20, 16).addBox(4.0F, 0.0F, 4.0F, 1.0F, 1.0F, 1.0F), PartPose.offset(0.0F, HALO_Y, 0.0F));
		// The wings, hinged on the orb's sides: flight feathers, with a shorter layer of coverts just behind them.
		angel.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.texOffs(0, 32).mirror().addBox(-14.0F, -9.0F, 0.0F, 14.0F, 16.0F, 0.0F)
				.texOffs(32, 32).mirror().addBox(-10.0F, -8.0F, 0.3F, 10.0F, 8.0F, 0.0F), PartPose.offset(-4.0F, -1.0F, 1.0F));
		angel.addOrReplaceChild("left_wing", CubeListBuilder.create()
				.texOffs(0, 32).addBox(0.0F, -9.0F, 0.0F, 14.0F, 16.0F, 0.0F)
				.texOffs(32, 32).addBox(0.0F, -8.0F, 0.3F, 10.0F, 8.0F, 0.0F), PartPose.offset(4.0F, -1.0F, 1.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		// Floating: a slow bob. Wings beating slowly up and down, swept a little back.
		this.angel.y = ORB_Y + Mth.sin(age * 0.08F) * 1.2F;
		float beat = Mth.sin(age * 0.25F);
		this.rightWing.yRot = 0.35F;
		this.leftWing.yRot = -0.35F;
		this.rightWing.zRot = 0.1F + 0.4F * beat;
		this.leftWing.zRot = -this.rightWing.zRot;
		// The halo turns slowly and floats a little over the orb.
		this.halo.yRot = age * 0.03F;
		this.halo.y = HALO_Y + Mth.sin(age * 0.1F + 1.0F) * 0.4F;
	}
}
