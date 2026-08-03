package com.silverfish_expansion.worldgen;

import com.silverfish_expansion.SilverfishExpansion;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class ModStructures {
	public static final StructureType<BreedingChamberStructure> BREEDING_CHAMBER = Registry.register(
			BuiltInRegistries.STRUCTURE_TYPE,
			id("breeding_chamber"),
			() -> BreedingChamberStructure.CODEC
	);

	public static final StructurePieceType BREEDING_CHAMBER_PIECE = Registry.register(
			BuiltInRegistries.STRUCTURE_PIECE,
			id("breeding_chamber"),
			(StructurePieceType.ContextlessType) BreedingChamberPiece::new
	);

	private ModStructures() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(SilverfishExpansion.MOD_ID, path);
	}

	public static void register() {
		// Static fields register on class load.
	}
}
