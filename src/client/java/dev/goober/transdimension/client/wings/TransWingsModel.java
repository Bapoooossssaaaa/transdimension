package dev.goober.transdimension.client.wings;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import dev.goober.transdimension.TransDimension;

/**
 * Feathered Trans Wings, drawn on a player's back by {@link TransWingsLayer}. Each wing is an inner feather panel
 * (10 x 12 pixels) with an outer panel (12 x 14) hinged at its tip; the left wing mirrors the right. Coordinates are
 * relative to the body part, the shoulders sit just behind the back.
 *
 * <p>Texture (64 x 32, tools/generate_textures.py {@code trans_wings_model_texture()}): inner panel at 0,0 and outer
 * panel at 0,13, both faces painted white at the root, pink in the middle and blue at the tips.
 */
public class TransWingsModel extends EntityModel<HumanoidRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("trans_wings"), "main");

	private final ModelPart rightWing;
	private final ModelPart rightOuter;
	private final ModelPart leftWing;
	private final ModelPart leftOuter;

	public TransWingsModel(ModelPart root) {
		super(root);
		this.rightWing = root.getChild("right_wing");
		this.rightOuter = this.rightWing.getChild("right_outer");
		this.leftWing = root.getChild("left_wing");
		this.leftOuter = this.leftWing.getChild("left_outer");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition right = root.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-10.0F, -1.0F, 0.0F, 10.0F, 12.0F, 1.0F), PartPose.offset(-1.5F, 2.0F, 2.2F));
		right.addOrReplaceChild("right_outer", CubeListBuilder.create()
				.texOffs(0, 13).addBox(-12.0F, -1.0F, 0.0F, 12.0F, 14.0F, 1.0F), PartPose.offset(-10.0F, 0.0F, 0.0F));
		PartDefinition left = root.addOrReplaceChild("left_wing", CubeListBuilder.create().mirror()
				.texOffs(0, 0).addBox(0.0F, -1.0F, 0.0F, 10.0F, 12.0F, 1.0F), PartPose.offset(1.5F, 2.0F, 2.2F));
		left.addOrReplaceChild("left_outer", CubeListBuilder.create().mirror()
				.texOffs(0, 13).addBox(0.0F, -1.0F, 0.0F, 12.0F, 14.0F, 1.0F), PartPose.offset(10.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(HumanoidRenderState state) {
		super.setupAnim(state);
		WingPose pose = state.getData(WingPose.KEY);
		if (pose == null) {
			pose = WingPose.FOLDED;
		}
		this.rightWing.xRot = pose.rootX();
		this.rightWing.yRot = pose.rootY();
		this.rightWing.zRot = pose.rootZ();
		this.rightOuter.zRot = pose.outerZ();
		this.leftWing.xRot = pose.rootX();
		this.leftWing.yRot = -pose.rootY();
		this.leftWing.zRot = -pose.rootZ();
		this.leftOuter.zRot = -pose.outerZ();
	}
}
