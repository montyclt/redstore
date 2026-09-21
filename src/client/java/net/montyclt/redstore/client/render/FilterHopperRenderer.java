package net.montyclt.redstore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.phys.Vec3;

import net.montyclt.redstore.blockentity.FilterHopperBlockEntity;

/**
 * Draws the filter item on the hopper's sides.
 *
 * <p>The vanilla contraption this block replaces is a hopper with an item frame nailed to it, and
 * half of why a sorter is readable is that the frame shows what each cell takes. A block that hid
 * that would be worse than the thing it compresses, so the filter goes back on the outside.
 *
 * <p>It is drawn on every side but the one the spout points at, which is occupied, and only from
 * close by: a sorter is hundreds of these, and an item model is not free.
 */
public class FilterHopperRenderer implements BlockEntityRenderer<FilterHopperBlockEntity, FilterHopperRenderState> {
	/** On the collar, which is the only part of the block that is full width. */
	private static final float HEIGHT = 0.8125F;

	/** Half a block, plus enough to clear the face without z-fighting it. */
	private static final float OUT = 0.51F;

	private static final float SIZE = 0.4F;

	private static final int VIEW_DISTANCE = 24;

	private final ItemModelResolver items;

	public FilterHopperRenderer(BlockEntityRendererProvider.Context context) {
		this.items = context.itemModelResolver();
	}

	@Override
	public FilterHopperRenderState createRenderState() {
		return new FilterHopperRenderState();
	}

	@Override
	public void extractRenderState(FilterHopperBlockEntity hopper, FilterHopperRenderState state,
			float partialTick, Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderer.super.extractRenderState(hopper, state, partialTick, cameraPosition, crumbling);

		state.output = hopper.getBlockState().getValue(HopperBlock.FACING);
		state.filter.clear();

		ItemStack filter = hopper.getFilterStack();

		if (!filter.isEmpty()) {
			this.items.updateForTopItem(state.filter, filter, ItemDisplayContext.FIXED,
					hopper.getLevel(), null, (int) hopper.getBlockPos().asLong());
		}
	}

	@Override
	public void submit(FilterHopperRenderState state, PoseStack pose, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (state.filter.isEmpty()) {
			return;
		}

		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (side == state.output) {
				continue;
			}

			pose.pushPose();
			pose.translate(0.5F, HEIGHT, 0.5F);
			// An item faces +Z, and rotating by -yaw turns +Z onto the side's own normal.
			pose.rotateDegrees(Axis.YP, -side.toYRot());
			pose.translate(0.0F, 0.0F, OUT);
			pose.scale(SIZE, SIZE, SIZE);
			state.filter.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
	}

	@Override
	public int getViewDistance() {
		return VIEW_DISTANCE;
	}
}
