package net.montyclt.redstore.chunkloading;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.montyclt.redstore.Redstore;
import net.montyclt.redstore.block.ChunkLoaderBlock;
import net.montyclt.redstore.blockentity.ChunkLoaderBlockEntity;
import net.montyclt.redstore.registry.RedstoreBlocks;

/**
 * Hands chunks to vanilla and takes them back, and keeps the record of which ones are ours.
 *
 * <p>The loading itself is vanilla's: {@code setChunkForced} adds a {@code FORCED} ticket at the
 * entity-ticking level, and that ticket persists across restarts because vanilla saves its own
 * tickets. What this class adds is bookkeeping — see
 * {@link ChunkLoaderSavedData} — and the care needed to call that method at a safe moment.
 *
 * <p>See spec/blocks/chunk-loader.md section 2.
 */
public final class ChunkLoaderManager {
	public static void initialize() {
		// Every dimension is reconciled once the server is up, which is the first moment its levels
		// and their saved data all exist.
		ServerLifecycleEvents.SERVER_STARTED.register(server ->
				server.getAllLevels().forEach(ChunkLoaderManager::reconcile));

		// The repair path: a loader the record has never heard of claims its chunk the first time
		// anybody visits it. That covers a world restored from a backup, and one where the mod was
		// uninstalled for a while and the record went stale.
		//
		// Everything read here comes off the chunk that was handed to us. Asking the *level* for a
		// block state instead would send the question back through the chunk source, and this
		// callback runs inside the chunk source, on the chunk it is still finishing: the server
		// thread then waits for a chunk only the server thread can finish. See section 2.3.
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> chunk.getBlockEntities().forEach((pos, entity) -> {
			if (entity instanceof ChunkLoaderBlockEntity
					&& chunk.getBlockState(pos).getValue(ChunkLoaderBlock.ENABLED)) {
				acquire(level, pos);
			}
		}));
	}

	/**
	 * Claim this block's chunk.
	 *
	 * <p>Deferred to the next tick, and not out of caution: {@code setChunkForced} loads the chunk
	 * synchronously, so calling it from a chunk-load callback re-enters the chunk system, which is
	 * a known deadlock with C2ME. The block entity calls this from {@code onLoad}.
	 */
	public static void acquire(ServerLevel level, BlockPos pos) {
		level.getServer().execute(() -> {
			ChunkLoaderSavedData data = data(level);

			if (!data.claim(pos)) {
				return;
			}

			ChunkPos chunk = ChunkPos.containing(pos);

			// False means the ticket was already there: somebody else forced this chunk, and it is
			// not ours to give back.
			if (!level.setChunkForced(chunk.x(), chunk.z(), true)) {
				data.keepForSomeoneElse(chunk);
			}
		});
	}

	/** Give the chunk back, if this was the last loader holding it and we were the ones holding it. */
	public static void release(ServerLevel level, BlockPos pos) {
		level.getServer().execute(() -> {
			if (data(level).unclaim(pos)) {
				ChunkPos chunk = ChunkPos.containing(pos);
				level.setChunkForced(chunk.x(), chunk.z(), false);
			}
		});
	}

	/**
	 * Put the world and the record back in agreement, once, at startup.
	 *
	 * <p>Both directions need it. A chunk we recorded may have lost its ticket — the world was
	 * edited, the mod was uninstalled for a while, someone ran `/forceload remove` — so every
	 * record is re-forced, which is idempotent and cheap. And a block we recorded may no longer be
	 * there, in which case the record is dropped and the chunk released.
	 *
	 * <p>Checking the block means loading its chunk, which forcing it does anyway, so the order is:
	 * force first, then look.
	 */
	private static void reconcile(ServerLevel level) {
		ChunkLoaderSavedData data = data(level);

		for (BlockPos pos : data.loaders()) {
			ChunkPos chunk = ChunkPos.containing(pos);

			if (!level.setChunkForced(chunk.x(), chunk.z(), true)) {
				data.keepForSomeoneElse(chunk);
			}

			if (level.getBlockState(pos).is(RedstoreBlocks.CHUNK_LOADER)) {
				continue;
			}

			Redstore.LOGGER.info("No chunk loader at {} any more; letting go of {}", pos, chunk);

			if (data.unclaim(pos)) {
				level.setChunkForced(chunk.x(), chunk.z(), false);
			}
		}
	}

	private static ChunkLoaderSavedData data(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(ChunkLoaderSavedData.TYPE);
	}

	private ChunkLoaderManager() {
	}
}
