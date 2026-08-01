package com.silverfish_expansion.colony;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.silverfish_expansion.SilverfishExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks vanilla infested blocks created by larvae, with game-time-based maturation:
 * larva → juvenile vanilla silverfish → adult colony variant.
 */
public class ColonyInfestationData extends SavedData {
	/** Still a larva if broken before this age. */
	public static final long TICKS_TO_JUVENILE = 3L * 60L * 20L;
	/** Adult variants if broken after this age. */
	public static final long TICKS_TO_ADULT = 10L * 60L * 20L;

	private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
			Codec.LONG.fieldOf("infest_time").forGetter(Entry::infestGameTime)
	).apply(instance, Entry::new));

	public static final Codec<ColonyInfestationData> CODEC = ENTRY_CODEC.listOf()
			.xmap(ColonyInfestationData::new, data -> data.entries.values().stream().map(e ->
					new Entry(BlockPos.of(e.packedPos()), e.infestGameTime())).toList());

	public static final SavedDataType<ColonyInfestationData> TYPE = new SavedDataType<>(
			SilverfishExpansion.MOD_ID + "_colony_infestations",
			ColonyInfestationData::new,
			CODEC,
			DataFixTypes.SAVED_DATA_SCOREBOARD
	);

	private final Map<Long, PackedEntry> entries = new HashMap<>();

	public ColonyInfestationData() {
	}

	private ColonyInfestationData(List<Entry> list) {
		for (Entry entry : list) {
			entries.put(entry.pos().asLong(), new PackedEntry(entry.pos().asLong(), entry.infestGameTime()));
		}
	}

	public static ColonyInfestationData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public void register(BlockPos pos, long gameTime) {
		entries.put(pos.asLong(), new PackedEntry(pos.asLong(), gameTime));
		setDirty();
	}

	public boolean contains(BlockPos pos) {
		return entries.containsKey(pos.asLong());
	}

	/** Removes tracking; returns infest game time, or -1 if not tracked. */
	public long remove(BlockPos pos) {
		PackedEntry removed = entries.remove(pos.asLong());
		if (removed == null) {
			return -1L;
		}
		setDirty();
		return removed.infestGameTime();
	}

	public static Maturity stageFor(long infestGameTime, long now) {
		long age = Math.max(0L, now - infestGameTime);
		if (age < TICKS_TO_JUVENILE) {
			return Maturity.LARVA;
		}
		if (age < TICKS_TO_ADULT) {
			return Maturity.JUVENILE;
		}
		return Maturity.ADULT;
	}

	/** Drop stale entries whose block is no longer infested. */
	public void cleanup(ServerLevel level) {
		boolean changed = entries.entrySet().removeIf(entry -> {
			BlockPos pos = BlockPos.of(entry.getKey());
			if (!level.isLoaded(pos)) {
				return false;
			}
			return !(level.getBlockState(pos).getBlock() instanceof InfestedBlock);
		});
		if (changed) {
			setDirty();
		}
	}

	public enum Maturity {
		LARVA,
		JUVENILE,
		ADULT
	}

	private record Entry(BlockPos pos, long infestGameTime) {
	}

	private record PackedEntry(long packedPos, long infestGameTime) {
	}
}
