package net.montyclt.redstore;

import net.minecraft.client.gui.screens.MenuScreens;

import net.fabricmc.api.ClientModInitializer;

import net.montyclt.redstore.client.screen.FilterHopperScreen;
import net.montyclt.redstore.registry.RedstoreMenus;

public class RedstoreClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(RedstoreMenus.FILTER_HOPPER, FilterHopperScreen::new);
	}
}
