package com.silverfish_expansion.block;

import com.silverfish_expansion.SilverfishExpansion;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

public final class ModBlocks {
	public static final Block SILVERFISH_EGGS = register(
			"silverfish_eggs",
			SilverfishEggsBlock::new,
			SilverfishEggsBlock.createProperties()
	);

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(
				Registries.BLOCK,
				Identifier.fromNamespaceAndPath(SilverfishExpansion.MOD_ID, name)
		);
		Block block = factory.apply(properties.setId(blockKey));

		ResourceKey<Item> itemKey = ResourceKey.create(
				Registries.ITEM,
				Identifier.fromNamespaceAndPath(SilverfishExpansion.MOD_ID, name)
		);
		BlockItem blockItem = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);

		return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
	}

	public static void register() {
		// Static fields register on class load.
	}
}
