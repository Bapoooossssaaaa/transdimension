package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * The trans fish: a cod-sized fish whose body is five pixels tall, one per stripe of the flag (blue, pink, white, pink,
 * blue), with a pearly face, a pink nose and a forked flag tail. It wiggles as it swims and flops on its side on land.
 *
 * <p>Every part hangs off one "fish" part pivoting at the middle of the body, so the swim wiggle and the flop are done
 * here in the model. Texture layout (32x32, see tools/generate_textures.py {@code trans_fish()}): body 0,0 · head 0,11 ·
 * nose 10,11 · tail fin 16,0 · top fin 16,9 · side fins 24,0.
 */
public class TransFishModel extends EntityModel<LivingEntityRenderState> {
	private static final float FISH_Y = 21.5F;

	private final ModelPart fish;
	private final ModelPart tailFin;

	public TransFishModel(ModelPart root) {
		super(root);
		this.fish = root.getChild("fish");
		this.tailFin = this.fish.getChild("tail_fin");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition fish = mesh.getRoot().addOrReplaceChild("fish", CubeListBuilder.create(), PartPose.offset(0.0F, FISH_Y, 0.0F));
		fish.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-1.0F, -2.5F, -3.0F, 2.0F, 5.0F, 6.0F), PartPose.ZERO);
		fish.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 11).addBox(-1.0F, -2.0F, -3.0F, 2.0F, 4.0F, 3.0F), PartPose.offset(0.0F, 0.0F, -3.0F));
		fish.addOrReplaceChild("nose", CubeListBuilder.create()
				.texOffs(10, 11).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 1.0F), PartPose.offset(0.0F, 0.5F, -6.0F));
		fish.addOrReplaceChild("tail_fin", CubeListBuilder.create()
				.texOffs(16, 0).addBox(0.0F, -2.5F, 0.0F, 0.0F, 5.0F, 4.0F), PartPose.offset(0.0F, 0.0F, 3.0F));
		fish.addOrReplaceChild("top_fin", CubeListBuilder.create()
				.texOffs(16, 9).addBox(0.0F, -2.0F, -2.0F, 0.0F, 2.0F, 4.0F), PartPose.offset(0.0F, -2.5F, 0.0F));
		fish.addOrReplaceChild("right_fin", CubeListBuilder.create()
						.texOffs(24, 0).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 0.0F, 2.0F),
				PartPose.offsetAndRotation(-1.0F, 1.5F, -1.0F, 0.0F, 0.0F, -Mth.PI / 4.0F));
		fish.addOrReplaceChild("left_fin", CubeListBuilder.create()
						.texOffs(24, 0).mirror().addBox(0.0F, 0.0F, -1.0F, 2.0F, 0.0F, 2.0F),
				PartPose.offsetAndRotation(1.0F, 1.5F, -1.0F, 0.0F, 0.0F, Mth.PI / 4.0F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		// Like a cod: the whole fish sways and its tail beats, harder when it's stranded.
		float beat = state.isInWater ? 1.0F : 1.5F;
		this.fish.yRot = 4.3F * Mth.DEG_TO_RAD * Mth.sin(0.6F * state.ageInTicks);
		this.tailFin.yRot = -beat * 0.45F * Mth.sin(0.6F * state.ageInTicks);
		if (!state.isInWater) {
			// Flopping on its side on the ground.
			this.fish.zRot = Mth.HALF_PI;
			this.fish.y = FISH_Y + 1.5F;
		}
	}
}
