package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

import dev.goober.transdimension.TransDimension;

/**
 * The little winged light shared by wild fairies and the Fairy Jar: a glowing 4-pixel cube with two pairs of
 * see-through fairy wings (big upper wings, small hind wings) that flutter like a butterfly's. {@link #createHaloLayer()}
 * is a soft glowing shell drawn over the cube.
 *
 * <p>Texture (32x32, tools/generate_textures.py {@code fairy_light()}): cube 0,0 · upper wing 0,8 · hind wing 0,14 ·
 * halo 0,20. Every part hangs off "light", which sits {@link #LIGHT_Y} pixels down the model (the ground is 24), so the
 * fairy floats inside its 0.4-block box.
 */
public class FairyLightModel extends EntityModel<FairyRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("fairy_light"), "main");
	public static final ModelLayerLocation HALO_LAYER = new ModelLayerLocation(TransDimension.id("fairy_light"), "halo");
	public static final float LIGHT_Y = 20.0F;

	private final Parts parts;

	public FairyLightModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
		this.parts = new Parts(root);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition light = mesh.getRoot().addOrReplaceChild("light", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offset(0.0F, LIGHT_Y, 0.0F));
		light.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.texOffs(0, 8).addBox(-5.0F, -4.0F, 0.0F, 5.0F, 5.0F, 0.0F), PartPose.offset(-1.0F, -1.0F, 2.2F));
		light.addOrReplaceChild("left_wing", CubeListBuilder.create()
				.texOffs(0, 8).mirror().addBox(0.0F, -4.0F, 0.0F, 5.0F, 5.0F, 0.0F), PartPose.offset(1.0F, -1.0F, 2.2F));
		light.addOrReplaceChild("right_hind_wing", CubeListBuilder.create()
				.texOffs(0, 14).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 3.0F, 0.0F), PartPose.offset(-1.0F, 0.5F, 2.2F));
		light.addOrReplaceChild("left_hind_wing", CubeListBuilder.create()
				.texOffs(0, 14).mirror().addBox(0.0F, 0.0F, 0.0F, 3.0F, 3.0F, 0.0F), PartPose.offset(1.0F, 0.5F, 2.2F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	/** The halo: a slightly larger see-through cube round the light (the wing parts exist but are empty). */
	public static LayerDefinition createHaloLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition light = mesh.getRoot().addOrReplaceChild("light", CubeListBuilder.create()
				.texOffs(0, 20).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, LIGHT_Y, 0.0F));
		for (String wing : Parts.WINGS) {
			light.addOrReplaceChild(wing, CubeListBuilder.create(), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(FairyRenderState state) {
		super.setupAnim(state);
		this.parts.flutter(state.ageInTicks, 0.25F);
	}

	/** The light's moving parts, shared with the Fairy Jar's model. */
	public static final class Parts {
		static final String[] WINGS = {"right_wing", "left_wing", "right_hind_wing", "left_hind_wing"};
		private final ModelPart rightWing;
		private final ModelPart leftWing;
		private final ModelPart rightHindWing;
		private final ModelPart leftHindWing;

		public Parts(ModelPart root) {
			ModelPart light = root.getChild("light");
			this.rightWing = light.getChild(WINGS[0]);
			this.leftWing = light.getChild(WINGS[1]);
			this.rightHindWing = light.getChild(WINGS[2]);
			this.leftHindWing = light.getChild(WINGS[3]);
		}

		/**
		 * Wings beat quickly, sweeping back and opening again; the hind wings follow a moment later. {@code open} is how
		 * far the wings spread at the open end of a beat (0 = out to the sides, about 1 = swept right back).
		 */
		public void flutter(float time, float open) {
			float beat = 0.5F + 0.5F * Mth.sin(time * 1.9F);
			float hind = 0.5F + 0.5F * Mth.sin(time * 1.9F - 0.6F);
			this.rightWing.yRot = open + (1.2F - open) * beat;
			this.leftWing.yRot = -this.rightWing.yRot;
			this.rightWing.zRot = 0.3F;
			this.leftWing.zRot = -0.3F;
			this.rightHindWing.yRot = open * 0.8F + (0.9F - open * 0.8F) * hind;
			this.leftHindWing.yRot = -this.rightHindWing.yRot;
			this.rightHindWing.zRot = -0.25F;
			this.leftHindWing.zRot = 0.25F;
		}
	}
}
