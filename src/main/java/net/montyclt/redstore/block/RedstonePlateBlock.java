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
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The flat, repeater-sized components: the logic gates and the redstone clock.
 *
 * <p>Everything they share lives here — the plate shape and its placement rules, {@code facing}
 * pointing at the output, the rule for reading a signal off a face, the front-only strong output,
 * and the click grammar where a plain right-click changes the block's main property and a sneaking
 * one switches its mode.
 *
 * <p>See spec/blocks/abstract-redstone-plate.md.
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

	/**
	 * Read one face the way a comparator reads its sides: dust, blocks of redstone and anything
	 * aimed at us, but never a powered block and never through one. See the spec, section 4.1, for
	 * why the permissive rule is wrong for a block whose inputs are its sides.
	 */
	protected int readFace(SignalGetter level, BlockPos pos, Direction side) {
		return level.getControlInputSignal(pos.relative(side), side, false);
	}

	/**
	 * Read one face the way a repeater reads the sides that lock it: only a diode pointing at us
	 * counts, and nothing else does, however strongly powered it is.
	 *
	 * <p>Vanilla expresses this as a type check rather than a signal strength —
	 * {@code DiodeBlock.isDiode(state)}, which is {@code getBlock() instanceof DiodeBlock} — and
	 * then reads that block's direct signal towards us. A block of redstone against the side does
	 * nothing because it is not a diode.
	 *
	 * <p>No widening is needed: Redstore's gates extend {@link DiodeBlock}, so vanilla's own check
	 * already accepts them.
	 */
	protected int readLock(SignalGetter level, BlockPos pos, Direction side) {
		return level.getControlInputSignal(pos.relative(side), side, true);
	}

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

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
