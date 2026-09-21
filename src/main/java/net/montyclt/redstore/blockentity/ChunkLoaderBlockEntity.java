package net.montyclt.redstore.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.montyclt.redstore.registry.RedstoreBlockEntities;

/**
 * A marker, and deliberately nothing else. It does not tick and it stores no data.
 *
 * <p>What a loader has claimed is kept once per dimension, in
 * {@link net.montyclt.redstore.chunkloading.ChunkLoaderSavedData}, because that answer has to
 * outlive the block's chunk being unloaded — which is exactly what a block entity does not do.
 *
 * <p>What it is for is being <em>found</em>: a chunk's block entities are a map the game already
 * keeps, so when a chunk loads, the manager can ask it for loaders in one lookup instead of
 * walking sixteen by sixteen by three hundred and eighty-four block states. That is what lets a
 * loader placed before this record existed — a world restored from a backup, or one where the mod
 * was uninstalled for a while — claim its chunk again the first time anybody visits it.
 */
public class ChunkLoaderBlockEntity extends BlockEntity {
	public ChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
		super(RedstoreBlockEntities.CHUNK_LOADER, pos, state);
	}
}
