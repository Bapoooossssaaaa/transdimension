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
 * The wild fairy: a glowing 4-pixel cube with a pair of see-through fairy wings, one on each side, that flutter up and
 * down. {@link #createHaloLayer()} is a soft glowing shell drawn over the cube.
 *
 * <p>Texture (32x32, tools/generate_textures.py {@code fairy_light()}): cube 0,0 · wing 0,8 · halo 0,20. Every part
 * hangs off "light", which sits {@link #LIGHT_Y} pixels down the model (the ground is 24), so the fairy floats inside
 * its 0.4-block box.
 */
public class FairyLightModel extends EntityModel<FairyRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("fairy_light"), "main");
	public static final ModelLayerLocation HALO_LAYER = new ModelLayerLocation(TransDimension.id("fairy_light"), "halo");
	public static final float LIGHT_Y = 20.0F;

	private final Parts parts;
	/** How fast the wings beat, against a wild fairy's. */
	private final float wingBeat;

	public FairyLightModel(ModelPart root) {
		this(root, false);
	}

	/**
	 * {@code inJar}: the Fairy Jar's fairy, drawn cutout instead of see-through (it's seen through the jar's glass) and
	 * beating its wings at under half a wild fairy's pace.
	 */
	public FairyLightModel(ModelPart root, boolean inJar) {
		super(root, inJar ? RenderTypes::entityCutout : RenderTypes::entityTranslucent);
		this.parts = new Parts(root);
		this.wingBeat = inJar ? 0.4F : 1.0F;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition light = mesh.getRoot().addOrReplaceChild("light", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offset(0.0F, LIGHT_Y, 0.0F));
		// One wing on each side, fixed at the middle of the light's side and reaching out sideways.
		light.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.texOffs(0, 8).addBox(-5.0F, -3.0F, 0.0F, 5.0F, 5.0F, 0.0F), PartPose.offset(-2.0F, 0.0F, 0.5F));
		light.addOrReplaceChild("left_wing", CubeListBuilder.create()
				.texOffs(0, 8).mirror().addBox(0.0F, -3.0F, 0.0F, 5.0F, 5.0F, 0.0F), PartPose.offset(2.0F, 0.0F, 0.5F));
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
		this.parts.flutter(state.ageInTicks * this.wingBeat, 0.25F);
	}

	/** The light's moving parts. */
	public static final class Parts {
		static final String[] WINGS = {"right_wing", "left_wing"};
		private final ModelPart rightWing;
		private final ModelPart leftWing;

		public Parts(ModelPart root) {
			ModelPart light = root.getChild("light");
			this.rightWing = light.getChild(WINGS[0]);
			this.leftWing = light.getChild(WINGS[1]);
		}

		/**
		 * The wings beat quickly up and down. {@code sweep} is how far back they're swept (0 = straight out to the
		 * sides, about 1 = well back).
		 */
		public void flutter(float time, float sweep) {
			float beat = Mth.sin(time * 1.9F);
			this.rightWing.yRot = sweep;
			this.leftWing.yRot = -sweep;
			this.rightWing.zRot = 0.45F * beat;
			this.leftWing.zRot = -0.45F * beat;
		}
	}
}
