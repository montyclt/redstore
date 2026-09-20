package net.montyclt.redstore.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;

import net.montyclt.redstore.block.FilterHopperBlock;
import net.montyclt.redstore.block.RedstoneClockBlock;
import net.montyclt.redstore.block.gate.GateOperation;
import net.montyclt.redstore.block.gate.LogicGateBlock;

public final class RedstoreBlocks {
	public static final Block AND_GATE = gate(RedstoreBlockIds.AND_GATE, GateOperation.AND);

	public static final Block REDSTONE_CLOCK = register(
			RedstoreBlockIds.REDSTONE_CLOCK,
			RedstoneClockBlock::new,
			plateProperties(state -> state.getValue(RedstoneClockBlock.POWERED))
	);

	public static final Block FILTER_HOPPER = register(
			RedstoreBlockIds.FILTER_HOPPER,
			FilterHopperBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.HOPPER)
	);

	public static void initialize() {
		// The mod's own creative tab comes later; for now the blocks live next to the vanilla hopper.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(tab -> {
			tab.accept(AND_GATE.asItem());
			tab.accept(REDSTONE_CLOCK.asItem());
			tab.accept(FILTER_HOPPER.asItem());
		});
	}

	private static Block gate(BlockItemId id, GateOperation operation) {
		return register(
				id,
				properties -> new LogicGateBlock(operation, properties),
				plateProperties(state -> state.getValue(LogicGateBlock.POWERED))
		);
	}

	/** Every flat plate shares these: break instantly, pop off pistons, glow while emitting. */
	private static BlockBehaviour.Properties plateProperties(java.util.function.Predicate<BlockState> lit) {
		return BlockBehaviour.Properties.of()
				.instabreak()
				.sound(SoundType.STONE)
				.pushReaction(PushReaction.POPPED)
				.lightLevel(state -> lit.test(state) ? 7 : 0);
	}

	private static Block register(ResourceKey<Block> id, Function<BlockBehaviour.Properties, Block> blockFactory, BlockBehaviour.Properties properties) {
		Block block = blockFactory.apply(properties.setId(id));
		return Registry.register(BuiltInRegistries.BLOCK, id, block);
	}

	private static Block register(BlockItemId id, Function<BlockBehaviour.Properties, Block> blockFactory, BlockBehaviour.Properties properties) {
		Block block = register(id.block(), blockFactory, properties);

		BlockItem blockItem = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(id.item()));
		Registry.register(BuiltInRegistries.ITEM, id.item(), blockItem);

		return block;
	}

	private RedstoreBlocks() {
	}
}
