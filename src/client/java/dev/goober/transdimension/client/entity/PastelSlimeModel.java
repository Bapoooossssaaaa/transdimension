package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A pastel slime: a 12-pixel cube with a sleepy face (slit eyes and a tiny mouth on a lighter lower band, after the
 * owner's reference creature) and, once tamed, a little bow on top. A second instance ({@link #createJellyLayer()})
 * is the clear jelly coat one pixel outside the body, drawn translucent by {@link PastelSlimeJellyLayer}.
 *
 * <p>The body pivots at the middle of its bottom face, so squashing and stretching it (and shrinking babies) keeps it
 * sitting on the ground. Texture layout (64x64, see tools/generate_textures.py {@code pastel_slime()}): body 0,0 ·
 * jelly 0,24 · bow loops 48,0 · bow knot 48,8.
 */
public class PastelSlimeModel extends EntityModel<PastelSlimeRenderState> {
	private final ModelPart body;
	private final ModelPart bow;

	public PastelSlimeModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.bow = this.body.getChild("bow");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-6.0F, -12.0F, -6.0F, 12.0F, 12.0F, 12.0F), PartPose.offset(0.0F, 24.0F, 0.0F));
		// The bow sits on the jelly, towards the front left corner, tilted jauntily; its loops angle up from the knot.
		PartDefinition bow = body.addOrReplaceChild("bow", CubeListBuilder.create()
						.texOffs(48, 8).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
				PartPose.offsetAndRotation(2.5F, -14.0F, -3.0F, 0.0F, 0.2F, 0.15F));
		bow.addOrReplaceChild("bow_right", CubeListBuilder.create()
						.texOffs(48, 0).addBox(-4.0F, -1.5F, -0.5F, 4.0F, 3.0F, 1.0F),
				PartPose.offsetAndRotation(-0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.35F));
		bow.addOrReplaceChild("bow_left", CubeListBuilder.create()
						.texOffs(48, 0).mirror().addBox(0.0F, -1.5F, -0.5F, 4.0F, 3.0F, 1.0F),
				PartPose.offsetAndRotation(0.5F, 0.0F, 0.0F, 0.0F, 0.0F, -0.35F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	public static LayerDefinition createJellyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
						.texOffs(0, 24).addBox(-6.0F, -12.0F, -6.0F, 12.0F, 12.0F, 12.0F, new CubeDeformation(1.0F)),
				PartPose.offset(0.0F, 24.0F, 0.0F));
		body.addOrReplaceChild("bow", CubeListBuilder.create(), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(PastelSlimeRenderState state) {
		super.setupAnim(state);
		// Squash and stretch like a vanilla slime, only gentler, keeping its volume.
		float wide = 1.0F / (state.squish * 0.35F + 1.0F);
		float tall = 1.0F / wide;
		if (state.sitting) {
			// Settled down into a loaf.
			wide *= 1.06F;
			tall *= 0.88F;
		} else {
			// A slow, contented wobble.
			float wobble = Mth.sin(state.ageInTicks * 0.12F) * 0.025F;
			tall *= 1.0F + wobble;
			wide *= 1.0F - wobble * 0.5F;
		}
		float size = state.isBaby ? 0.55F : 1.0F;
		this.body.xScale = wide * size;
		this.body.yScale = tall * size;
		this.body.zScale = wide * size;
		// It has no neck, so it turns its whole self a little towards whatever it's watching.
		this.body.yRot = state.yRot * Mth.DEG_TO_RAD * 0.25F;
		this.bow.visible = state.tamed;
	}
}
