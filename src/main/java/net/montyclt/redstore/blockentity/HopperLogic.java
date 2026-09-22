package net.montyclt.redstore.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one piece of hopper behaviour that vanilla does not expose.
 *
 * <p>{@code HopperBlockEntity} cannot be subclassed — its only constructor hardcodes
 * {@code BlockEntityType.HOPPER} — but most of its transfer algorithm is public static and takes
 * interfaces, so we call it directly: {@link HopperBlockEntity#suckInItems} for the input side,
 * {@link HopperBlockEntity#addItem} for insertion, {@link HopperBlockEntity#getContainerAt} for
 * resolving chests, double chests and container entities.
 *
 * <p>Only {@code ejectItems} is private in vanilla, so only {@code ejectItems} is ported here.
 * Keeping the copied surface this small is the point: every line in this file is a line that can
 * silently drift from vanilla behaviour when Mojang changes something.
 *
 * <p>Known difference: vanilla staggers a destination hopper by setting its transfer cooldown when
 * it was empty, but only recognises its own {@code HopperBlockEntity}. Pushing into one of our
 * filter hoppers therefore does not stagger it, so a chain of filter hoppers can run slightly out
 * of phase with a chain of vanilla ones.
 */
final class HopperLogic {
	private HopperLogic() {
	}

	/** Ported from the private {@code HopperBlockEntity#ejectItems}. */
	static boolean ejectItems(Level level, BlockPos pos, BlockState state, FilterHopperBlockEntity hopper) {
		Direction facing = state.getValue(HopperBlock.FACING);
		Container target = HopperBlockEntity.getContainerAt(level, pos.relative(facing));

		if (target == null) {
			return false;
		}

		Direction insertSide = facing.getOpposite();

		if (isFullContainer(target, insertSide)) {
			return false;
		}

		for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
			ItemStack stack = hopper.getItem(slot);

			if (stack.isEmpty()) {
				continue;
			}

			ItemStack original = stack.copy();
			ItemStack leftover = HopperBlockEntity.addItem(hopper, target, hopper.removeItem(slot, 1), insertSide);

			if (leftover.isEmpty()) {
				target.setChanged();
				return true;
			}

			hopper.setItem(slot, original);
		}

		return false;
	}

	private static boolean isFullContainer(Container container, Direction side) {
		for (int slot : slotsFor(container, side)) {
			ItemStack stack = container.getItem(slot);

			if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
				return false;
			}
		}

		return true;
	}

	/** Which slots of this container can be reached from that side. */
	private static int[] slotsFor(Container container, Direction side) {
		if (container instanceof WorldlyContainer worldly) {
			return worldly.getSlotsForFace(side);
		}

		int[] slots = new int[container.getContainerSize()];

		for (int i = 0; i < slots.length; i++) {
			slots[i] = i;
		}

		return slots;
	}
}
