package net.montyclt.redstore.chunkloading;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import net.montyclt.redstore.Redstore;

/**
 * What this dimension's chunk loaders have claimed, remembered across restarts.
 *
 * <p>Vanilla already persists the force-load itself — a forced chunk is a {@code FORCED} ticket and
 * {@code TicketStorage} is a {@code SavedData} of its own — so none of this is about keeping chunks
 * loaded. It is about knowing **which forced chunks are ours**, because the forced set is shared
 * with {@code /forceload} and with every other mod, and releasing one blindly would quietly undo
 * somebody else's work.
 *
 * <p>Two things are therefore kept:
 *
 * <ul>
 *   <li>{@code loaders} — where our switched-on loaders are. The chunk is derived from the
 *       position, so it is not stored twice.</li>
 *   <li>{@code preexisting} — chunks that were already forced when our first loader claimed them.
 *       Those are never released. The flag comes free: {@code setChunkForced} returns whether the
 *       ticket was actually added.</li>
 * </ul>
 *
 * <p>See spec/blocks/chunk-loader.md sections 2.2 and 11.
 */
public class ChunkLoaderSavedData extends SavedData {
	public static final Codec<ChunkLoaderSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.listOf().fieldOf("loaders").forGetter(data -> List.copyOf(data.loaders)),
			ChunkPos.CODEC.listOf().fieldOf("preexisting").forGetter(data -> List.copyOf(data.preexisting))
	).apply(instance, ChunkLoaderSavedData::new));

	/**
	 * The data fixer type is vanilla's own forced-chunk one. Nothing in this file shares a field
	 * name with vanilla's, so its fixes cannot match anything here, and it is the type whose
	 * meaning is closest if one is ever needed.
	 */
	public static final SavedDataType<ChunkLoaderSavedData> TYPE = new SavedDataType<>(
			Redstore.id("chunk_loaders"),
			ChunkLoaderSavedData::new,
			CODEC,
			DataFixTypes.SAVED_DATA_FORCED_CHUNKS);

	private final Set<BlockPos> loaders;
	private final Set<ChunkPos> preexisting;

	public ChunkLoaderSavedData() {
		this(List.of(), List.of());
	}

	private ChunkLoaderSavedData(List<BlockPos> loaders, List<ChunkPos> preexisting) {
		this.loaders = new HashSet<>(loaders);
		this.preexisting = new HashSet<>(preexisting);
	}

	/** True if this is the first loader to claim that chunk. */
	public boolean claim(BlockPos pos) {
		if (!this.loaders.add(pos)) {
			return false;
		}

		this.setDirty();

		return this.count(ChunkPos.containing(pos)) == 1;
	}

	/** True if that was the last loader in the chunk, so the chunk is ours to release. */
	public boolean unclaim(BlockPos pos) {
		if (!this.loaders.remove(pos)) {
			return false;
		}

		this.setDirty();

		return this.count(ChunkPos.containing(pos)) == 0 && !this.preexisting.contains(ChunkPos.containing(pos));
	}

	/** Somebody else had already forced this chunk, so it is not ours to give back. */
	public void keepForSomeoneElse(ChunkPos chunk) {
		if (this.preexisting.add(chunk)) {
			this.setDirty();
		}
	}

	public Set<BlockPos> loaders() {
		return Set.copyOf(this.loaders);
	}

	private int count(ChunkPos chunk) {
		return (int) this.loaders.stream().filter(pos -> ChunkPos.containing(pos).equals(chunk)).count();
	}
}
