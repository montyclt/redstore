package net.montyclt.redstore.menu;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.montyclt.redstore.Redstore;
import net.montyclt.redstore.blockentity.FilterHopperBlockEntity;
import net.montyclt.redstore.registry.RedstoreMenus;

public class FilterHopperMenu extends AbstractContainerMenu {
	public static final int BUTTON_TOGGLE_BLACKLIST = 0;
	public static final int BUTTON_TOGGLE_STRICT = 1;

	public static final int DATA_BLACKLIST = 0;
	public static final int DATA_STRICT = 1;
	public static final int DATA_COUNT = 2;

	public static final int STORAGE_SLOTS = FilterHopperBlockEntity.CONTAINER_SIZE;

	/** The placeholder the empty filter slot draws: a funnel, in the flat grey of a slot icon. */
	private static final Identifier FILTER_ICON = Redstore.id("container/slot/filter");

	/**
	 * Screen coordinates. The three things that make up the filter — the item, the whitelist
	 * toggle and the strict toggle — sit together on the left, then a gap, then the five storage
	 * slots. Grouping them stops the toggles from reading as part of the storage row.
	 */
	public static final int FILTER_SLOT_X = 8;
	public static final int FILTER_SLOT_Y = 20;
	public static final int STORAGE_START_X = 78;
	public static final int STORAGE_START_Y = 20;
	public static final int INVENTORY_START_X = 8;
	public static final int INVENTORY_START_Y = 51;

	private static final int STORAGE_START = 0;
	private static final int STORAGE_END = STORAGE_START + STORAGE_SLOTS;
	/** Public because the screen has to recognise the slot to explain it. */
	public static final int FILTER_SLOT = STORAGE_END;
	private static final int INVENTORY_START = FILTER_SLOT + 1;
	private static final int INVENTORY_END = INVENTORY_START + Inventory.INVENTORY_SIZE;

	private final Container storage;
	private final Container filter;
	private final ContainerData data;

	/** Client side: empty stand-ins, filled in by the usual container sync. */
	public FilterHopperMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(STORAGE_SLOTS), new SimpleContainer(1), new SimpleContainerData(DATA_COUNT));
	}

	public FilterHopperMenu(int containerId, Inventory inventory, Container storage, Container filter, ContainerData data) {
		super(RedstoreMenus.FILTER_HOPPER, containerId);

		checkContainerSize(storage, STORAGE_SLOTS);
		checkContainerSize(filter, 1);
		checkContainerDataCount(data, DATA_COUNT);

		this.storage = storage;
		this.filter = filter;
		this.data = data;

		storage.startOpen(inventory.player);

		for (int i = 0; i < STORAGE_SLOTS; i++) {
			final int index = i;

			this.addSlot(new Slot(storage, index, STORAGE_START_X + i * SLOT_SIZE, STORAGE_START_Y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					// The filter governs what a player may drop in, exactly as it governs hoppers.
					return storage.canPlaceItem(index, stack);
				}
			});
		}

		this.addSlot(new Slot(filter, 0, FILTER_SLOT_X, FILTER_SLOT_Y) {
			@Override
			public int getMaxStackSize() {
				// One item is enough to describe a filter; no point burying a stack in there.
				return 1;
			}

			@Override
			public Identifier getNoItemIcon() {
				return FILTER_ICON;
			}
		});

		this.addStandardInventorySlots(inventory, INVENTORY_START_X, INVENTORY_START_Y);

		this.addDataSlots(data);
	}

	public boolean isBlacklist() {
		return this.data.get(DATA_BLACKLIST) != 0;
	}

	public boolean isStrict() {
		return this.data.get(DATA_STRICT) != 0;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (!this.stillValid(player) || !(this.storage instanceof FilterHopperBlockEntity hopper)) {
			return false;
		}

		switch (id) {
			case BUTTON_TOGGLE_BLACKLIST -> hopper.setBlacklist(!hopper.isBlacklist());
			case BUTTON_TOGGLE_STRICT -> hopper.setStrict(!hopper.isStrict());
			default -> {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);

		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (slotIndex < INVENTORY_START) {
			// Storage or filter slot -> player inventory.
			if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, STORAGE_START, STORAGE_END, false)) {
			// Player inventory -> storage only. Shift-clicking never sets the filter, so an
			// accidental shift-click cannot change what the hopper accepts.
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.storage.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.storage.stopOpen(player);
	}
}
