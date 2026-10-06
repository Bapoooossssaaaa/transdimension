package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
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

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.SculkPerson;

/**
 * Draws a sculk person as a player-shaped humanoid (classic arms) in a skin of pink sculk with glowing eyes
 * (textures/entity/sculk_person/sculk_person.png, made by tools/generate_textures.py {@code sculk_person_skin()}).
 * They're villagers underneath, but don't look like one.
 */
public class SculkPersonRenderer extends MobRenderer<SculkPerson, SculkPersonRenderer.SculkPersonRenderState,
		HumanoidModel<SculkPersonRenderer.SculkPersonRenderState>> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("sculk_person"), "main");
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/sculk_person/sculk_person.png");

	public SculkPersonRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), 0.5F);
	}

	/** The player model with classic (four pixel) arms and the outer skin layer on every part. */
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
				.texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F)
				.texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer), PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F)
				.texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer), PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create()
				.texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F)
				.texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create()
				.texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F)
				.texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer), PartPose.offset(1.9F, 12.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public SculkPersonRenderState createRenderState() {
		return new SculkPersonRenderState();
	}

	@Override
	public Identifier getTextureLocation(SculkPersonRenderState state) {
		return TEXTURE;
	}

	/** Nothing beyond what a humanoid model reads. */
	public static class SculkPersonRenderState extends HumanoidRenderState {
	}
}
