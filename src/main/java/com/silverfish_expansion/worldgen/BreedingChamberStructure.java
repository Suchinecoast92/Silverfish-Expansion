package com.silverfish_expansion.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A silverfish colony's breeding chamber: an irregular cavity carved into deep rock,
 * decorated only with blocks the world already generates down there.
 */
public class BreedingChamberStructure extends Structure {
	public static final MapCodec<BreedingChamberStructure> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					settingsCodec(instance),
					HeightProvider.CODEC.fieldOf("height").forGetter(structure -> structure.height)
			).apply(instance, BreedingChamberStructure::new)
	);

	/** Rock that must remain between the chamber roof and the surface. */
	private static final int MIN_ROCK_COVER = 22;

	private final HeightProvider height;

	public BreedingChamberStructure(Structure.StructureSettings settings, HeightProvider height) {
		super(settings);
		this.height = height;
	}

	@Override
	protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
		WorldgenRandom random = context.random();
		int x = context.chunkPos().getMiddleBlockX();
		int z = context.chunkPos().getMiddleBlockZ();

		WorldGenerationContext heightContext = new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor());
		int y = this.height.sample(random, heightContext);

		int surface = context.chunkGenerator()
				.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
		if (y + MIN_ROCK_COVER > surface) {
			return Optional.empty();
		}

		int minY = context.heightAccessor().getMinY();
		if (y - BreedingChamberPiece.MAX_VERTICAL_REACH < minY) {
			return Optional.empty();
		}

		BlockPos origin = new BlockPos(x, y, z);
		long seed = random.nextLong();
		return Optional.of(new Structure.GenerationStub(
				origin,
				builder -> builder.addPiece(new BreedingChamberPiece(origin, seed))
		));
	}

	@Override
	public StructureType<?> type() {
		return ModStructures.BREEDING_CHAMBER;
	}
}
