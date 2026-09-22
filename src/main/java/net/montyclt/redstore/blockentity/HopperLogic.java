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
	/** Indexed by container size; 54 is a double chest, which is vanilla's own ceiling. */
	private static final int[][] FLAT_SLOTS = new int[54][];

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

		return flatSlots(container.getContainerSize());
	}

	/**
	 * Every slot of a container that has no sides — {@code 0 … size - 1} — built once per size.
	 *
	 * <p>The cache is vanilla's, down to the length: {@code HopperBlockEntity} keeps the same table
	 * of 54, which is a double chest, and falls back to building a fresh array for anything bigger.
	 * It matters because the commonest thing a hopper pushes into is a chest, a chest has no sides,
	 * and so every push that took this path allocated a fresh {@code int[27]} — once per hopper
	 * every eight ticks, in a sorter that is hundreds of hoppers.
	 *
	 * <p>Like vanilla's, it is unsynchronised: two threads racing would each build an identical
	 * array and one would win, which is why vanilla does not guard it either.
	 */
	static int[] flatSlots(int size) {
		if (size >= FLAT_SLOTS.length) {
			return createFlatSlots(size);
		}

		int[] cached = FLAT_SLOTS[size];

		if (cached == null) {
			cached = createFlatSlots(size);
			FLAT_SLOTS[size] = cached;
		}

		return cached;
	}

	private static int[] createFlatSlots(int size) {
		int[] slots = new int[size];

		for (int i = 0; i < size; i++) {
			slots[i] = i;
		}

		return slots;
	}
}
