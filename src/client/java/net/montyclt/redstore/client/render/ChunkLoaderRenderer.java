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
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.montyclt.redstore.block.ChunkLoaderBlock;
import net.montyclt.redstore.blockentity.ChunkLoaderBlockEntity;

/**
 * Draws the pearl held over the pedestal, facing the camera and bobbing, the way the enchanting
 * table draws its book.
 *
 * <p>Two things are borrowed whole, and between them they are the entire renderer.
 *
 * <p><b>Facing the eye</b> is how vanilla draws a pearl that is in the air: never edge-on, so it
 * reads as a sphere without being one. A pearl held in stasis is a thrown pearl stopped mid-flight,
 * so it is drawn the same way — and a flat sprite that always faces you is rounder than any ball
 * that can be built out of boxes, which was the lesson of three attempts at building one.
 *
 * <p>It aims at the camera's <b>position</b>, and not at the camera's orientation the way
 * {@code ThrownItemRenderer} and the conduit's wind do. Turning by the orientation aligns a sprite
 * with the screen, which is not the same as pointing it at the viewer: anything away from the
 * middle of the screen is then seen at an angle, and its edge shows. It also means walking
 * sideways, without turning, does not move the sprite at all.
 *
 * <p>Vanilla can afford that because the things it billboards are small, fast and usually in front
 * of you. This one is a block you stand next to and look at from every side, so it turns to the eye
 * instead: a yaw and a pitch straight out of the vector from the pearl to the camera, which is
 * exactly perpendicular to the line of sight from wherever it is looked at.
 *
 * <p><b>The float</b> is the book's, to the number: {@code 0.75} up the block, then {@code 0.1}
 * more, then {@code sin(time × 0.1) × 0.01} — a bob of a sixth of a pixel with a period of about
 * three seconds. The pedestal is the enchanting table's height, so the pearl ends up exactly where
 * the book floats.
 *
 * <p>How far away it is still drawn is not set here. {@code BlockEntityRenderer#getViewDistance}
 * already answers 64, which is what the enchanting table pays for its book — and the pearl is this
 * block's whole state read-out, so it should not go dark before the thing it imitates.
 */
public class ChunkLoaderRenderer implements BlockEntityRenderer<ChunkLoaderBlockEntity, ChunkLoaderRenderState> {
	/** Where the enchanting table's book floats: the top of the table, and a little over. */
	private static final float HEIGHT = 0.75F;
	private static final float RISE = 0.1F;

	/** The book's own bob, unchanged. */
	private static final float BOB_SPEED = 0.1F;
	private static final float BOB_HEIGHT = 0.01F;

	/**
	 * Six pixels across, which is about what the book it replaces occupies. The {@code GROUND}
	 * display context — the one a thrown item is drawn in — already halves the item, so this is the
	 * rest of the way.
	 */
	private static final float SIZE = 0.75F;

	private final ItemModelResolver items;

	/**
	 * The pearl, built the first time one is drawn and not before.
	 *
	 * <p>It cannot be a constant. Block entity renderers are constructed during the first resource
	 * reload, and an item's default data components are not bound that early — building an
	 * {@code ItemStack} there fails with <i>Components not bound yet</i>, which takes the whole
	 * client down before the main menu. By the time anything is rendered they are bound.
	 */
	private ItemStack pearl;

	public ChunkLoaderRenderer(BlockEntityRendererProvider.Context context) {
		this.items = context.itemModelResolver();
	}

	@Override
	public ChunkLoaderRenderState createRenderState() {
		return new ChunkLoaderRenderState();
	}

	@Override
	public void extractRenderState(ChunkLoaderBlockEntity loader, ChunkLoaderRenderState state,
			float partialTick, Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderer.super.extractRenderState(loader, state, partialTick, cameraPosition, crumbling);

		state.pearl.clear();

		Level level = loader.getLevel();

		if (level == null || !loader.getBlockState().getValue(ChunkLoaderBlock.ENABLED)) {
			return;
		}

		// The book counts its own ticks, because its block entity has somewhere to keep them. This
		// one keeps nothing (see spec/blocks/chunk-loader.md section 6), so the world's clock does
		// the counting and the block's position sets the phase — which is what stops a row of
		// loaders from bobbing in lockstep, the way two enchanting tables never do.
		BlockPos pos = loader.getBlockPos();
		state.time = level.getGameTime() + (pos.getX() * 7 + pos.getY() * 13 + pos.getZ() * 29 & 0x3F);

		// The vector from the pearl to the eye, as the two angles that turn +Z onto it: yaw about
		// Y, then pitch about X, which is the order `submit` applies them in.
		double dx = cameraPosition.x - (pos.getX() + 0.5);
		double dy = cameraPosition.y - (pos.getY() + HEIGHT + RISE);
		double dz = cameraPosition.z - (pos.getZ() + 0.5);
		double flat = Math.sqrt(dx * dx + dz * dz);

		state.yaw = (float) Mth.atan2(dx, dz);
		state.pitch = (float) -Mth.atan2(dy, flat);

		if (this.pearl == null) {
			this.pearl = new ItemStack(Items.ENDER_PEARL);
		}

		this.items.updateForTopItem(state.pearl, this.pearl, ItemDisplayContext.GROUND, level, null,
				(int) pos.asLong());
	}

	@Override
	public void submit(ChunkLoaderRenderState state, PoseStack pose, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (state.pearl.isEmpty()) {
			return;
		}

		pose.pushPose();
		pose.translate(0.5F, HEIGHT, 0.5F);
		pose.translate(0.0F, RISE + Mth.sin(state.time * BOB_SPEED) * BOB_HEIGHT, 0.0F);
		pose.rotate(Axis.YP, state.yaw);
		pose.rotate(Axis.XP, state.pitch);
		pose.scale(SIZE, SIZE, SIZE);
		state.pearl.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}
}
