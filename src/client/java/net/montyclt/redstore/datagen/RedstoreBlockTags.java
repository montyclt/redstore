package net.montyclt.redstore.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;

import net.montyclt.redstore.Redstore;
import net.montyclt.redstore.registry.RedstoreBlockIds;

/**
 * The mod's two families, and the one vanilla tag every block of it belongs to.
 *
 * <p>The families exist for data packs and for whoever builds on this later; nothing in the mod
 * reads them yet, which is why they are declared here rather than in the block registry.
 */
public class RedstoreBlockTags extends FabricTagsProvider.BlockTagsProvider {
	public static final TagKey<Block> LOGIC_GATES = family("logic_gates");
	public static final TagKey<Block> REDSTONE_PLATES = family("redstone_plates");

	public RedstoreBlockTags(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		this.builder(LOGIC_GATES)
				.add(RedstoreBlockIds.AND_GATE, RedstoreBlockIds.OR_GATE, RedstoreBlockIds.XOR_GATE);

		this.builder(REDSTONE_PLATES)
				.add(RedstoreBlockIds.AND_GATE, RedstoreBlockIds.OR_GATE, RedstoreBlockIds.XOR_GATE,
						RedstoreBlockIds.REDSTONE_CLOCK);

		this.builder(BlockTags.MINEABLE_WITH_PICKAXE)
				.add(RedstoreBlockIds.AND_GATE, RedstoreBlockIds.OR_GATE, RedstoreBlockIds.XOR_GATE,
						RedstoreBlockIds.REDSTONE_CLOCK, RedstoreBlockIds.FILTER_HOPPER,
						RedstoreBlockIds.CHUNK_LOADER);

		// Obsidian's tier, because the block is mostly obsidian.
		this.builder(BlockTags.NEEDS_DIAMOND_TOOL).add(RedstoreBlockIds.CHUNK_LOADER);
	}

	private static TagKey<Block> family(String name) {
		return TagKey.create(Registries.BLOCK, Redstore.id(name));
	}
}
