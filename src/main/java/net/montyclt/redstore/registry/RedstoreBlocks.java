package net.montyclt.redstore.registry;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.references.BlockItemId;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;

import net.montyclt.redstore.block.ChunkLoaderBlock;
import net.montyclt.redstore.block.FilterHopperBlock;
import net.montyclt.redstore.block.RedstoneClockBlock;
import net.montyclt.redstore.block.gate.GateOperation;
import net.montyclt.redstore.block.gate.LogicGateBlock;

public final class RedstoreBlocks {
	/** Exactly two, for every block: see {@link #tooltip}. */
	private static final int TOOLTIP_LINES = 2;

	public static final Block AND_GATE = gate(RedstoreBlockIds.AND_GATE, GateOperation.AND);
	public static final Block OR_GATE = gate(RedstoreBlockIds.OR_GATE, GateOperation.OR);
	public static final Block XOR_GATE = gate(RedstoreBlockIds.XOR_GATE, GateOperation.XOR);

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

	public static final Block CHUNK_LOADER = register(
			RedstoreBlockIds.CHUNK_LOADER,
			ChunkLoaderBlock::new,
			BlockBehaviour.Properties.of()
					.strength(3.0F)
					.requiresCorrectToolForDrops()
					.sound(SoundType.AMETHYST)
					.lightLevel(state -> state.getValue(ChunkLoaderBlock.ENABLED) ? 7 : 0)
					// A moving chunk loader would churn tickets every redstone tick.
					.pushReaction(PushReaction.IMMOVEABLE)
					// Twelve pixels tall, like the enchanting table it is shaped after.
					.noOcclusion()
	);

	public static void initialize() {
		// No tab of our own: these belong beside the vanilla components they imitate. The chunk
		// loader goes to FUNCTIONAL_BLOCKS instead. See spec/conventions.md §6.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(tab -> {
			tab.accept(AND_GATE.asItem());
			tab.accept(OR_GATE.asItem());
			tab.accept(XOR_GATE.asItem());
			tab.accept(REDSTONE_CLOCK.asItem());
			tab.accept(FILTER_HOPPER.asItem());
		});

		// Not a redstone component: it takes no signal, emits none, and no circuit contains one.
		// See spec/conventions.md §6.2.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(tab ->
				tab.accept(CHUNK_LOADER.asItem()));
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

		BlockItem blockItem = new BlockItem(block, new Item.Properties()
				.useBlockDescriptionPrefix()
				.component(DataComponents.LORE, tooltip(id))
				.setId(id.item()));
		Registry.register(BuiltInRegistries.ITEM, id.item(), blockItem);

		return block;
	}

	/**
	 * Every block item carries its own two-line hint: what the block is, and what a click does to
	 * it. The mod has no configuration and invents no rules, which only helps a player who can
	 * find out what a block does without leaving the game.
	 *
	 * <p>It is carried as the vanilla lore component rather than by overriding
	 * {@code Item#appendHoverText}, which 26.3 deprecates. The lines are handed over already
	 * styled, so they read as a hint in grey instead of lore in purple italics.
	 */
	private static ItemLore tooltip(BlockItemId id) {
		String name = id.item().identifier().getPath();

		List<Component> lines = IntStream.rangeClosed(1, TOOLTIP_LINES)
				.mapToObj(line -> (Component) Component
						.translatable("tooltip.redstore." + name + "." + line)
						.withStyle(ChatFormatting.GRAY))
				.toList();

		return new ItemLore(lines, lines);
	}

	private RedstoreBlocks() {
	}
}
