package net.montyclt.redstore.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;

import net.montyclt.redstore.registry.RedstoreBlocks;

/** Every block drops itself. There is nothing else any of them could drop. */
public class RedstoreLootTables extends FabricBlockLootSubProvider {
	public RedstoreLootTables(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	public void generate() {
		this.dropSelf(RedstoreBlocks.AND_GATE);
		this.dropSelf(RedstoreBlocks.OR_GATE);
		this.dropSelf(RedstoreBlocks.XOR_GATE);
		this.dropSelf(RedstoreBlocks.REDSTONE_CLOCK);
		this.dropSelf(RedstoreBlocks.FILTER_HOPPER);
		this.dropSelf(RedstoreBlocks.CHUNK_LOADER);
	}
}
