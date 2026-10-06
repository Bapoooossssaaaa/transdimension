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
 * A Cloudy: a wide cloud with puffs heaped on top, bulging from its sides and hanging lumpy underneath (so it's round
 * all over, not flat-bottomed), and a happy face on its front (the north face of the body). It bobs and rolls gently as
 * it floats.
 *
 * <p>Texture (128x64, tools/generate_textures.py {@code cloudy_texture()}): body 0,0 (face at 16,16) · top puffs 0,25 and
 * 40,25 · side puffs 0,40 and 28,40 · bottom puffs 80,0, 80,16 and 80,30.
 */
public class CloudyModel extends EntityModel<LivingEntityRenderState> {
	private final ModelPart cloud;

	public CloudyModel(ModelPart root) {
		super(root);
		this.cloud = root.getChild("cloud");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition cloud = mesh.getRoot().addOrReplaceChild("cloud", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-11.0F, -12.0F, -8.0F, 22.0F, 9.0F, 16.0F), PartPose.offset(0.0F, 24.0F, 0.0F));
		cloud.addOrReplaceChild("puff_top_left", CubeListBuilder.create()
				.texOffs(0, 25).addBox(-8.0F, -16.0F, -5.0F, 10.0F, 4.0F, 10.0F), PartPose.ZERO);
		cloud.addOrReplaceChild("puff_top_right", CubeListBuilder.create()
				.texOffs(40, 25).addBox(1.0F, -15.0F, -5.0F, 8.0F, 3.0F, 9.0F), PartPose.ZERO);
		cloud.addOrReplaceChild("puff_left", CubeListBuilder.create()
				.texOffs(0, 40).addBox(-14.0F, -10.0F, -5.0F, 3.0F, 6.0F, 10.0F), PartPose.ZERO);
		cloud.addOrReplaceChild("puff_right", CubeListBuilder.create()
				.texOffs(28, 40).addBox(11.0F, -11.0F, -6.0F, 3.0F, 6.0F, 11.0F), PartPose.ZERO);
		// Underneath: three lumps of different sizes and depths, so the bottom is as round as the top.
		cloud.addOrReplaceChild("puff_bottom_left", CubeListBuilder.create()
				.texOffs(80, 0).addBox(-9.0F, -3.0F, -6.0F, 9.0F, 3.0F, 10.0F), PartPose.ZERO);
		cloud.addOrReplaceChild("puff_bottom_right", CubeListBuilder.create()
				.texOffs(80, 16).addBox(1.0F, -3.0F, -4.0F, 8.0F, 2.0F, 9.0F), PartPose.ZERO);
		cloud.addOrReplaceChild("puff_bottom_middle", CubeListBuilder.create()
				.texOffs(80, 30).addBox(-4.0F, -2.0F, -1.0F, 8.0F, 2.0F, 7.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		this.cloud.y = 24.0F + Mth.sin(state.ageInTicks * 0.1F) * 0.6F - 0.6F;
		this.cloud.zRot = Mth.sin(state.ageInTicks * 0.07F) * 0.03F;
		this.cloud.xRot = Mth.sin(state.ageInTicks * 0.05F + 1.0F) * 0.02F;
	}
}
