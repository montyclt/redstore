package net.montyclt.redstore.datagen;

import com.mojang.math.Quadrant;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import net.montyclt.redstore.Redstore;
import net.montyclt.redstore.block.RedstoneClockBlock;
import net.montyclt.redstore.block.gate.LogicGateBlock;
import net.montyclt.redstore.registry.RedstoreBlocks;

/**
 * The block state files: which model each state of each block uses, and how far it is turned.
 *
 * <p>The models themselves are not here. They are built by the `generateAssets` Gradle task out of
 * vanilla's own, because they are art rather than data — see spec/conventions.md section 10. This
 * provider only names them, which is why every model below is a string.
 *
 * <p>These files are the reason this provider exists at all: a clock has 128 states and a gate 32,
 * and writing those by hand is how a variant goes missing.
 */
public class RedstoreModels extends FabricModelProvider {
	public RedstoreModels(FabricPackOutput output) {
		super(output);
	}

	@Override
	public void generateBlockStateModels(BlockModelGenerators generators) {
		gate(generators, RedstoreBlocks.AND_GATE, "and_gate");
		gate(generators, RedstoreBlocks.OR_GATE, "or_gate");
		gate(generators, RedstoreBlocks.XOR_GATE, "xor_gate");
		clock(generators);

		chunkLoader(generators);
		filterHopper(generators);
	}

	/**
	 * A gate: three torches that light on their own, and nothing else that changes.
	 *
	 * <p>`inverted` is deliberately not dispatched on. It changes nothing to look at — see
	 * spec/blocks/abstract-logic-gate.md section 6.2 — so leaving it out of the keys is what says
	 * so, and halves the file.
	 */
	private void gate(BlockModelGenerators generators, Block block, String name) {
		PropertyDispatch.C3<MultiVariant, Boolean, Boolean, Boolean> models =
				PropertyDispatch.initial(LogicGateBlock.INPUT_LEFT, LogicGateBlock.INPUT_RIGHT, DiodeBlock.POWERED);

		for (boolean left : BOTH) {
			for (boolean right : BOTH) {
				for (boolean powered : BOTH) {
					models.select(left, right, powered, model(name
							+ (left ? "_left" : "")
							+ (right ? "_right" : "")
							+ (powered ? "_on" : "")));
				}
			}
		}

		generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
				.with(models)
				.with(facing()));
	}

	/** The clock: a delay, a mode, an output and a lock. A locked clock is always dark. */
	private void clock(BlockModelGenerators generators) {
		PropertyDispatch.C4<MultiVariant, Integer, Boolean, Boolean, Boolean> models =
				PropertyDispatch.initial(RedstoneClockBlock.DELAY, RedstoneClockBlock.PULSE,
						RedstoneClockBlock.POWERED, RedstoneClockBlock.LOCKED);

		for (int delay = 1; delay <= 4; delay++) {
			for (boolean pulse : BOTH) {
				for (boolean powered : BOTH) {
					for (boolean locked : BOTH) {
						models.select(delay, pulse, powered, locked, model("redstone_clock_" + delay + "tick"
								+ (pulse ? "_pulse" : "")
								+ (locked ? "_locked" : powered ? "_on" : "")));
					}
				}
			}
		}

		generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(RedstoreBlocks.REDSTONE_CLOCK)
				.with(models)
				.with(facing()));
	}

	/**
	 * The chunk loader: one entry and no properties.
	 *
	 * <p>`enabled` chooses nothing here because the pearl that shows it is not part of the model —
	 * it faces the camera, so a block entity renderer draws it and reads the property itself. See
	 * spec/blocks/chunk-loader.md section 8.
	 */
	private void chunkLoader(BlockModelGenerators generators) {
		generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(
				RedstoreBlocks.CHUNK_LOADER, model("chunk_loader")));
	}

	/** The hopper: vanilla's own five, since the block is a hopper in every respect but its filter. */
	private void filterHopper(BlockModelGenerators generators) {
		MultiVariant down = model("filter_hopper");
		MultiVariant side = model("filter_hopper_side");

		generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(RedstoreBlocks.FILTER_HOPPER)
				.with(PropertyDispatch.initial(HopperBlock.FACING)
						.select(Direction.DOWN, down)
						.select(Direction.NORTH, side)
						.select(Direction.EAST, side.with(turn(Quadrant.R90)))
						.select(Direction.SOUTH, side.with(turn(Quadrant.R180)))
						.select(Direction.WEST, side.with(turn(Quadrant.R270)))));
	}

	/**
	 * Which way a plate is turned.
	 *
	 * <p>South is the unrotated model, because `facing` points at a diode's input and a model is
	 * drawn with its output towards the north.
	 */
	private static PropertyDispatch<VariantMutator> facing() {
		return PropertyDispatch.modify(HorizontalDirectionalBlock.FACING)
				.select(Direction.SOUTH, turn(Quadrant.R0))
				.select(Direction.WEST, turn(Quadrant.R90))
				.select(Direction.NORTH, turn(Quadrant.R180))
				.select(Direction.EAST, turn(Quadrant.R270));
	}

	private static VariantMutator turn(Quadrant quarters) {
		return VariantMutator.Y_ROT.withValue(quarters);
	}

	private static MultiVariant model(String name) {
		return new MultiVariant(WeightedList.of(new Variant(Redstore.id("block/" + name))));
	}

	private static final boolean[] BOTH = {false, true};

	/**
	 * Every block item is a **flat sprite**, not the block's own model.
	 *
	 * <p>This has to be said explicitly, because left alone the generator points each item at the
	 * block model, and a two-pixel plate rendered in three dimensions is a sliver you cannot read
	 * in a hotbar. It is why vanilla draws its own repeaters and comparators as sprites, and why
	 * the mod's icons are derived from those sprites.
	 *
	 * <p>The sprite models themselves — three lines of `item/generated` each — stay hand-written
	 * in `src/main/resources`.
	 */
	@Override
	public void generateItemModels(ItemModelGenerators generators) {
		for (String name : new String[]{"and_gate", "or_gate", "xor_gate", "redstone_clock", "filter_hopper"}) {
			generators.itemModelOutput.accept(
					BuiltInRegistries.ITEM.getValue(Redstore.id(name)),
					ItemModelUtils.plainModel(Redstore.id("item/" + name)));
		}

		// The chunk loader is the exception: it is a pedestal, not a plate, so its own model is
		// what it should look like in a hand and in a slot.
		generators.itemModelOutput.accept(
				BuiltInRegistries.ITEM.getValue(Redstore.id("chunk_loader")),
				ItemModelUtils.plainModel(Redstore.id("block/chunk_loader")));
	}
}
