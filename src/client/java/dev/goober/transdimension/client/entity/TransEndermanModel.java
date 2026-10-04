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
 * The trans enderman: vanilla's enderman shape (64x32 texture, same layout), animated like it: a slow, stiff walk, arms
 * swaying a little, and the jaw dropping open when it's angry.
 *
 * <p>The glowing eyes are a second instance of this model ({@link #createEyesLayer()}): the same parts, but only a head
 * cube a hair bigger than the real one, textured with nothing but the eyes and drawn full bright. Being the same class,
 * it poses its head exactly like the body's.
 */
public class TransEndermanModel extends EntityModel<TransEndermanRenderState> {
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public TransEndermanModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		return create(false);
	}

	public static LayerDefinition createEyesLayer() {
		return create(true);
	}

	private static LayerDefinition create(boolean eyesOnly) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeListBuilder head = CubeListBuilder.create().texOffs(0, 0)
				.addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(eyesOnly ? 0.02F : 0.0F));
		PartDefinition headPart = root.addOrReplaceChild("head", head, PartPose.offset(0.0F, -13.0F, 0.0F));
		headPart.addOrReplaceChild("jaw", eyesOnly ? CubeListBuilder.create() : CubeListBuilder.create().texOffs(0, 16)
				.addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(-0.5F)), PartPose.ZERO);
		root.addOrReplaceChild("body", eyesOnly ? CubeListBuilder.create() : CubeListBuilder.create().texOffs(32, 16)
				.addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F), PartPose.offset(0.0F, -14.0F, 0.0F));
		CubeListBuilder arm = eyesOnly ? CubeListBuilder.create() : CubeListBuilder.create().texOffs(56, 0)
				.addBox(-1.0F, -2.0F, -1.0F, 2.0F, 30.0F, 2.0F);
		CubeListBuilder mirroredArm = eyesOnly ? CubeListBuilder.create() : CubeListBuilder.create().texOffs(56, 0).mirror()
				.addBox(-1.0F, -2.0F, -1.0F, 2.0F, 30.0F, 2.0F);
		root.addOrReplaceChild("right_arm", arm, PartPose.offset(-5.0F, -12.0F, 0.0F));
		root.addOrReplaceChild("left_arm", mirroredArm, PartPose.offset(5.0F, -12.0F, 0.0F));
		CubeListBuilder leg = eyesOnly ? CubeListBuilder.create() : CubeListBuilder.create().texOffs(56, 0)
				.addBox(-1.0F, 0.0F, -1.0F, 2.0F, 30.0F, 2.0F);
		CubeListBuilder mirroredLeg = eyesOnly ? CubeListBuilder.create() : CubeListBuilder.create().texOffs(56, 0).mirror()
				.addBox(-1.0F, 0.0F, -1.0F, 2.0F, 30.0F, 2.0F);
		root.addOrReplaceChild("right_leg", leg, PartPose.offset(-2.0F, -6.0F, 0.0F));
		root.addOrReplaceChild("left_leg", mirroredLeg, PartPose.offset(2.0F, -6.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(TransEndermanRenderState state) {
		super.setupAnim(state);
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD;

		// A humanoid walk, halved and clamped like vanilla's enderman: long legs, small steps.
		float swing = state.walkAnimationPos * 0.6662F;
		float amount = state.walkAnimationSpeed;
		this.rightArm.xRot = Mth.clamp(Mth.cos(swing + Mth.PI) * amount * 0.5F, -0.4F, 0.4F);
		this.leftArm.xRot = Mth.clamp(Mth.cos(swing) * amount * 0.5F, -0.4F, 0.4F);
		this.rightLeg.xRot = Mth.clamp(Mth.cos(swing) * 0.7F * amount, -0.4F, 0.4F);
		this.leftLeg.xRot = Mth.clamp(Mth.cos(swing + Mth.PI) * 0.7F * amount, -0.4F, 0.4F);

		// Arms drift a little while it stands around.
		float sway = Mth.cos(state.ageInTicks * 0.09F) * 0.05F + 0.05F;
		this.rightArm.zRot = sway;
		this.leftArm.zRot = -sway;
		this.rightArm.xRot += Mth.sin(state.ageInTicks * 0.067F) * 0.05F;
		this.leftArm.xRot -= Mth.sin(state.ageInTicks * 0.067F) * 0.05F;

		// A swipe with the right arm when it hits.
		if (state.attackAnim > 0.0F) {
			float swipe = Mth.sin(state.attackAnim * Mth.PI);
			this.rightArm.xRot -= swipe * 1.3F;
			this.rightArm.yRot = swipe * 0.3F;
		}

		if (state.creepy) {
			this.head.y -= 5.0F;
			this.jaw.y += 5.0F;
		}
	}
}
