package dev.goober.transdimension.client.wings;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import dev.goober.transdimension.TransDimension;

/**
 * Feathered Trans Wings, drawn on a player's back by {@link TransWingsLayer} (coordinates are relative to the body).
 *
 * <p>Each wing is built like a bird's: an arm bone from the shoulder blade with a row of covert feathers and four
 * secondary feathers hanging from it, and a hand bone from the wrist with its own coverts and five long primary
 * feathers. Every feather is its own part, so the wing can fold (the arm rises to the shoulder, the hand folds back
 * down beside it and the feathers taper to a point at the hips) and spread (bones straight out, feathers fanned into a wing with a white leading
 * edge, pink inner feathers and blue tips). Feathers sit at slightly different depths so overlapping ones never
 * flicker, and the two wings sit at different depths where they meet on the spine. The left wing mirrors the right.
 *
 * <p>Texture (64 x 64, tools/generate_textures.py {@code trans_wings_model_texture()}, same layout as the tables
 * below): arm 0,0 · arm coverts 14,0 · hand 28,0 · hand coverts 42,0 · secondaries 8*i,6 · primaries 8*i,20.
 */
public class TransWingsModel extends EntityModel<HumanoidRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("trans_wings"), "main");

	private static final float SHOULDER_X = 1.25F;
	private static final float SHOULDER_Y = 1.75F;
	private static final float SHOULDER_Z = 2.85F;
	/** The left wing sits a little further back so the two never fight over the same pixels on the spine. */
	private static final float LEFT_EXTRA_Z = 0.12F;
	private static final int ARM_LENGTH = 5;
	private static final int HAND_LENGTH = 5;
	/** Feathers are one-pixel boxes drawn 0.4 pixels thick. */
	private static final CubeDeformation FEATHER = new CubeDeformation(0.0F, 0.0F, -0.3F);

	// Secondary feathers on the arm, inner first: pivot x along the arm, pivot z (layering), length.
	private static final float[] SECONDARY_X = {-0.8F, -2.0F, -3.2F, -4.4F};
	private static final float[] SECONDARY_Z = {-0.4F, -0.3F, -0.2F, -0.1F};
	private static final int[] SECONDARY_LENGTH = {7, 8, 9, 10};
	/** Secondary angles against the arm when folded (hanging down and in from the raised arm) and spread (trailing behind it). */
	static final float[] SECONDARY_FOLDED = {-1.05F, -0.95F, -0.85F, -0.75F};
	static final float[] SECONDARY_SPREAD = {0.02F, 0.07F, 0.13F, 0.20F};

	// Primary feathers on the hand, inner first.
	private static final float[] PRIMARY_X = {-0.5F, -1.6F, -2.7F, -3.8F, -4.8F};
	private static final float[] PRIMARY_Z = {0.0F, 0.1F, 0.2F, 0.3F, 0.4F};
	private static final int[] PRIMARY_LENGTH = {11, 12, 13, 14, 13};
	/** Primary angles against the hand when folded and spread (fanning out to the wingtip). */
	static final float[] PRIMARY_FOLDED = {2.10F, 2.02F, 1.94F, 1.86F, 1.78F};
	static final float[] PRIMARY_SPREAD = {0.18F, 0.45F, 0.75F, 1.05F, 1.32F};

	private final Wing right;
	private final Wing left;

	public TransWingsModel(ModelPart root) {
		super(root);
		this.right = Wing.of(root, "right", 1.0F);
		this.left = Wing.of(root, "left", -1.0F);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		addWing(root, "right", false);
		addWing(root, "left", true);
		return LayerDefinition.create(mesh, 64, 64);
	}

	/**
	 * Adds one wing. The right wing reaches out to -x; the left one mirrors it (boxes flipped to +x, mirrored UVs and
	 * mirrored pivots). Feather boxes are centred on their pivot, so only the bones change shape.
	 */
	private static void addWing(PartDefinition root, String side, boolean left) {
		float sign = left ? -1.0F : 1.0F;
		float armX = left ? 0.0F : -ARM_LENGTH;
		float handX = left ? 0.0F : -HAND_LENGTH;

		PartDefinition wing = root.addOrReplaceChild(side + "_wing", CubeListBuilder.create().mirror(left)
						.texOffs(0, 0).addBox(armX, -1.0F, -0.5F, ARM_LENGTH, 2.0F, 1.0F)
						.texOffs(14, 0).addBox(armX, 0.4F, -0.15F, ARM_LENGTH, 3.0F, 1.0F, FEATHER),
				PartPose.offset(-SHOULDER_X * sign, SHOULDER_Y, SHOULDER_Z + (left ? LEFT_EXTRA_Z : 0.0F)));
		for (int i = 0; i < SECONDARY_LENGTH.length; i++) {
			wing.addOrReplaceChild(side + "_secondary_" + i, CubeListBuilder.create().mirror(left)
							.texOffs(8 * i, 6).addBox(-1.5F, 0.0F, -0.5F, 3.0F, SECONDARY_LENGTH[i], 1.0F, FEATHER),
					PartPose.offset(SECONDARY_X[i] * sign, 0.7F, SECONDARY_Z[i]));
		}

		PartDefinition hand = wing.addOrReplaceChild(side + "_hand", CubeListBuilder.create().mirror(left)
						.texOffs(28, 0).addBox(handX, -0.75F, -0.5F, HAND_LENGTH, 2.0F, 1.0F, new CubeDeformation(0.0F, -0.25F, -0.1F))
						.texOffs(42, 0).addBox(handX, 0.3F, -0.05F, HAND_LENGTH, 2.0F, 1.0F, FEATHER),
				PartPose.offset(-ARM_LENGTH * sign, 0.0F, 0.15F));
		for (int i = 0; i < PRIMARY_LENGTH.length; i++) {
			hand.addOrReplaceChild(side + "_primary_" + i, CubeListBuilder.create().mirror(left)
							.texOffs(8 * i, 20).addBox(-1.5F, 0.0F, -0.5F, 3.0F, PRIMARY_LENGTH[i], 1.0F, FEATHER),
					PartPose.offset(PRIMARY_X[i] * sign, 0.5F, PRIMARY_Z[i]));
		}
	}

	@Override
	public void setupAnim(HumanoidRenderState state) {
		super.setupAnim(state);
		WingPose pose = state.getData(WingPose.KEY);
		if (pose == null) {
			pose = WingPose.FOLDED;
		}
		this.right.apply(pose);
		this.left.apply(pose);
	}

	/** The parts of one wing; {@code sign} is 1 for the right wing and -1 for the mirrored left one. */
	private record Wing(ModelPart arm, ModelPart hand, ModelPart[] secondaries, ModelPart[] primaries, float sign) {
		static Wing of(ModelPart root, String side, float sign) {
			ModelPart arm = root.getChild(side + "_wing");
			ModelPart hand = arm.getChild(side + "_hand");
			ModelPart[] secondaries = new ModelPart[SECONDARY_LENGTH.length];
			for (int i = 0; i < secondaries.length; i++) {
				secondaries[i] = arm.getChild(side + "_secondary_" + i);
			}
			ModelPart[] primaries = new ModelPart[PRIMARY_LENGTH.length];
			for (int i = 0; i < primaries.length; i++) {
				primaries[i] = hand.getChild(side + "_primary_" + i);
			}
			return new Wing(arm, hand, secondaries, primaries, sign);
		}

		/** Poses are given for the right wing; mirroring negates the y and z rotations. */
		void apply(WingPose pose) {
			this.arm.xRot = pose.rootX();
			this.arm.yRot = pose.rootY() * this.sign;
			this.arm.zRot = pose.rootZ() * this.sign;
			this.hand.yRot = pose.handY() * this.sign;
			this.hand.zRot = pose.handZ() * this.sign;
			float fan = pose.fan();
			for (int i = 0; i < this.secondaries.length; i++) {
				ModelPart feather = this.secondaries[i];
				feather.xRot = pose.flex() * 0.5F;
				feather.zRot = (SECONDARY_FOLDED[i] + (SECONDARY_SPREAD[i] - SECONDARY_FOLDED[i]) * fan) * this.sign;
			}
			for (int i = 0; i < this.primaries.length; i++) {
				ModelPart feather = this.primaries[i];
				// Outer primaries bend the most, like the tip of a real wing.
				feather.xRot = pose.flex() * (0.6F + 0.1F * i);
				feather.zRot = (PRIMARY_FOLDED[i] + (PRIMARY_SPREAD[i] - PRIMARY_FOLDED[i]) * fan) * this.sign;
			}
		}
	}
}
