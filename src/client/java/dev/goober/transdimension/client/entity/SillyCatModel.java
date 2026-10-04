package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A chubby, big-headed cat with a tongue that pokes out for a lick.
 *
 * <p>Texture layout (64x32, see tools/generate_textures.py {@code silly_cat()}):
 * head 0,0 · muzzle 22,0 · ears 34,0 and 40,0 · tongue 46,0 · body 0,10 · legs 28,10 · tail 36,10.
 *
 * <p>The tongue is a 5-pixel-long box whose back end always stays inside the muzzle; it slides forward
 * for a lick (and tilts down), so it never detaches from the mouth.
 */
public class SillyCatModel extends EntityModel<SillyCatRenderState> {
	private static final float TONGUE_Y = 1.4F;
	/** Head-local z of the tongue's back end at full stretch (4 pixels past the muzzle). */
	private static final float TONGUE_Z_OUT = -6.0F;
	private static final float TAIL_X_ROT = 0.95F;

	private final ModelPart head;
	private final ModelPart tongue;
	private final ModelPart tail;
	private final ModelPart rightHindLeg;
	private final ModelPart leftHindLeg;
	private final ModelPart rightFrontLeg;
	private final ModelPart leftFrontLeg;

	public SillyCatModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.tongue = this.head.getChild("tongue");
		this.tail = root.getChild("tail");
		this.rightHindLeg = root.getChild("right_hind_leg");
		this.leftHindLeg = root.getChild("left_hind_leg");
		this.rightFrontLeg = root.getChild("right_front_leg");
		this.leftFrontLeg = root.getChild("left_front_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 5.0F, 5.0F)
						.texOffs(22, 0).addBox(-2.0F, 0.0F, -7.0F, 4.0F, 2.0F, 2.0F)
						.texOffs(34, 0).addBox(-3.0F, -5.0F, -3.0F, 2.0F, 2.0F, 1.0F)
						.texOffs(40, 0).addBox(1.0F, -5.0F, -3.0F, 2.0F, 2.0F, 1.0F),
				PartPose.offset(0.0F, 16.0F, -3.5F));
		head.addOrReplaceChild("tongue", CubeListBuilder.create()
						.texOffs(46, 0).addBox(-1.0F, 0.0F, -5.0F, 2.0F, 1.0F, 5.0F),
				PartPose.offset(0.0F, TONGUE_Y, TONGUE_Z_OUT));

		root.addOrReplaceChild("body", CubeListBuilder.create()
						.texOffs(0, 10).addBox(-2.5F, -2.0F, -4.5F, 5.0F, 4.0F, 9.0F),
				PartPose.offset(0.0F, 19.0F, 0.0F));
		root.addOrReplaceChild("tail", CubeListBuilder.create()
						.texOffs(36, 10).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, 17.5F, 4.0F, TAIL_X_ROT, 0.0F, 0.0F));

		CubeListBuilder leg = CubeListBuilder.create().texOffs(28, 10).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F);
		root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-1.5F, 21.0F, 3.0F));
		root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(1.5F, 21.0F, 3.0F));
		root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-1.5F, 21.0F, -3.0F));
		root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(1.5F, 21.0F, -3.0F));

		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(SillyCatRenderState state) {
		super.setupAnim(state);

		float lick = state.lickProgress;
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD + lick * 0.35F;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		// A happy little head tilt while licking.
		this.head.zRot = Mth.sin(state.ageInTicks * 0.9F) * 0.12F * lick;

		// The tongue: hidden when idle (unless this cat is a blepper, who shows ~0.7 pixels of tongue),
		// sliding out ~4 pixels and wiggling down for a lick.
		this.tongue.visible = state.blep || lick > 0.0F;
		float retracted = state.blep ? 3.3F : 4.0F;
		this.tongue.y = TONGUE_Y + lick * 0.6F;
		this.tongue.z = TONGUE_Z_OUT + retracted * (1.0F - lick);
		this.tongue.xRot = (state.blep ? 0.06F : 0.0F) + lick * (0.45F + Mth.sin(state.ageInTicks * 1.6F) * 0.2F);

		// Classic quadruped walk cycle.
		float swing = state.walkAnimationPos * 0.6662F;
		float amount = Math.min(state.walkAnimationSpeed, 1.0F) * 1.4F;
		this.rightHindLeg.xRot = Mth.cos(swing) * amount;
		this.leftHindLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
		this.rightFrontLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
		this.leftFrontLeg.xRot = Mth.cos(swing) * amount;

		// The tail sways lazily and perks up while licking.
		this.tail.xRot = TAIL_X_ROT + lick * 0.3F + Mth.sin(state.ageInTicks * 0.15F) * 0.05F;
		this.tail.zRot = Mth.sin(state.ageInTicks * 0.12F) * 0.35F;
	}
}
