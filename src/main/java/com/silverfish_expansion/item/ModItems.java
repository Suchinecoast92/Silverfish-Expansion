package com.silverfish_expansion.item;

import com.silverfish_expansion.SilverfishExpansion;
import com.silverfish_expansion.entity.ModEntities;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import java.util.function.Function;

public final class ModItems {
	public static final SpawnEggItem ARANEO_SILVERFISH_SPAWN_EGG = registerSpawnEgg(
			"araneo_silverfish_spawn_egg", ModEntities.ARANEO_SILVERFISH);
	public static final SpawnEggItem MANTIS_SILVERFISH_SPAWN_EGG = registerSpawnEgg(
			"mantis_silverfish_spawn_egg", ModEntities.MANTIS_SILVERFISH);
	public static final SpawnEggItem FLY_SILVERFISH_SPAWN_EGG = registerSpawnEgg(
			"fly_silverfish_spawn_egg", ModEntities.FLY_SILVERFISH);

	private ModItems() {
	}

	private static SpawnEggItem registerSpawnEgg(String name, net.minecraft.world.entity.EntityType<?> type) {
		return register(name, SpawnEggItem::new, new Item.Properties().spawnEgg(type));
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> itemKey = ResourceKey.create(
				Registries.ITEM,
				Identifier.fromNamespaceAndPath(SilverfishExpansion.MOD_ID, name)
		);
		T item = factory.apply(properties.setId(itemKey));
		return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(itemGroup -> {
			itemGroup.accept(ARANEO_SILVERFISH_SPAWN_EGG);
			itemGroup.accept(MANTIS_SILVERFISH_SPAWN_EGG);
			itemGroup.accept(FLY_SILVERFISH_SPAWN_EGG);
		});
	}
}
