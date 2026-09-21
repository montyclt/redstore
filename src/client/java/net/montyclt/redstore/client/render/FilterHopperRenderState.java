package net.montyclt.redstore.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;

/** What the renderer needs from a filter hopper: the filter item and where the spout points. */
public class FilterHopperRenderState extends BlockEntityRenderState {
	public final ItemStackRenderState filter = new ItemStackRenderState();

	public Direction output = Direction.DOWN;
}
