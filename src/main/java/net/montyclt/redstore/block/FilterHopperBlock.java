package net.montyclt.redstore.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import net.montyclt.redstore.blockentity.FilterHopperBlockEntity;
import net.montyclt.redstore.registry.RedstoreBlockEntities;

/**
 * A hopper with one extra filter slot.
 *
 * <p>Everything visible — shape, model, placement, the {@code facing} and {@code enabled} states,
 * the comparator output — is inherited from the vanilla hopper. Only the block entity, the menu
 * and what the hopper is willing to accept differ.
 */
public class FilterHopperBlock extends HopperBlock {
	public FilterHopperBlock(Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FilterHopperBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}

		return createTickerHelper(type, RedstoreBlockEntities.FILTER_HOPPER, FilterHopperBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FilterHopperBlockEntity hopper) {
			player.openMenu(hopper);
		}

		return InteractionResult.SUCCESS;
	}
}
