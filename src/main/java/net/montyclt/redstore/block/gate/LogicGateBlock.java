package net.montyclt.redstore.block.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A two-input logic gate: the same class for all three, differing only in the operation they carry.
 *
 * <p>This extends vanilla's {@link DiodeBlock} because a gate <em>is</em> a diode — a flat plate
 * that reads redstone, waits a tick and emits from its front — and inheritance is an is-a
 * relationship. Everything that makes it one comes from there: the plate's shape and placement,
 * the front-only strong output, the delay, the tick scheduling and its priority, and the type check
 * that lets a repeater be locked by the things pointing at its side. Being a real diode is also
 * what lets a Redstore gate lock a vanilla repeater, which a look-alike could never do.
 *
 * <p>Only two hooks are ours: what the gate computes, and how long it takes to do it.
 *
 * <p>See spec/blocks/abstract-logic-gate.md.
 */
public class LogicGateBlock extends DiodeBlock {
	public static final BooleanProperty INVERTED = BooleanProperty.create("inverted");

	/** One redstone tick, like a repeater on its minimum setting. */
	private static final int DELAY = 2;

	private final GateOperation operation;

	public LogicGateBlock(GateOperation operation, Properties properties) {
		super(properties);
		this.operation = operation;
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(INVERTED, false)
				.setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, INVERTED, POWERED);
	}

	@Override
	protected int getDelay(BlockState state) {
		return DELAY;
	}

	/**
	 * Read the side faces the way a comparator reads its own, not the way a repeater's lock does:
	 * these are inputs carrying a value, not a mechanism holding the block shut. See
	 * spec/blocks/abstract-redstone-plate.md section 4.
	 */
	@Override
	protected boolean sideInputDiodesOnly() {
		return false;
	}

	/**
	 * The gate itself. {@code FACING} points at the back in vanilla's diode convention, so the two
	 * inputs are simply the faces to either side of it, and inversion flips the answer.
	 *
	 * <p>Note that an inverted OR or XOR comes out high with no inputs at all, exactly like a
	 * redstone torch on an unpowered block. That is intended and needs no special case.
	 */
	@Override
	protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(FACING);
		Direction left = facing.getCounterClockWise();
		Direction right = facing.getClockWise();

		int a = level.getControlInputSignal(pos.relative(left), left, false);
		int b = level.getControlInputSignal(pos.relative(right), right, false);

		return this.operation.test(a, b) != state.getValue(INVERTED);
	}

	/**
	 * Dust connects to the two inputs and the output, and not to the back.
	 *
	 * <p>Vanilla's default is to let dust connect to every side of anything that emits a signal.
	 * On a gate that draws a wire into the one face which neither reads nor emits, suggesting an
	 * input that does not exist. {@code RepeaterBlock} overrides this for the same reason, and
	 * {@code DiodeBlock} does not carry the override, because a repeater's two useful faces are
	 * not a gate's three.
	 *
	 * <p>The convention is the one {@code getSignal} uses: {@code direction} runs from the asking
	 * block towards this one, so the back — {@code FACING} in vanilla's diode convention — is
	 * {@code direction == FACING.getOpposite()}.
	 */
	@Override
	protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return direction != null && direction != state.getValue(FACING).getOpposite();
	}

	/** Right-click toggles the negation, which is the gate's only setting. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		BlockState next = state.cycle(INVERTED);
		level.setBlock(pos, next, Block.UPDATE_ALL);

		// The answer may have changed, so put the block back through the diode state machine.
		this.checkTickOnNeighbor(level, pos, next);

		level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F,
				next.getValue(INVERTED) ? 0.55F : 0.5F);

		return InteractionResult.SUCCESS;
	}
}
