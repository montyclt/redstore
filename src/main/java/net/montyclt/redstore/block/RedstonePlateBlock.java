package net.montyclt.redstore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The flat, repeater-sized components.
 *
 * <p>The plate shape and its placement rules, {@code facing} pointing at the output, the front-only
 * strong output, and the click grammar where a right-click advances the block's setting.
 *
 * <p>The three gates used to live here too and now extend vanilla's {@link DiodeBlock} instead,
 * which gives them all of this and more for nothing. The redstone clock cannot follow them, because
 * a diode's whole machinery is a signal going in and coming out again, and a clock has no input.
 * That leaves this class with one subclass — see spec/blocks/abstract-redstone-plate.md.
 */
public abstract class RedstonePlateBlock extends HorizontalDirectionalBlock {
	protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

	protected RedstonePlateBlock(Properties properties) {
		super(properties);
	}

	/** The strength this plate currently emits from its front face, 0-15. */
	protected abstract int outputSignal(BlockState state);

	/** Right-click: advance the block to its next setting. */
	protected abstract InteractionResult onClick(BlockState state, Level level, BlockPos pos, Player player);

	// ------------------------------------------------------------------ placement

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// The output points away from whoever placed it, like a repeater.
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return Block.canSupportRigidBlock(level, pos.below());
	}

	@Override
	protected BlockState updateShape(
			BlockState state,
			LevelReader level,
			ScheduledTickAccess tickAccess,
			BlockPos pos,
			Direction direction,
			BlockPos neighbourPos,
			BlockState neighbourState,
			RandomSource random
	) {
		if (direction == Direction.DOWN && !this.canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}

		return super.updateShape(state, level, tickAccess, pos, direction, neighbourPos, neighbourState, random);
	}

	// ------------------------------------------------------------------ signal

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return direction == state.getValue(FACING) ? this.outputSignal(state) : 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		// Strong power to the block in front, exactly like a repeater's target.
		return this.getSignal(state, level, pos, direction);
	}

	/**
	 * Dust connects to the output face and to nothing else.
	 *
	 * <p>Vanilla's default is to let dust connect to every side of anything that emits a signal,
	 * which on a plate draws a wire into faces that never carry one. A repeater carries the same
	 * override, for its own pair of useful faces; it lives on {@code BlockBehaviour}, not on
	 * {@code DiodeBlock}, so each block states its own answer.
	 *
	 * <p>The convention is the one {@link #getSignal} already uses: {@code direction} runs from the
	 * asking block towards this one, so the front face is {@code direction == FACING}.
	 *
	 * <p>A subclass that reads dust from another face has to widen this, or a builder will wire a
	 * signal the block silently ignores.
	 */
	@Override
	protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return direction == state.getValue(FACING);
	}

	// ------------------------------------------------------------------ interaction

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		// One click, one cycle through every setting. Sneaking is deliberately not a second
		// interaction: vanilla only delivers a sneaking click to a block when the player's hands
		// are empty, so anything bound to it is unreachable mid-build, with a stack in hand.
		return this.onClick(state, level, pos, player);
	}
}
