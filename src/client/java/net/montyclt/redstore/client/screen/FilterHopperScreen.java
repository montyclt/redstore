package net.montyclt.redstore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import net.montyclt.redstore.menu.FilterHopperMenu;

/**
 * Deliberately textureless for now: the screen reuses the vanilla hopper sheet and copies one of
 * its own slot frames to draw the extra filter slot, so the mod ships no GUI art yet.
 */
public class FilterHopperScreen extends AbstractContainerScreen<FilterHopperMenu> {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/hopper.png");

	/** What the funnel placeholder means, for a slot that behaves like no other slot in the game. */
	private static final Component FILTER_HINT = Component.translatable("gui.redstore.filter.slot_hint");

	/** A slot frame inside the vanilla hopper sheet, so we can stamp our own wherever we want. */
	private static final int SLOT_FRAME_U = 43;
	private static final int SLOT_FRAME_V = 19;

	/** A patch of blank panel in the sheet's right-hand gutter, used to erase the painted slots. */
	private static final int BLANK_U = 140;
	private static final int BLANK_V = 19;

	private static final int SLOT_FRAME_SIZE = 18;

	/** Where the vanilla sheet paints its own five slot frames. */
	private static final int VANILLA_SLOTS_X = 43;
	private static final int SLOTS_Y = 19;

	private static final int WIDTH = 176;
	private static final int HEIGHT = 133;

	private static final int BUTTON_SIZE = 18;
	private static final int MODE_BUTTON_X = 26;
	private static final int STRICT_BUTTON_X = 44;
	private static final int BUTTON_Y = 19;

	private Button modeButton;
	private Button strictButton;

	public FilterHopperScreen(FilterHopperMenu menu, Inventory inventory, Component title) {
		// imageWidth/imageHeight are final in 26.3; they come in through the constructor.
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	/**
	 * Vanilla only writes a tooltip for a slot that holds something, which is exactly backwards
	 * here: the filter slot is at its most puzzling while it is empty. So the hint is drawn for
	 * the empty slot, and dropped again as soon as it holds an item or the player is carrying one.
	 */
	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);

		if (this.hoveredSlot != null
				&& this.hoveredSlot.index == FilterHopperMenu.FILTER_SLOT
				&& !this.hoveredSlot.hasItem()
				&& this.menu.getCarried().isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, FILTER_HINT, mouseX, mouseY);
		}
	}

	@Override
	protected void init() {
		super.init();

		this.modeButton = Button.builder(Component.empty(), button -> this.press(FilterHopperMenu.BUTTON_TOGGLE_BLACKLIST))
				.bounds(this.leftPos + MODE_BUTTON_X, this.topPos + BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)
				.build();

		this.strictButton = Button.builder(Component.empty(), button -> this.press(FilterHopperMenu.BUTTON_TOGGLE_STRICT))
				.bounds(this.leftPos + STRICT_BUTTON_X, this.topPos + BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)
				.build();

		this.addRenderableWidget(this.modeButton);
		this.addRenderableWidget(this.strictButton);

		this.refreshButtons();
	}

	private void press(int buttonId) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.refreshButtons();
	}

	private void refreshButtons() {
		boolean blacklist = this.menu.isBlacklist();
		boolean strict = this.menu.isStrict();

		this.modeButton.setMessage(Component.translatable(blacklist
				? "gui.redstore.filter.mode.short.blacklist"
				: "gui.redstore.filter.mode.short.whitelist"));
		this.modeButton.setTooltip(Tooltip.create(Component.translatable(blacklist
				? "gui.redstore.filter.mode.blacklist"
				: "gui.redstore.filter.mode.whitelist")));

		this.strictButton.setMessage(Component.translatable(strict
				? "gui.redstore.filter.strict.short.on"
				: "gui.redstore.filter.strict.short.off"));
		this.strictButton.setTooltip(Tooltip.create(Component.translatable(strict
				? "gui.redstore.filter.strict.on"
				: "gui.redstore.filter.strict.off")));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);

		graphics.blit(
				RenderPipelines.GUI_TEXTURED, BACKGROUND,
				this.leftPos, this.topPos, 0.0F, 0.0F,
				this.imageWidth, this.imageHeight,
				BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT
		);

		// The sheet paints five slot frames in the middle; our slots are somewhere else, so erase
		// that strip with blank panel copied from the sheet's own gutter...
		for (int i = 0; i < FilterHopperMenu.STORAGE_SLOTS; i++) {
			this.patch(graphics, VANILLA_SLOTS_X + i * SLOT_FRAME_SIZE, SLOTS_Y, BLANK_U, BLANK_V);
		}

		// ...and stamp the frames back where this menu actually puts them: the filter item on the
		// left, next to its two toggles, then the five storage slots.
		this.patch(graphics, FilterHopperMenu.FILTER_SLOT_X - 1, SLOTS_Y, SLOT_FRAME_U, SLOT_FRAME_V);

		for (int i = 0; i < FilterHopperMenu.STORAGE_SLOTS; i++) {
			int x = FilterHopperMenu.STORAGE_START_X - 1 + i * SLOT_FRAME_SIZE;
			this.patch(graphics, x, SLOTS_Y, SLOT_FRAME_U, SLOT_FRAME_V);
		}
	}

	private void patch(GuiGraphicsExtractor graphics, int x, int y, int u, int v) {
		graphics.blit(
				RenderPipelines.GUI_TEXTURED, BACKGROUND,
				this.leftPos + x, this.topPos + y, (float) u, (float) v,
				SLOT_FRAME_SIZE, SLOT_FRAME_SIZE,
				BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT
		);
	}
}
