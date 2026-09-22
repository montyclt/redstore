package net.montyclt.redstore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.montyclt.redstore.blockentity.ChunkLoaderBlockEntity;
import net.montyclt.redstore.chunkloading.ChunkLoaderManager;

/**
 * Keeps its own chunk loaded, for as long as it stands there switched on.
 *
 * <p>The loading is vanilla's — a {@code FORCED} ticket at the entity-ticking level, which vanilla
 * also persists — and everything this class does is decide when to ask for it and when to let go.
 * The bookkeeping that makes letting go safe is in
 * {@link net.montyclt.redstore.chunkloading.ChunkLoaderManager}.
 *
 * <p>See spec/blocks/chunk-loader.md.
 */
public class ChunkLoaderBlock extends BaseEntityBlock {
	public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;

	/** The enchanting table's shape, because the block is one with a pearl instead of a book. */
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

	public ChunkLoaderBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(ENABLED, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ENABLED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ChunkLoaderBlockEntity(pos, state);
	}

	/**
	 * A click switches it off and on, and says which.
	 *
	 * <p>There is no status read-out because there is nothing a message could say that the pearl
	 * does not: it is there while the chunk is held and gone while it is not.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		boolean enabled = !state.getValue(ENABLED);
		level.setBlock(pos, state.setValue(ENABLED, enabled), Block.UPDATE_ALL);

		if (level instanceof ServerLevel server) {
			if (enabled) {
				ChunkLoaderManager.acquire(server, pos);
			} else {
				ChunkLoaderManager.release(server, pos);
			}
		}

		level.playSound(null, pos, enabled ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_BREAK,
				SoundSource.BLOCKS, 0.5F, enabled ? 1.0F : 0.7F);

		if (player instanceof ServerPlayer serverPlayer) {
			// true = action bar rather than chat.
			serverPlayer.sendSystemMessage(Component.translatable(enabled
					? "message.redstore.chunk_loader.on"
					: "message.redstore.chunk_loader.off"), true);
		}

		return InteractionResult.SUCCESS;
	}

	/** A loader claims its chunk the moment it is put down. */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);

		if (level instanceof ServerLevel server && state.getValue(ENABLED) && !oldState.is(this)) {
			ChunkLoaderManager.acquire(server, pos);
		}
	}

	/**
	 * Let the chunk go when the block actually stops being a chunk loader.
	 *
	 * <p>This hook and not {@code BlockEntity#setRemoved}, which also fires when a world unloads:
	 * a server shutting down must not release every loader it has.
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		ChunkLoaderManager.release(level, pos);
	}
}
