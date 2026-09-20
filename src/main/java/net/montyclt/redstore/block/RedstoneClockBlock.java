package net.montyclt.redstore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;

/**
 * A plate that emits a periodic signal, replacing a vanilla repeater loop and its lever.
 *
 * <p>It runs by default and a redstone signal on its back stops it. The setting N always means the
 * same thing — the period is 2N redstone ticks — and the mode only chooses the duty cycle: half on
 * and half off, or a single one-tick pulse.
 *
 * <p>See spec/blocks/redstone-clock.md.
 */
public class RedstoneClockBlock extends RedstonePlateBlock {
	public static final IntegerProperty DELAY = BlockStateProperties.DELAY;
	public static final BooleanProperty PULSE = BooleanProperty.create("pulse");
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;

	private static final int TICKS_PER_REDSTONE_TICK = 2;

	public RedstoneClockBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(DELAY, 1)
				.setValue(PULSE, false)
				.setValue(POWERED, false)
				.setValue(LOCKED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(DELAY, PULSE, POWERED, LOCKED);
	}

	@Override
	protected int outputSignal(BlockState state) {
		return state.getValue(POWERED) ? 15 : 0;
	}

	// ------------------------------------------------------------------ running

	/**
	 * A diode pointing at either side face stops the clock — the same mechanism, and the same
	 * rule, that locks a repeater. See {@link RedstonePlateBlock#readLock}.
	 *
	 * <p>The back is the block's input in vanilla's {@code FACING} convention and the front is its
	 * output, so neither of them can be used: reading the front would let the clock's own output
	 * switch it off the moment anything was wired to it.
	 */
	private boolean sidePowered(Level level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(FACING);

		return this.readLock(level, pos, facing.getClockWise()) > 0
				|| this.readLock(level, pos, facing.getCounterClockWise()) > 0;
	}

	/** Game ticks the phase the block is entering should last. */
	private int phaseLength(BlockState state, boolean onPhase) {
		int n = state.getValue(DELAY);

		if (!state.getValue(PULSE)) {
			return TICKS_PER_REDSTONE_TICK * n;
		}

		// Same period, different duty cycle: one redstone tick on, the rest off.
		return onPhase ? TICKS_PER_REDSTONE_TICK : TICKS_PER_REDSTONE_TICK * (2 * n - 1);
	}

	/**
	 * Start running: schedule the next flip <em>before</em> writing the state.
	 *
	 * <p>Order matters. {@code setBlock} notifies the neighbours synchronously, a powered dust in
	 * front notifies us straight back, and anything that inspected the tick queue in between would
	 * see no pending tick and start a second clock on top of this one. That is what made the block
	 * freeze on as soon as its output was wired to anything.
	 */
	private void start(Level level, BlockPos pos, BlockState state) {
		BlockState running = state.setValue(POWERED, true);

		if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
			level.scheduleTick(pos, this, this.phaseLength(running, true));
		}

		level.setBlock(pos, running, Block.UPDATE_ALL);
	}

	private void stop(Level level, BlockPos pos, BlockState state) {
		// A tick that is already pending will fire, see the block locked, and not reschedule.
		level.setBlock(pos, state.setValue(LOCKED, true).setValue(POWERED, false), Block.UPDATE_ALL);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (oldState.is(state.getBlock())) {
			return;
		}

		if (this.sidePowered(level, pos, state)) {
			this.stop(level, pos, state);
		} else {
			this.start(level, pos, state);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock, Orientation orientation, boolean movedByPiston) {
		boolean locked = this.sidePowered(level, pos, state);

		// The only thing a neighbour can tell this block is whether it is locked now. Reacting to
		// anything else would mean reacting to our own output, which is a loop.
		if (locked == state.getValue(LOCKED)) {
			return;
		}

		if (locked) {
			this.stop(level, pos, state);
		} else {
			this.start(level, pos, state.setValue(LOCKED, false));
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(LOCKED)) {
			if (state.getValue(POWERED)) {
				level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
			}

			return;
		}

		boolean on = !state.getValue(POWERED);
		level.scheduleTick(pos, this, this.phaseLength(state, on));
		level.setBlock(pos, state.setValue(POWERED, on), Block.UPDATE_ALL);
	}

	// ------------------------------------------------------------------ interaction

	/**
	 * Right-click walks the eight settings in order: 1-4 as a square wave, then 1-4 as a pulse,
	 * then round again. There is no second interaction; see {@link RedstonePlateBlock}.
	 */
	@Override
	protected InteractionResult onClick(BlockState state, Level level, BlockPos pos, Player player) {
		int delay = state.getValue(DELAY);
		boolean pulse = state.getValue(PULSE);

		if (delay < 4) {
			delay++;
		} else {
			delay = 1;
			pulse = !pulse;
		}

		level.setBlock(pos, state.setValue(DELAY, delay).setValue(PULSE, pulse), Block.UPDATE_ALL);

		// The same four pitches a repeater uses for its four settings, a tone higher for pulses.
		float pitch = (pulse ? 0.7F : 0.5F) + delay * 0.05F;
		level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, pitch);
		this.announceMode(player, pulse);

		return InteractionResult.SUCCESS;
	}

	private void announceMode(Player player, boolean pulse) {
		if (player instanceof ServerPlayer serverPlayer) {
			// true = action bar rather than chat.
			serverPlayer.sendSystemMessage(Component.translatable(pulse
					? "message.redstore.clock.mode.pulse"
					: "message.redstore.clock.mode.square"), true);
		}
	}
}
