package net.montyclt.redstore.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * What the renderer needs from a chunk loader: the pearl, and how long the world has been running.
 *
 * <p>The pearl is left empty when the loader is switched off, which is the whole of the block's
 * state read-out.
 */
public class ChunkLoaderRenderState extends BlockEntityRenderState {
	public final ItemStackRenderState pearl = new ItemStackRenderState();

	public float time;

	/** Where the eye is, as an angle to turn the pearl by. See {@code ChunkLoaderRenderer}. */
	public float yaw;
	public float pitch;
}
