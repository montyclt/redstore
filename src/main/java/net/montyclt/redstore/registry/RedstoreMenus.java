package net.montyclt.redstore.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import net.montyclt.redstore.Redstore;
import net.montyclt.redstore.menu.FilterHopperMenu;

public final class RedstoreMenus {
	public static final MenuType<FilterHopperMenu> FILTER_HOPPER = register("filter_hopper", FilterHopperMenu::new);

	public static void initialize() {
	}

	private static <T extends AbstractContainerMenu> MenuType<T> register(String name, MenuType.MenuSupplier<T> constructor) {
		return Registry.register(BuiltInRegistries.MENU, Redstore.id(name), new MenuType<>(constructor, FeatureFlagSet.of()));
	}

	private RedstoreMenus() {
	}
}
