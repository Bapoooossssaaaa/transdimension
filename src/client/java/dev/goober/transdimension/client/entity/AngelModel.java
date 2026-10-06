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
import net.minecraft.util.Mth;

/**
 * An angel: the Trans Fairy's figure (the same body, head, hair, gown, arms and legs, laid out the same on the texture)
 * dressed in white and gold, with a golden halo turning slowly over its head and two great feathered wings in place of
 * the fairy's glittering ones. Each wing is a long panel of flight feathers with a shorter layer of covert feathers over
 * its back, both cut out feather by feather in the texture.
 *
 * <p>Texture (128x128, tools/generate_textures.py {@code angel_texture()}): the fairy's layout ({@code trans_fairy()}),
 * plus the halo at 96,0 / 96,4 / 112,0, the flight feathers at 64,32 and the coverts at 64,60.
 */
public class AngelModel extends EntityModel<LivingEntityRenderState> {
	private static final float WAIST_Y = 4.0F;
	private static final float HALO_Y = -11.0F;

	private final ModelPart angel;
	private final ModelPart head;
	private final ModelPart halo;
	private final ModelPart backHair;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart rightWing;
	private final ModelPart leftWing;

	public AngelModel(ModelPart root) {
		super(root);
		this.angel = root.getChild("angel");
		ModelPart body = this.angel.getChild("body");
		this.head = body.getChild("head");
		this.halo = this.head.getChild("halo");
		this.backHair = this.head.getChild("back_hair");
		this.rightArm = body.getChild("right_arm");
		this.leftArm = body.getChild("left_arm");
		this.rightLeg = body.getChild("right_leg");
		this.leftLeg = body.getChild("left_leg");
		this.rightWing = body.getChild("right_wing");
		this.leftWing = body.getChild("left_wing");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition angel = mesh.getRoot().addOrReplaceChild("angel", CubeListBuilder.create(), PartPose.offset(0.0F, WAIST_Y, 0.0F));
		PartDefinition body = angel.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 32).addBox(-3.0F, -7.0F, -2.0F, 6.0F, 8.0F, 4.0F)
				.texOffs(20, 32).addBox(-3.5F, -7.5F, -2.5F, 7.0F, 3.0F, 5.0F), PartPose.ZERO);
		body.addOrReplaceChild("skirt", CubeListBuilder.create()
				.texOffs(0, 44).addBox(-4.5F, 0.0F, -3.0F, 9.0F, 3.0F, 6.0F)
				.texOffs(0, 53).addBox(-5.5F, 3.0F, -4.0F, 11.0F, 3.0F, 8.0F)
				.texOffs(0, 64).addBox(-6.5F, 6.0F, -5.0F, 13.0F, 3.0F, 10.0F), PartPose.offset(0.0F, 1.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F)
				.texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, -7.0F, 0.0F));
		head.addOrReplaceChild("back_hair", CubeListBuilder.create()
				.texOffs(64, 0).addBox(-4.5F, 0.0F, 0.0F, 9.0F, 13.0F, 2.0F), PartPose.offset(0.0F, -6.0F, 3.5F));
		head.addOrReplaceChild("right_lock", CubeListBuilder.create()
				.texOffs(88, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F), PartPose.offset(-4.2F, -5.5F, -2.2F));
		head.addOrReplaceChild("left_lock", CubeListBuilder.create()
				.texOffs(88, 0).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F), PartPose.offset(4.2F, -5.5F, -2.2F));
		// The halo: an octagonal ring of gold, a little above the head.
		head.addOrReplaceChild("halo", CubeListBuilder.create()
				.texOffs(96, 0).addBox(-3.0F, 0.0F, -5.0F, 6.0F, 1.0F, 1.0F)
				.texOffs(96, 0).addBox(-3.0F, 0.0F, 4.0F, 6.0F, 1.0F, 1.0F)
				.texOffs(96, 4).addBox(-5.0F, 0.0F, -3.0F, 1.0F, 1.0F, 6.0F)
				.texOffs(96, 4).addBox(4.0F, 0.0F, -3.0F, 1.0F, 1.0F, 6.0F)
				.texOffs(112, 0).addBox(-4.0F, 0.0F, -4.0F, 1.0F, 1.0F, 1.0F)
				.texOffs(112, 0).addBox(3.0F, 0.0F, -4.0F, 1.0F, 1.0F, 1.0F)
				.texOffs(112, 0).addBox(-4.0F, 0.0F, 3.0F, 1.0F, 1.0F, 1.0F)
				.texOffs(112, 0).addBox(3.0F, 0.0F, 3.0F, 1.0F, 1.0F, 1.0F), PartPose.offset(0.0F, HALO_Y, 0.0F));

		body.addOrReplaceChild("right_arm", CubeListBuilder.create()
				.texOffs(40, 16).addBox(-1.5F, -1.0F, -1.0F, 2.0F, 10.0F, 2.0F)
				.texOffs(48, 16).addBox(-2.0F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offset(-3.5F, -6.0F, 0.0F));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(40, 16).mirror().addBox(-0.5F, -1.0F, -1.0F, 2.0F, 10.0F, 2.0F)
				.texOffs(48, 16).mirror().addBox(-1.0F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offset(3.5F, -6.0F, 0.0F));
		body.addOrReplaceChild("right_leg", CubeListBuilder.create()
				.texOffs(0, 76).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F)
				.texOffs(8, 76).addBox(-1.0F, 8.0F, -2.0F, 2.0F, 1.0F, 3.0F), PartPose.offset(-1.5F, 7.0F, 0.0F));
		body.addOrReplaceChild("left_leg", CubeListBuilder.create()
				.texOffs(0, 76).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F)
				.texOffs(8, 76).mirror().addBox(-1.0F, 8.0F, -2.0F, 2.0F, 1.0F, 3.0F), PartPose.offset(1.5F, 7.0F, 0.0F));

		// The wings, hinged on the upper back: flight feathers, and a shorter layer of coverts just behind them.
		body.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.texOffs(64, 32).mirror().addBox(-22.0F, -10.0F, 0.0F, 22.0F, 26.0F, 0.0F)
				.texOffs(64, 60).mirror().addBox(-16.0F, -9.0F, 0.35F, 16.0F, 12.0F, 0.0F), PartPose.offset(-1.0F, -5.0F, 2.1F));
		body.addOrReplaceChild("left_wing", CubeListBuilder.create()
				.texOffs(64, 32).addBox(0.0F, -10.0F, 0.0F, 22.0F, 26.0F, 0.0F)
				.texOffs(64, 60).addBox(0.0F, -9.0F, 0.35F, 16.0F, 12.0F, 0.0F), PartPose.offset(1.0F, -5.0F, 2.1F));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;

		// Slow, stately wingbeats, the wings held half open and swept back.
		float beat = Mth.sin(age * 0.12F);
		this.rightWing.yRot = 0.55F + 0.25F * beat;
		this.rightWing.zRot = 0.12F + 0.06F * beat;
		this.leftWing.yRot = -this.rightWing.yRot;
		this.leftWing.zRot = -this.rightWing.zRot;

		// Floating: a slow bob, the gown's hair stirring, feet hanging, arms a little open.
		this.angel.y = WAIST_Y + Mth.sin(age * 0.08F) * 1.0F;
		this.backHair.xRot = 0.1F + 0.04F * Mth.sin(age * 0.11F);
		this.rightLeg.xRot = 0.12F + 0.05F * Mth.sin(age * 0.09F);
		this.leftLeg.xRot = 0.04F + 0.05F * Mth.sin(age * 0.09F + 1.9F);
		this.rightArm.xRot = -0.25F + 0.05F * Mth.sin(age * 0.07F);
		this.rightArm.zRot = 0.3F;
		this.leftArm.xRot = -0.25F + 0.05F * Mth.sin(age * 0.07F + 1.2F);
		this.leftArm.zRot = -0.3F;
		this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.8F, 0.8F);
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.6F;

		// The halo turns slowly and floats a little above the head.
		this.halo.yRot = age * 0.03F;
		this.halo.y = HALO_Y + Mth.sin(age * 0.1F) * 0.4F;
	}
}
