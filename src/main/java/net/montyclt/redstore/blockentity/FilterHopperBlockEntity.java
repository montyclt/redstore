package net.montyclt.redstore.blockentity;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.montyclt.redstore.Redstore;
import net.montyclt.redstore.menu.FilterHopperMenu;
import net.montyclt.redstore.registry.RedstoreBlockEntities;

public class FilterHopperBlockEntity extends BlockEntity implements Hopper, WorldlyContainer, MenuProvider {
	public static final int CONTAINER_SIZE = 5;
	public static final int TRANSFER_COOLDOWN = 8;

	private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4};

	private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

	/**
	 * The filter item lives outside the hopper's own container on purpose: {@link #getContainerSize()}
	 * stays at 5, so no transfer logic can ever see it, move it or count it for a comparator.
	 */
	private final SimpleContainer filterContainer = new SimpleContainer(1) {
		@Override
		public void setChanged() {
			super.setChanged();
			FilterHopperBlockEntity.this.setChanged();
			FilterHopperBlockEntity.this.sendFilterToClients();
		}
	};

	private boolean blacklist;
	private boolean strict;
	private int transferCooldown = -1;

	private final ContainerData dataAccess = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case FilterHopperMenu.DATA_BLACKLIST -> blacklist ? 1 : 0;
				case FilterHopperMenu.DATA_STRICT -> strict ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case FilterHopperMenu.DATA_BLACKLIST -> blacklist = value != 0;
				case FilterHopperMenu.DATA_STRICT -> strict = value != 0;
				default -> {
				}
			}
		}

		@Override
		public int getCount() {
			return FilterHopperMenu.DATA_COUNT;
		}
	};

	public FilterHopperBlockEntity(BlockPos pos, BlockState state) {
		super(RedstoreBlockEntities.FILTER_HOPPER, pos, state);
	}

	// ------------------------------------------------------------------ filtering

	/** The single rule the whole block exists for. */
	public boolean filterAccepts(ItemStack stack) {
		ItemStack filter = this.getFilterStack();

		if (filter.isEmpty()) {
			return true;
		}

		boolean matches = this.strict
				? ItemStack.isSameItemSameComponents(filter, stack)
				: filter.is(stack.getItem());

		return this.blacklist != matches;
	}

	public ItemStack getFilterStack() {
		return this.filterContainer.getItem(0);
	}

	public Container getFilterContainer() {
		return this.filterContainer;
	}

	public ContainerData getDataAccess() {
		return this.dataAccess;
	}

	public boolean isBlacklist() {
		return this.blacklist;
	}

	public boolean isStrict() {
		return this.strict;
	}

	public void setBlacklist(boolean blacklist) {
		this.blacklist = blacklist;
		this.setChanged();
	}

	public void setStrict(boolean strict) {
		this.strict = strict;
		this.setChanged();
	}

	// ------------------------------------------------------------------ ticking

	public static void serverTick(Level level, BlockPos pos, BlockState state, FilterHopperBlockEntity hopper) {
		hopper.transferCooldown--;

		if (hopper.transferCooldown > 0) {
			return;
		}

		hopper.transferCooldown = 0;

		if (!state.getValue(HopperBlock.ENABLED)) {
			return;
		}

		boolean moved = false;

		if (!hopper.isEmpty()) {
			moved = HopperLogic.ejectItems(level, pos, state, hopper);
		}

		if (!hopper.isFull()) {
			// Vanilla's own input side: it pulls from the container above and picks up item
			// entities, and it routes every insertion through canPlaceItem, so the filter applies.
			moved |= HopperBlockEntity.suckInItems(level, hopper);
		}

		if (moved) {
			hopper.transferCooldown = TRANSFER_COOLDOWN;
			hopper.setChanged();
		}
	}

	public void setTransferCooldown(int cooldown) {
		this.transferCooldown = cooldown;
	}

	public boolean isOnCooldown() {
		return this.transferCooldown > 0;
	}

	public boolean isFull() {
		for (ItemStack stack : this.items) {
			if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * {@link BlockEntity#preRemoveSideEffects} already drops the contents of any block entity that
	 * is a {@link Container}, which covers the five storage slots. The filter lives in a container
	 * of its own, so it is the only thing left to drop.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);

		if (this.level != null) {
			Containers.dropContents(this.level, pos, this.filterContainer);
		}
	}

	// ------------------------------------------------------------------ client sync

	/**
	 * The block draws its filter on its own sides, so a change has to reach whoever is looking at
	 * it. Nothing else about this block entity is worth a packet: the five storage slots are
	 * server business, and a sorter is several hundred hoppers moving an item every eight ticks.
	 */
	private void sendFilterToClients() {
		if (this.level == null || this.level.isClientSide() || !this.level.isLoaded(this.getBlockPos())) {
			return;
		}

		BlockState state = this.getBlockState();
		// UPDATE_CLIENTS and not UPDATE_ALL: a filter change is a picture, not a signal, and the
		// neighbours have nothing to recompute.
		this.level.sendBlockUpdated(this.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** Only the filter travels; {@link #loadAdditional} reads it back on the client. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		try (ProblemReporter.ScopedCollector problems =
				new ProblemReporter.ScopedCollector(this.problemPath(), Redstore.LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(problems, registries);
			output.store("Filter", ItemStack.CODEC, this.getFilterStack());

			return output.buildResult();
		}
	}

	// ------------------------------------------------------------------ persistence

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		// TODO(verify 26.3): ValueOutput#store / ValueInput#read for a single ItemStack.
		output.store("Filter", ItemStack.CODEC, this.getFilterStack());
		output.putBoolean("Blacklist", this.blacklist);
		output.putBoolean("Strict", this.strict);
		output.putInt("TransferCooldown", this.transferCooldown);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		ContainerHelper.loadAllItems(input, this.items);
		this.filterContainer.setItem(0, input.read("Filter", ItemStack.CODEC).orElse(ItemStack.EMPTY));
		this.blacklist = input.getBooleanOr("Blacklist", false);
		this.strict = input.getBooleanOr("Strict", false);
		this.transferCooldown = input.getIntOr("TransferCooldown", -1);
	}

	// ------------------------------------------------------------------ hopper

	@Override
	public double getLevelX() {
		return this.worldPosition.getX() + 0.5D;
	}

	@Override
	public double getLevelY() {
		return this.worldPosition.getY() + 0.5D;
	}

	@Override
	public double getLevelZ() {
		return this.worldPosition.getZ() + 0.5D;
	}

	@Override
	public boolean isGridAligned() {
		return true;
	}

	// ------------------------------------------------------------------ container

	@Override
	public int getContainerSize() {
		return CONTAINER_SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : this.items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		return ContainerHelper.removeItem(this.items, slot, amount);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(this.items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.items.set(slot, stack);
		stack.limitSize(this.getMaxStackSize(stack));
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	/** The filter, applied to every insertion path vanilla knows about. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return this.filterAccepts(stack);
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return ALL_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return this.filterAccepts(stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		// Extraction is never filtered: whatever is inside can always leave.
		return true;
	}

	// ------------------------------------------------------------------ menu

	@Override
	@NonNull
	public Component getDisplayName() {
		return Component.translatable("container.redstore.filter_hopper");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new FilterHopperMenu(containerId, inventory, this, this.filterContainer, this.dataAccess);
	}
}
