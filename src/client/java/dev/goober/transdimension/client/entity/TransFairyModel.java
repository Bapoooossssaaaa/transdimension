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

import dev.goober.transdimension.entity.TransFairy;

/**
 * The Trans Fairy: a girl with long pink hair fading to blue, a crystal tiara and big blue eyes, in a gown that runs
 * down the flag (a blue bodice, then pink, white and pink tiers with a blue hem), holding a star wand, with two pairs
 * of glittering wings.
 *
 * <p>The model comes in two layers built from the same parts, so both pose identically: {@link #createBodyLayer()} has
 * everything solid (128x128 texture), {@link #createGlowLayer()} only the see-through wings and the wand's star
 * (64x64 texture), which TransFairyGlowLayer draws translucent and full bright. Layouts are documented in
 * tools/generate_textures.py ({@code trans_fairy()} and {@code trans_fairy_glow()}).
 *
 * <p>The whole fairy hangs from the "fairy" part at her waist, so she bobs as she hovers and tilts forward into a dive.
 * Her wings flutter fast; her arms, legs and hair follow her action (see {@link #setupAnim}).
 */
public class TransFairyModel extends EntityModel<TransFairyRenderState> {
	private static final float WAIST_Y = 4.0F;

	private final ModelPart fairy;
	private final ModelPart head;
	private final ModelPart backHair;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart rightUpperWing;
	private final ModelPart leftUpperWing;
	private final ModelPart rightLowerWing;
	private final ModelPart leftLowerWing;

	public TransFairyModel(ModelPart root) {
		super(root);
		this.fairy = root.getChild("fairy");
		ModelPart body = this.fairy.getChild("body");
		this.head = body.getChild("head");
		this.backHair = this.head.getChild("back_hair");
		this.rightArm = body.getChild("right_arm");
		this.leftArm = body.getChild("left_arm");
		this.rightLeg = body.getChild("right_leg");
		this.leftLeg = body.getChild("left_leg");
		this.rightUpperWing = body.getChild("right_upper_wing");
		this.leftUpperWing = body.getChild("left_upper_wing");
		this.rightLowerWing = body.getChild("right_lower_wing");
		this.leftLowerWing = body.getChild("left_lower_wing");
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(create(false), 128, 128);
	}

	public static LayerDefinition createGlowLayer() {
		return LayerDefinition.create(create(true), 64, 64);
	}

	private static CubeListBuilder solid(boolean glow, CubeListBuilder cubes) {
		return glow ? CubeListBuilder.create() : cubes;
	}

	private static CubeListBuilder glowing(boolean glow, CubeListBuilder cubes) {
		return glow ? cubes : CubeListBuilder.create();
	}

	private static MeshDefinition create(boolean glow) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition fairy = mesh.getRoot().addOrReplaceChild("fairy", CubeListBuilder.create(), PartPose.offset(0.0F, WAIST_Y, 0.0F));
		PartDefinition body = fairy.addOrReplaceChild("body", solid(glow, CubeListBuilder.create()
				.texOffs(0, 32).addBox(-3.0F, -7.0F, -2.0F, 6.0F, 8.0F, 4.0F)
				.texOffs(20, 32).addBox(-3.5F, -7.5F, -2.5F, 7.0F, 3.0F, 5.0F)), PartPose.ZERO);
		body.addOrReplaceChild("skirt", solid(glow, CubeListBuilder.create()
				.texOffs(0, 44).addBox(-4.5F, 0.0F, -3.0F, 9.0F, 3.0F, 6.0F)
				.texOffs(0, 53).addBox(-5.5F, 3.0F, -4.0F, 11.0F, 3.0F, 8.0F)
				.texOffs(0, 64).addBox(-6.5F, 6.0F, -5.0F, 13.0F, 3.0F, 10.0F)), PartPose.offset(0.0F, 1.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", solid(glow, CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F)
				.texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F))), PartPose.offset(0.0F, -7.0F, 0.0F));
		head.addOrReplaceChild("back_hair", solid(glow, CubeListBuilder.create()
				.texOffs(64, 0).addBox(-4.5F, 0.0F, 0.0F, 9.0F, 13.0F, 2.0F)), PartPose.offset(0.0F, -6.0F, 3.5F));
		head.addOrReplaceChild("right_lock", solid(glow, CubeListBuilder.create()
				.texOffs(88, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F)), PartPose.offset(-4.2F, -5.5F, -2.2F));
		head.addOrReplaceChild("left_lock", solid(glow, CubeListBuilder.create()
				.texOffs(88, 0).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F)), PartPose.offset(4.2F, -5.5F, -2.2F));
		head.addOrReplaceChild("tiara", solid(glow, CubeListBuilder.create()
				.texOffs(96, 0).addBox(-2.5F, -1.0F, -0.5F, 5.0F, 1.0F, 1.0F)
				.texOffs(96, 4).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F)
				.texOffs(96, 4).addBox(-2.0F, -2.0F, -0.5F, 1.0F, 1.0F, 1.0F)
				.texOffs(96, 4).addBox(1.0F, -2.0F, -0.5F, 1.0F, 1.0F, 1.0F)), PartPose.offset(0.0F, -8.5F, -3.2F));

		PartDefinition rightArm = body.addOrReplaceChild("right_arm", solid(glow, CubeListBuilder.create()
				.texOffs(40, 16).addBox(-1.5F, -1.0F, -1.0F, 2.0F, 10.0F, 2.0F)
				.texOffs(48, 16).addBox(-2.0F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F)), PartPose.offset(-3.5F, -6.0F, 0.0F));
		body.addOrReplaceChild("left_arm", solid(glow, CubeListBuilder.create()
				.texOffs(40, 16).mirror().addBox(-0.5F, -1.0F, -1.0F, 2.0F, 10.0F, 2.0F)
				.texOffs(48, 16).mirror().addBox(-1.0F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F)), PartPose.offset(3.5F, -6.0F, 0.0F));
		PartDefinition wand = rightArm.addOrReplaceChild("wand", solid(glow, CubeListBuilder.create()
				.texOffs(64, 16).addBox(-0.5F, -0.5F, -9.0F, 1.0F, 1.0F, 9.0F)), PartPose.offset(-0.5F, 8.5F, 0.0F));
		wand.addOrReplaceChild("star", glowing(glow, CubeListBuilder.create()
				.texOffs(0, 48).addBox(-2.5F, -0.5F, -0.5F, 5.0F, 1.0F, 1.0F)
				.texOffs(0, 48).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 5.0F, 1.0F)
				.texOffs(14, 48).addBox(-1.5F, -1.5F, -0.6F, 3.0F, 3.0F, 1.2F)), PartPose.offset(0.0F, 0.0F, -10.0F));

		body.addOrReplaceChild("right_leg", solid(glow, CubeListBuilder.create()
				.texOffs(0, 76).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F)
				.texOffs(8, 76).addBox(-1.0F, 8.0F, -2.0F, 2.0F, 1.0F, 3.0F)), PartPose.offset(-1.5F, 7.0F, 0.0F));
		body.addOrReplaceChild("left_leg", solid(glow, CubeListBuilder.create()
				.texOffs(0, 76).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F)
				.texOffs(8, 76).mirror().addBox(-1.0F, 8.0F, -2.0F, 2.0F, 1.0F, 3.0F)), PartPose.offset(1.5F, 7.0F, 0.0F));

		// Wings: flat panels hinged on her upper back, in the glow layer only.
		body.addOrReplaceChild("right_upper_wing", glowing(glow, CubeListBuilder.create()
				.texOffs(0, 0).mirror().addBox(-15.0F, -15.0F, 0.0F, 15.0F, 17.0F, 0.0F)), PartPose.offset(-1.0F, -4.5F, 2.1F));
		body.addOrReplaceChild("left_upper_wing", glowing(glow, CubeListBuilder.create()
				.texOffs(0, 0).addBox(0.0F, -15.0F, 0.0F, 15.0F, 17.0F, 0.0F)), PartPose.offset(1.0F, -4.5F, 2.1F));
		body.addOrReplaceChild("right_lower_wing", glowing(glow, CubeListBuilder.create()
				.texOffs(0, 20).mirror().addBox(-10.0F, -1.0F, 0.0F, 10.0F, 13.0F, 0.0F)), PartPose.offset(-1.0F, -4.5F, 2.1F));
		body.addOrReplaceChild("left_lower_wing", glowing(glow, CubeListBuilder.create()
				.texOffs(0, 20).addBox(0.0F, -1.0F, 0.0F, 10.0F, 13.0F, 0.0F)), PartPose.offset(1.0F, -4.5F, 2.1F));
		return mesh;
	}

	@Override
	public void setupAnim(TransFairyRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float t = state.actionTime;

		// Fast, glittering wingbeats; the lower pair a beat behind.
		float flutter = Mth.sin(age * 1.3F);
		this.rightUpperWing.yRot = 0.45F + 0.4F * flutter;
		this.rightLowerWing.yRot = 0.6F + 0.35F * Mth.sin(age * 1.3F - 0.6F);
		this.rightUpperWing.zRot = 0.3F;
		this.rightLowerWing.zRot = -0.45F;

		// Hovering: a gentle bob, swaying hair, dangling feet, wand held ready.
		this.fairy.y = WAIST_Y + Mth.sin(age * 0.1F) * 1.2F;
		this.backHair.xRot = 0.12F + 0.05F * Mth.sin(age * 0.15F);
		this.rightLeg.xRot = 0.15F + 0.08F * Mth.sin(age * 0.12F);
		this.leftLeg.xRot = 0.05F + 0.08F * Mth.sin(age * 0.12F + 1.7F);
		this.rightArm.xRot = -0.55F;
		this.rightArm.zRot = 0.18F;
		this.leftArm.xRot = 0.05F;
		this.leftArm.zRot = -0.3F;
		this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.8F, 0.8F);
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.6F;

		switch (state.action) {
			case TransFairy.VOLLEY -> {
				// Wand pointed at you, flicking with each burst.
				this.rightArm.xRot = -1.55F - 0.15F * Mth.sin(t * 0.8F) + this.head.xRot;
				this.rightArm.yRot = -0.1F;
				this.leftArm.xRot = -0.3F;
				this.leftArm.zRot = -0.7F;
			}
			case TransFairy.SWOOP -> {
				if (t < 14.0F) {
					// Winding up: leaning back, arms raised.
					float k = t / 14.0F;
					this.fairy.xRot = -0.3F * k;
					this.rightArm.xRot = -2.4F * k;
					this.leftArm.xRot = -2.4F * k;
				} else {
					// Diving: tipped forward, arms and wings swept back, hair streaming.
					this.fairy.xRot = 1.1F;
					this.rightArm.xRot = 0.9F;
					this.leftArm.xRot = 0.9F;
					this.rightArm.zRot = 0.35F;
					this.leftArm.zRot = -0.35F;
					this.rightLeg.xRot = 0.6F;
					this.leftLeg.xRot = 0.5F;
					this.backHair.xRot = 0.9F;
					this.rightUpperWing.yRot = 1.15F + 0.1F * flutter;
					this.rightLowerWing.yRot = 1.2F;
				}
			}
			case TransFairy.SPIKES -> {
				// Wand pointed at the ground; a thrust when the crystals break out.
				float thrust = t > 12.0F && t < 18.0F ? 0.4F : 0.0F;
				this.fairy.xRot = 0.25F;
				this.rightArm.xRot = -0.35F + thrust;
				this.rightArm.zRot = 0.1F;
				this.leftArm.xRot = -0.35F + thrust;
				this.leftArm.zRot = -0.1F;
			}
			case TransFairy.SUMMON, TransFairy.STARFALL -> {
				// Arms raised to the sky, looking up.
				this.rightArm.xRot = -2.9F;
				this.rightArm.zRot = 0.35F;
				this.leftArm.xRot = -2.9F;
				this.leftArm.zRot = -0.35F;
				this.head.xRot = -0.35F;
				this.rightUpperWing.yRot = 0.25F + 0.2F * flutter;
			}
			default -> {
			}
		}

		// The left side mirrors the right.
		this.leftUpperWing.yRot = -this.rightUpperWing.yRot;
		this.leftLowerWing.yRot = -this.rightLowerWing.yRot;
		this.leftUpperWing.zRot = -this.rightUpperWing.zRot;
		this.leftLowerWing.zRot = -this.rightLowerWing.zRot;
	}
}
