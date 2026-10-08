package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.client.FairyCutsceneCamera;
import dev.goober.transdimension.entity.Maddie;

/**
 * Draws Maddie as a player-shaped humanoid with slim arms, wearing her own skin
 * (textures/entity/maddie/maddie.png, made by tools/generate_textures.py {@code maddie_skin()}). In the Fairy Realm's
 * cutscene, while the Trans Fairy holds her up in the air, she hangs with her arms out and her head back
 * ({@link MaddieModel}, the pose PruneEffect breaks her up in).
 */
public class MaddieRenderer extends MobRenderer<Maddie, MaddieRenderer.MaddieRenderState, MaddieRenderer.MaddieModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("maddie"), "main");
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/maddie/maddie.png");
	/** Her pose held up in the air (radians, as the model's parts turn): arms out and a little back, legs apart, head back. */
	public static final float LIFTED_ARM_X = 0.25F;
	public static final float LIFTED_ARM_Z = 0.9F;
	public static final float LIFTED_LEG_Z = 0.12F;
	public static final float LIFTED_HEAD_X = -0.5F;

	public MaddieRenderer(EntityRendererProvider.Context context) {
		super(context, new MaddieModel(context.bakeLayer(LAYER)), 0.5F);
	}

	/** The player model with slim arms and the outer skin layer (hat, jacket, sleeves, trousers) on every part. */
	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeDeformation outer = new CubeDeformation(0.25F);
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create()
				.texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F)
				.texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, outer), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create()
				.texOffs(40, 16).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F)
				.texOffs(40, 32).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, outer), PartPose.offset(-5.0F, 2.5F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F)
				.texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, outer), PartPose.offset(5.0F, 2.5F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create()
				.texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F)
				.texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create()
				.texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F)
				.texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer), PartPose.offset(1.9F, 12.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public MaddieRenderState createRenderState() {
		return new MaddieRenderState();
	}

	@Override
	public Identifier getTextureLocation(MaddieRenderState state) {
		return TEXTURE;
	}

	@Override
	public void extractRenderState(Maddie maddie, MaddieRenderState state, float partialTick) {
		super.extractRenderState(maddie, state, partialTick);
		state.lift = FairyCutsceneCamera.lift();
	}

	/** What a humanoid model reads, and how far she's been lifted by the fairy (0 to 1). */
	public static class MaddieRenderState extends HumanoidRenderState {
		public float lift;
	}

	/** The humanoid model, easing into her held-up pose as she's lifted, with a little helpless kick. */
	public static class MaddieModel extends HumanoidModel<MaddieRenderState> {
		public MaddieModel(ModelPart root) {
			super(root);
		}

		@Override
		public void setupAnim(MaddieRenderState state) {
			super.setupAnim(state);
			float k = state.lift;
			if (k <= 0.0F) {
				return;
			}
			float kick = Mth.sin(state.ageInTicks * 0.35F) * 0.08F * k;
			this.rightArm.xRot = Mth.lerp(k, this.rightArm.xRot, LIFTED_ARM_X) + kick;
			this.rightArm.zRot = Mth.lerp(k, this.rightArm.zRot, LIFTED_ARM_Z);
			this.leftArm.xRot = Mth.lerp(k, this.leftArm.xRot, LIFTED_ARM_X) - kick;
			this.leftArm.zRot = Mth.lerp(k, this.leftArm.zRot, -LIFTED_ARM_Z);
			this.rightLeg.xRot = Mth.lerp(k, this.rightLeg.xRot, 0.0F) - kick;
			this.rightLeg.zRot = LIFTED_LEG_Z * k;
			this.leftLeg.xRot = Mth.lerp(k, this.leftLeg.xRot, 0.0F) + kick;
			this.leftLeg.zRot = -LIFTED_LEG_Z * k;
			this.head.xRot = Mth.lerp(k, this.head.xRot, LIFTED_HEAD_X);
		}
	}
}
