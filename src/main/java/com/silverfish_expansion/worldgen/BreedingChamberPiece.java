package com.silverfish_expansion.worldgen;

import com.silverfish_expansion.SilverfishExpansion;
import com.silverfish_expansion.block.ModBlocks;
import com.silverfish_expansion.block.SilverfishEggsBlock;
import com.silverfish_expansion.colony.ColonyInfestationData;
import com.silverfish_expansion.entity.ExpandedSilverfishEntity;
import com.silverfish_expansion.entity.ModEntities;
import com.silverfish_expansion.entity.SilverfishVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Carves and dresses the breeding chamber.
 *
 * <p>The cavity is a union of overlapping metaball lobes (the same trick vanilla uses for
 * amethyst geodes) pushed around by value noise, so it never reads as a designed room. Every
 * decision is a pure function of the piece seed and the absolute block position, which is what
 * keeps the result seamless: neighbouring chunks are placed by separate calls and must agree
 * without ever reading each other.
 */
public class BreedingChamberPiece extends StructurePiece {
	/** Deepest the chamber can reach below its origin; the structure uses it to stay in the world. */
	public static final int MAX_VERTICAL_REACH = 22;

	private static final int FLAGS = Block.UPDATE_CLIENTS;

	/** Lobes are wider than they are tall, which is what gives the low cave ceiling. */
	private static final double VERTICAL_SQUASH = 1.85;
	private static final double TUNNEL_SQUASH = 1.35;
	private static final double SHAPE_NOISE_STRENGTH = 0.75;
	private static final double MARGIN = 3.0;
	private static final int FLOOR_DROP = 5;
	private static final int PUDDLE_CELL = 7;
	/** Extra reach past each tunnel tip while seeking a real cave opening. */
	private static final int CONNECTION_REACH = 24;

	private static final byte CELL_ROCK = 0;
	private static final byte CELL_FLOOR = 1;
	private static final byte CELL_AIR = 2;

	private static final int SALT_SHAPE_COARSE = 1;
	private static final int SALT_SHAPE_FINE = 2;
	private static final int SALT_FLOOR = 3;
	private static final int SALT_FLOOR_FINE = 4;
	private static final int SALT_PUDDLE = 5;
	private static final int SALT_FLOOR_PATCH = 6;
	private static final int SALT_FLOOR_MATERIAL = 7;
	private static final int SALT_DEEPSLATE = 8;
	private static final int SALT_WALL_PATCH = 9;
	private static final int SALT_WALL_MATERIAL = 10;
	private static final int SALT_ORE_SEED = 11;
	private static final int SALT_ORE_GROW = 12;
	private static final int SALT_ORE_TYPE = 13;
	private static final int SALT_INFESTED = 14;
	private static final int SALT_SUSPICIOUS = 15;
	private static final int SALT_EGG_MASK = 16;
	private static final int SALT_EGG_PICK = 17;
	private static final int SALT_EGG_COUNT = 18;
	private static final int SALT_EGG_FACE = 19;
	private static final int SALT_LICHEN_MASK = 20;
	private static final int SALT_LICHEN_PICK = 21;
	private static final int SALT_LICHEN_FACE = 22;
	private static final int SALT_ROOTS_MASK = 23;
	private static final int SALT_ROOTS_PICK = 24;
	private static final int SALT_MOSS_MASK = 25;
	private static final int SALT_MOSS_PICK = 26;
	private static final int SALT_MOSS_LENGTH = 27;
	private static final int SALT_MUSHROOM = 28;
	private static final int SALT_MUSHROOM_TYPE = 29;
	private static final int SALT_FLOOR_TUFF = 30;
	private static final int SALT_FLOOR_DITHER = 31;
	private static final int SALT_WALL_DITHER = 32;
	private static final int SALT_MOSS_CARPET = 33;
	private static final float SUSPICIOUS_GRAVEL_CHANCE = 0.20F;
	private static final float MOSS_CARPET_CHANCE = 0.32F;
	private static final double NEST_LICHEN_RADIUS = 8.0;

	private static final int SALT_REMAINS_FLOOR = 34;
	private static final int SALT_REMAINS_RAIL = 35;
	private static final int SALT_REMAINS_POST = 36;
	private static final int SALT_REMAINS_SLAB = 37;
	private static final int SALT_REMAINS_COBWEB = 38;
	private static final int SALT_REMAINS_FRAME = 39;
	private static final int TRAIL_SKIP = 6;
	/** Tunnel blobs are ~1.35 blocks apart, so this puts a mine frame roughly every 7 blocks. */
	private static final int TRAIL_SUPPORT_SPACING = 5;
	private static final float REMAINS_BARREL_CHANCE = 0.40F;

	private static final ResourceKey<LootTable> REMAINS_POT_LOOT = ResourceKey.create(
			Registries.LOOT_TABLE,
			Identifier.fromNamespaceAndPath(SilverfishExpansion.MOD_ID, "chests/nest_remains_pot")
	);
	private static final ResourceKey<LootTable> LOST_MINER_BARREL_LOOT = ResourceKey.create(
			Registries.LOOT_TABLE,
			Identifier.fromNamespaceAndPath(SilverfishExpansion.MOD_ID, "chests/lost_miner_barrel")
	);

	private static final Direction[] EGG_FACES = {
			Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP
	};

	private final BlockPos center;
	private final long seed;
	private final Layout layout;

	public BreedingChamberPiece(BlockPos center, long seed) {
		this(center, seed, createLayout(center, seed));
	}

	private BreedingChamberPiece(BlockPos center, long seed, Layout layout) {
		super(ModStructures.BREEDING_CHAMBER_PIECE, 0, layout.box());
		this.center = center;
		this.seed = seed;
		this.layout = layout;
	}

	public BreedingChamberPiece(CompoundTag tag) {
		super(ModStructures.BREEDING_CHAMBER_PIECE, tag);
		this.center = new BlockPos(tag.getIntOr("CX", 0), tag.getIntOr("CY", 0), tag.getIntOr("CZ", 0));
		this.seed = tag.getLongOr("Seed", 0L);
		this.layout = createLayout(this.center, this.seed);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putLong("Seed", this.seed);
		tag.putInt("CX", this.center.getX());
		tag.putInt("CY", this.center.getY());
		tag.putInt("CZ", this.center.getZ());
	}

	/** Main chamber floor; used as a fallback focus, not a queen seat. */
	public BlockPos getNestAnchor() {
		return new BlockPos(Mth.floor(this.layout.anchorX()), this.layout.baseFloorY() + 1, Mth.floor(this.layout.anchorZ()));
	}

	private static final int COLONY_ADULT_MIN = 6;
	private static final int COLONY_ADULT_MAX = 12;

	/**
	 * Spreads adults through colony nodes. Each structure chunk runs the same seeded
	 * sequence and only inserts entities whose stand is inside the chunk being
	 * decorated — never touching neighbour chunks (that deadlocks WorldGenRegion).
	 */
	private void trySpawnColonyFauna(WorldGenLevel level, BoundingBox chunkBox) {
		RandomSource fauna = RandomSource.create(this.seed ^ 0x51F15EEDL);
		List<ColonyNode> nodes = this.layout.nodes();
		int adults = COLONY_ADULT_MIN + fauna.nextInt(COLONY_ADULT_MAX - COLONY_ADULT_MIN + 1);
		for (int i = 0; i < adults; i++) {
			ColonyNode node = nodes.get(fauna.nextInt(nodes.size()));
			int dx = fauna.nextInt(7) - 3;
			int dz = fauna.nextInt(7) - 3;
			int variantPick = fauna.nextInt(SilverfishVariant.values().length);
			float yaw = fauna.nextFloat() * 360.0F;
			BlockPos probe = BlockPos.containing(node.x() + dx, node.y(), node.z() + dz);
			BlockPos stand = findSpawnStandInChunk(level, chunkBox, probe, 4);
			if (stand != null) {
				spawnColonyAdult(level, stand, SilverfishVariant.values()[variantPick], yaw);
			}
		}
	}

	@Nullable
	private BlockPos findSpawnStandInChunk(WorldGenLevel level, BoundingBox chunkBox, BlockPos near, int radius) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int r = 0; r <= radius; r++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) {
							continue;
						}
						cursor.set(near.getX() + dx, near.getY() + dy, near.getZ() + dz);
						if (!chunkBox.isInside(cursor)) {
							continue;
						}
						if (isValidSpawnStand(level, cursor)) {
							return cursor.immutable();
						}
					}
				}
			}
		}
		return null;
	}

	private static boolean isValidSpawnStand(WorldGenLevel level, BlockPos pos) {
		if (!canAccess(level, pos) || !canAccess(level, pos.above()) || !canAccess(level, pos.below())) {
			return false;
		}
		if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
			return false;
		}
		BlockPos floor = pos.below();
		BlockState floorState = level.getBlockState(floor);
		return floorState.isFaceSturdy(level, floor, Direction.UP);
	}

	/**
	 * WorldGenRegion only exposes nearby chunks. Reading outside throws
	 * {@code Requested chunk unavailable during world generation}.
	 */
	private static boolean canAccess(WorldGenLevel level, BlockPos pos) {
		return canAccess(level, pos.getX(), pos.getY(), pos.getZ());
	}

	private static boolean canAccess(WorldGenLevel level, int x, int y, int z) {
		if (y < level.getMinY() || y > level.getMaxY()) {
			return false;
		}
		return level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
	}

	// Queen spawn disabled: the nest is the colony, not a matriarch mob.
	// private void spawnMatriarch(...) { ... }

	private void spawnColonyAdult(WorldGenLevel level, BlockPos stand, SilverfishVariant variant, float yaw) {
		ExpandedSilverfishEntity adult = ModEntities.typeFor(variant).create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (adult == null) {
			return;
		}
		adult.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, yaw, 0.0F);
		adult.setPersistenceRequired();
		level.addFreshEntity(adult);
	}

	// ------------------------------------------------------------------
	// Layout
	// ------------------------------------------------------------------

	private record Blob(double x, double y, double z, double radius) {
	}

	private record TunnelTip(double x, double y, double z, double heading) {
	}

	private record ColonyNode(double x, double y, double z, double radius) {
	}

	/** Abandoned miner's camp: the satellite room plus the tunnel the miner came in through. */
	private record RemainsSite(double x, double y, double z, double radius, List<Blob> trail) {
		boolean isSupport(int index) {
			return index % TRAIL_SUPPORT_SPACING == TRAIL_SUPPORT_SPACING / 2 && index + 1 < this.trail.size();
		}
	}

	private record Layout(
			List<Blob> lobes,
			List<Blob> tunnels,
			List<TunnelTip> tips,
			List<ColonyNode> nodes,
			List<RemainsSite> remains,
			int barrelTip,
			double anchorX,
			double anchorZ,
			int baseFloorY,
			BoundingBox box
	) {
	}

	private static Layout createLayout(BlockPos center, long seed) {
		RandomSource random = RandomSource.create(seed);
		int cx = center.getX();
		int cy = center.getY();
		int cz = center.getZ();

		List<Blob> lobes = new ArrayList<>();
		double mainRadius = 13.5 + random.nextDouble() * 3.5;
		double mainX = cx + (random.nextDouble() - 0.5) * 5.0;
		double mainZ = cz + (random.nextDouble() - 0.5) * 5.0;
		lobes.add(new Blob(mainX, cy, mainZ, mainRadius));

		// Nearby lobes thicken the main chamber; farther ones become side rooms.
		int lobeCount = 9 + random.nextInt(4);
		double angle = random.nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < lobeCount; i++) {
			angle += Math.PI * 2.0 / lobeCount * (0.55 + random.nextDouble() * 0.9);
			boolean sideRoom = i < 3;
			double distance = sideRoom ? 14.0 + random.nextDouble() * 8.0 : 7.0 + random.nextDouble() * 7.0;
			double radius = sideRoom ? 6.0 + random.nextDouble() * 3.0 : 4.5 + random.nextDouble() * 3.0;
			double y = cy + (random.nextDouble() - 0.45) * 5.0;
			lobes.add(new Blob(cx + Math.cos(angle) * distance, y, cz + Math.sin(angle) * distance, radius));
		}

		int baseFloorY = cy - FLOOR_DROP;

		// Satellite colony nodes (small rooms) — ant-nest style network.
		List<ColonyNode> nodes = new ArrayList<>();
		nodes.add(new ColonyNode(mainX, baseFloorY + 3.0, mainZ, mainRadius * 0.35));

		int satelliteCount = 7 + random.nextInt(4);
		angle = random.nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < satelliteCount; i++) {
			angle += Math.PI * 2.0 / satelliteCount * (0.65 + random.nextDouble() * 0.7);
			double distance = 22.0 + random.nextDouble() * 20.0;
			double radius = 3.6 + random.nextDouble() * 2.6;
			double y = baseFloorY + 1.5 + random.nextDouble() * 5.0;
			double nx = mainX + Math.cos(angle) * distance;
			double nz = mainZ + Math.sin(angle) * distance;
			nodes.add(new ColonyNode(nx, y, nz, radius));
			lobes.add(new Blob(nx, y, nz, radius));
		}

		List<Blob> tunnels = new ArrayList<>();
		List<TunnelTip> tips = new ArrayList<>();

		// Main → each satellite.
		List<List<Blob>> approaches = new ArrayList<>();
		approaches.add(List.of());
		for (int i = 1; i < nodes.size(); i++) {
			ColonyNode from = nodes.get(0);
			ColonyNode to = nodes.get(i);
			int start = tunnels.size();
			carveTunnel(tunnels, tips, random, from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), 1.9, 1.35, false);
			approaches.add(List.copyOf(tunnels.subList(start, tunnels.size())));
		}

		// Cross-links between satellites so paths loop like a nest.
		int crossLinks = 4 + random.nextInt(4);
		for (int i = 0; i < crossLinks; i++) {
			int a = 1 + random.nextInt(nodes.size() - 1);
			int b = 1 + random.nextInt(nodes.size() - 1);
			if (a == b) {
				continue;
			}
			ColonyNode from = nodes.get(a);
			ColonyNode to = nodes.get(b);
			carveTunnel(tunnels, tips, random, from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), 1.55, 1.2, false);
		}

		// Extra short passages so rooms have more than one way in.
		int extraPassages = 3 + random.nextInt(3);
		for (int i = 0; i < extraPassages; i++) {
			ColonyNode from = nodes.get(random.nextInt(nodes.size()));
			ColonyNode to = nodes.get(random.nextInt(nodes.size()));
			if (from == to) {
				continue;
			}
			carveTunnel(tunnels, tips, random, from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), 1.4, 1.15, false);
		}

		// Dead-end / exploratory tunnels that may later punch into vanilla caves.
		int explorers = 6 + random.nextInt(4);
		for (int i = 0; i < explorers; i++) {
			ColonyNode from = nodes.get(random.nextInt(nodes.size()));
			double heading = random.nextDouble() * Math.PI * 2.0;
			double x = from.x();
			double y = from.y();
			double z = from.z();
			int length = 30 + random.nextInt(26);
			for (int step = 0; step < length; step++) {
				heading += (random.nextDouble() - 0.5) * 0.5;
				x += Math.cos(heading) * 1.35;
				z += Math.sin(heading) * 1.35;
				y = Mth.clamp(y + (random.nextDouble() - 0.5) * 1.0, baseFloorY - 5.0, cy + 5.0);
				double radius = Mth.lerp((double) step / length, 1.7, 0.65);
				tunnels.add(new Blob(x, y, z, radius));
			}
			tips.add(new TunnelTip(x, y, z, heading));
		}

		// Drawn after the geometry so existing nests keep their exact shape.
		List<RemainsSite> remains = new ArrayList<>();
		if (nodes.size() > 1) {
			int firstNode = 1 + random.nextInt(nodes.size() - 1);
			int secondNode = random.nextFloat() < 0.5F ? 1 + random.nextInt(nodes.size() - 1) : firstNode;
			for (int nodeIndex : secondNode == firstNode ? new int[]{firstNode} : new int[]{firstNode, secondNode}) {
				ColonyNode node = nodes.get(nodeIndex);
				List<Blob> approach = approaches.get(nodeIndex);
				// The first blobs still sit inside the main chamber; the miner's trail starts past them.
				List<Blob> trail = approach.size() > TRAIL_SKIP ? approach.subList(TRAIL_SKIP, approach.size()) : List.of();
				remains.add(new RemainsSite(node.x(), node.y(), node.z(), node.radius(), List.copyOf(trail)));
			}
		}
		int barrelTip = !tips.isEmpty() && random.nextFloat() < REMAINS_BARREL_CHANCE ? random.nextInt(tips.size()) : -1;

		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (Blob blob : lobes) {
			double vertical = blob.radius() / VERTICAL_SQUASH + MARGIN;
			minX = Math.min(minX, blob.x() - blob.radius() - MARGIN);
			maxX = Math.max(maxX, blob.x() + blob.radius() + MARGIN);
			minZ = Math.min(minZ, blob.z() - blob.radius() - MARGIN);
			maxZ = Math.max(maxZ, blob.z() + blob.radius() + MARGIN);
			minY = Math.min(minY, blob.y() - vertical);
			maxY = Math.max(maxY, blob.y() + vertical);
		}
		for (Blob blob : tunnels) {
			double vertical = blob.radius() / TUNNEL_SQUASH + MARGIN;
			minX = Math.min(minX, blob.x() - blob.radius() - MARGIN);
			maxX = Math.max(maxX, blob.x() + blob.radius() + MARGIN);
			minZ = Math.min(minZ, blob.z() - blob.radius() - MARGIN);
			maxZ = Math.max(maxZ, blob.z() + blob.radius() + MARGIN);
			minY = Math.min(minY, blob.y() - vertical);
			maxY = Math.max(maxY, blob.y() + vertical);
		}
		for (TunnelTip tip : tips) {
			minX = Math.min(minX, tip.x() - CONNECTION_REACH);
			maxX = Math.max(maxX, tip.x() + CONNECTION_REACH);
			minZ = Math.min(minZ, tip.z() - CONNECTION_REACH);
			maxZ = Math.max(maxZ, tip.z() + CONNECTION_REACH);
			minY = Math.min(minY, tip.y() - 6.0);
			maxY = Math.max(maxY, tip.y() + 6.0);
		}
		minY = Math.min(minY, baseFloorY - 6);

		BoundingBox box = new BoundingBox(
				Mth.floor(minX), Mth.floor(minY), Mth.floor(minZ),
				Mth.ceil(maxX), Mth.ceil(maxY), Mth.ceil(maxZ)
		);
		return new Layout(lobes, tunnels, tips, List.copyOf(nodes), List.copyOf(remains), barrelTip, mainX, mainZ, baseFloorY, box);
	}

	/** Winding passage between two colony nodes; optional tip at the end for cave seeking. */
	private static void carveTunnel(
			List<Blob> tunnels,
			List<TunnelTip> tips,
			RandomSource random,
			double startX, double startY, double startZ,
			double endX, double endY, double endZ,
			double startRadius,
			double endRadius,
			boolean leaveTip
	) {
		double dx = endX - startX;
		double dy = endY - startY;
		double dz = endZ - startZ;
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		int length = Math.max(8, Mth.ceil(distance / 1.3));
		double heading = Math.atan2(dz, dx);
		double x = startX;
		double y = startY;
		double z = startZ;

		for (int step = 0; step < length; step++) {
			double t = (double) step / length;
			double targetHeading = Math.atan2(endZ - z, endX - x);
			heading = lerpAngle(heading, targetHeading, 0.25) + (random.nextDouble() - 0.5) * 0.35;
			x += Math.cos(heading) * 1.35;
			z += Math.sin(heading) * 1.35;
			y += Mth.clamp((endY - y) * 0.08 + (random.nextDouble() - 0.5) * 0.7, -1.1, 1.1);
			double radius = Mth.lerp(t, startRadius, endRadius);
			tunnels.add(new Blob(x, y, z, radius));
		}
		if (leaveTip) {
			tips.add(new TunnelTip(x, y, z, heading));
		}
	}

	private static double lerpAngle(double from, double to, double t) {
		double diff = to - from;
		while (diff > Math.PI) {
			diff -= Math.PI * 2.0;
		}
		while (diff < -Math.PI) {
			diff += Math.PI * 2.0;
		}
		return from + diff * t;
	}

	// ------------------------------------------------------------------
	// Placement
	// ------------------------------------------------------------------

	@Override
	public void postProcess(
			WorldGenLevel level,
			StructureManager structureManager,
			ChunkGenerator generator,
			RandomSource random,
			BoundingBox chunkBox,
			ChunkPos chunkPos,
			BlockPos origin
	) {
		int minX = Math.max(this.boundingBox.minX(), chunkBox.minX());
		int maxX = Math.min(this.boundingBox.maxX(), chunkBox.maxX());
		int minZ = Math.max(this.boundingBox.minZ(), chunkBox.minZ());
		int maxZ = Math.min(this.boundingBox.maxZ(), chunkBox.maxZ());
		int minY = Math.max(Math.max(this.boundingBox.minY(), chunkBox.minY()), level.getMinY());
		int maxY = Math.min(Math.min(this.boundingBox.maxY(), chunkBox.maxY()), level.getMaxY());
		if (minX > maxX || minY > maxY || minZ > maxZ) {
			return;
		}

		Grid grid = buildGrid(minX, minY, minZ, maxX, maxY, maxZ);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

		placeTerrain(level, grid, cursor, minX, minY, minZ, maxX, maxY, maxZ);
		connectTunnelsToCaves(level, chunkBox, cursor);
		placeDecoration(level, grid, cursor, probe, minX, minY, minZ, maxX, maxY, maxZ);
		placeRemainsFeatures(level, chunkBox);
		trySpawnColonyFauna(level, chunkBox);
	}

	/**
	 * Probe past each tunnel tip for pre-existing cave air. Only if a real opening is found
	 * do we carve the short connecting passage — otherwise the tip stays a dead-end fissure.
	 */
	private void connectTunnelsToCaves(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos cursor) {
		int worldMinY = level.getMinY();
		int worldMaxY = level.getMaxY();

		for (int tipIndex = 0; tipIndex < this.layout.tips().size(); tipIndex++) {
			TunnelTip tip = this.layout.tips().get(tipIndex);
			int hitStep = findCaveConnectionStep(level, cursor, tip, tipIndex, worldMinY, worldMaxY);
			if (hitStep < 0) {
				continue;
			}
			carveCaveConnection(level, chunkBox, cursor, tip, tipIndex, hitStep, worldMinY, worldMaxY);
		}
	}

	private int findCaveConnectionStep(
			WorldGenLevel level,
			BlockPos.MutableBlockPos cursor,
			TunnelTip tip,
			int tipIndex,
			int worldMinY,
			int worldMaxY
	) {
		double x = tip.x();
		double y = tip.y();
		double z = tip.z();
		double heading = tip.heading();

		for (int step = 0; step < CONNECTION_REACH; step++) {
			float jitter = connectionJitter(x, y, z, tipIndex, step);
			heading += jitter * 0.45;
			x += Math.cos(heading) * 1.25;
			z += Math.sin(heading) * 1.25;
			y = connectionY(x, y, z, tipIndex);

			double radius = Mth.lerp((double) step / CONNECTION_REACH, 1.55, 0.7);
			int radiusCeil = Mth.ceil(radius);
			for (int dy = -radiusCeil; dy <= radiusCeil; dy++) {
				for (int dz = -radiusCeil; dz <= radiusCeil; dz++) {
					for (int dx = -radiusCeil; dx <= radiusCeil; dx++) {
						if (!insideTunnelDisk(dx, dy, dz, radius)) {
							continue;
						}
						int bx = Mth.floor(x) + dx;
						int by = Mth.floor(y) + dy;
						int bz = Mth.floor(z) + dz;
						if (by < worldMinY || by > worldMaxY || isOurCavity(bx, by, bz)) {
							continue;
						}
						if (!canAccess(level, bx, by, bz)) {
							continue;
						}
						cursor.set(bx, by, bz);
						if (level.getBlockState(cursor).isAir()) {
							return step;
						}
					}
				}
			}
		}
		return -1;
	}

	private void carveCaveConnection(
			WorldGenLevel level,
			BoundingBox chunkBox,
			BlockPos.MutableBlockPos cursor,
			TunnelTip tip,
			int tipIndex,
			int hitStep,
			int worldMinY,
			int worldMaxY
	) {
		double x = tip.x();
		double y = tip.y();
		double z = tip.z();
		double heading = tip.heading();

		for (int step = 0; step <= hitStep; step++) {
			float jitter = connectionJitter(x, y, z, tipIndex, step);
			heading += jitter * 0.45;
			x += Math.cos(heading) * 1.25;
			z += Math.sin(heading) * 1.25;
			y = connectionY(x, y, z, tipIndex);

			double radius = Mth.lerp((double) step / CONNECTION_REACH, 1.55, 0.7);
			int radiusCeil = Mth.ceil(radius);
			for (int dy = -radiusCeil; dy <= radiusCeil; dy++) {
				for (int dz = -radiusCeil; dz <= radiusCeil; dz++) {
					for (int dx = -radiusCeil; dx <= radiusCeil; dx++) {
						if (!insideTunnelDisk(dx, dy, dz, radius)) {
							continue;
						}
						int bx = Mth.floor(x) + dx;
						int by = Mth.floor(y) + dy;
						int bz = Mth.floor(z) + dz;
						if (by < worldMinY || by > worldMaxY || !chunkBox.isInside(bx, by, bz) || isOurCavity(bx, by, bz)) {
							continue;
						}
						if (!canAccess(level, bx, by, bz)) {
							continue;
						}
						cursor.set(bx, by, bz);
						BlockState existing = level.getBlockState(cursor);
						if (existing.is(Blocks.BEDROCK) || existing.isAir()) {
							continue;
						}
						if (canReplaceWall(existing)) {
							level.setBlock(cursor, CAVE_AIR, FLAGS);
						}
					}
				}
			}
		}
	}

	private float connectionJitter(double x, double y, double z, int tipIndex, int step) {
		return posRand(Mth.floor(x), Mth.floor(y), Mth.floor(z), SALT_SHAPE_FINE + tipIndex * 17 + step) * 2.0F - 1.0F;
	}

	private double connectionY(double x, double y, double z, int tipIndex) {
		return Mth.clamp(
				y + (posRand(Mth.floor(x), tipIndex, Mth.floor(z), SALT_FLOOR) - 0.5F) * 0.9,
				this.layout.baseFloorY() - 5.0,
				this.center.getY() + 5.0
		);
	}

	private static boolean insideTunnelDisk(int dx, int dy, int dz, double radius) {
		double scaledDy = dy * TUNNEL_SQUASH;
		return dx * dx + scaledDy * scaledDy + dz * dz <= radius * radius;
	}

	private boolean isOurCavity(int x, int y, int z) {
		return chamberField(x, y, z) > 1.0 || inTunnel(x, y, z, this.layout.tunnels());
	}

	private Grid buildGrid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		int originX = minX - 1;
		int originY = minY - 1;
		int originZ = minZ - 1;
		int sizeX = maxX - minX + 3;
		int sizeY = maxY - minY + 3;
		int sizeZ = maxZ - minZ + 3;

		List<Blob> nearbyTunnels = new ArrayList<>();
		for (Blob blob : this.layout.tunnels()) {
			double reach = blob.radius() + 2.0;
			if (blob.x() + reach >= originX && blob.x() - reach <= originX + sizeX
					&& blob.z() + reach >= originZ && blob.z() - reach <= originZ + sizeZ
					&& blob.y() + reach >= originY && blob.y() - reach <= originY + sizeY) {
				nearbyTunnels.add(blob);
			}
		}

		byte[] data = new byte[sizeX * sizeY * sizeZ];
		int index = 0;
		for (int y = originY; y < originY + sizeY; y++) {
			for (int z = originZ; z < originZ + sizeZ; z++) {
				for (int x = originX; x < originX + sizeX; x++) {
					data[index++] = computeCell(x, y, z, nearbyTunnels);
				}
			}
		}
		return new Grid(originX, originY, originZ, sizeX, sizeY, sizeZ, data);
	}

	private byte computeCell(int x, int y, int z, List<Blob> nearbyTunnels) {
		if (chamberField(x, y, z) > 1.0) {
			int floor = floorLevel(x, z);
			if (y <= floor) {
				return CELL_FLOOR;
			}
			return CELL_AIR;
		}
		return inTunnel(x, y, z, nearbyTunnels) ? CELL_AIR : CELL_ROCK;
	}

	private double chamberField(int x, int y, int z) {
		double sum = 0.0;
		for (Blob blob : this.layout.lobes()) {
			double dx = x - blob.x();
			double dy = (y - blob.y()) * VERTICAL_SQUASH;
			double dz = z - blob.z();
			double falloff = blob.radius() * blob.radius() / (dx * dx + dy * dy + dz * dz + 1.0);
			sum += falloff * falloff;
		}
		return sum + shapeNoise(x, y, z);
	}

	private boolean inTunnel(int x, int y, int z, List<Blob> nearbyTunnels) {
		if (nearbyTunnels.isEmpty()) {
			return false;
		}
		double wobble = shapeNoise(x, y, z) * 0.6;
		for (Blob blob : nearbyTunnels) {
			double dx = x - blob.x();
			double dy = (y - blob.y()) * TUNNEL_SQUASH;
			double dz = z - blob.z();
			double radius = blob.radius() + wobble;
			if (radius > 0.0 && dx * dx + dy * dy + dz * dz < radius * radius) {
				return true;
			}
		}
		return false;
	}

	private int floorLevel(int x, int z) {
		float coarse = valueNoise2D(x, z, SALT_FLOOR, 13) * 2.0F - 1.0F;
		float fine = valueNoise2D(x, z, SALT_FLOOR_FINE, 5) * 2.0F - 1.0F;
		int offset = Mth.clamp(Mth.floor((coarse * 0.78F + fine * 0.22F) * 4.6F), -4, 3);
		int floor = this.layout.baseFloorY() + offset;
		return isHollow(x, z) ? floor - 1 : floor;
	}

	private boolean isHollow(int x, int z) {
		double dx = x - this.layout.anchorX();
		double dz = z - this.layout.anchorZ();
		if (dx * dx + dz * dz < 12.0) {
			return false;
		}
		return valueNoise2D(x, z, SALT_PUDDLE, PUDDLE_CELL) > 0.72F;
	}

	// ------------------------------------------------------------------
	// Terrain pass
	// ------------------------------------------------------------------

	private void placeTerrain(
			WorldGenLevel level,
			Grid grid,
			BlockPos.MutableBlockPos cursor,
			int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ
	) {
		for (int y = minY; y <= maxY; y++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					byte cell = grid.get(x, y, z);
					// The rock shell is only touched where it faces the cavity. Sealing it also
					// stops neighbouring lava or water from draining into the chamber later.
					if (cell == CELL_ROCK && !touchesCavity(grid, x, y, z)) {
						continue;
					}

					cursor.set(x, y, z);
					BlockState existing = level.getBlockState(cursor);
					if (existing.is(Blocks.BEDROCK)) {
						continue;
					}

					switch (cell) {
						case CELL_AIR -> level.setBlock(cursor, CAVE_AIR, FLAGS);
						case CELL_FLOOR -> placeSolid(level, grid, cursor, x, y, z, true);
						default -> {
							if (canReplaceWall(existing)) {
								placeSolid(level, grid, cursor, x, y, z, false);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Anything solid with the cavity right above it reads as ground, whether it is filled floor or
	 * the untouched rock of a lobe that sits higher than the fill level.
	 */
	private void placeSolid(WorldGenLevel level, Grid grid, BlockPos.MutableBlockPos cursor, int x, int y, int z, boolean filled) {
		byte above = grid.get(x, y + 1, z);
		BlockState state;
		if (above == CELL_AIR) {
			state = floorSurface(x, y, z, isSolid(grid.get(x, y - 1, z)));
		} else {
			state = filled ? baseRock(x, y, z) : wallMaterial(x, y, z);
		}

		level.setBlock(cursor, state, FLAGS);
		if (state.is(Blocks.SUSPICIOUS_GRAVEL) && level.getBlockEntity(cursor) instanceof BrushableBlockEntity brushable) {
			brushable.setLootTable(BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON, this.seed + cursor.asLong());
		}
		trackMatureInfestation(level, cursor, state);
	}

	/** Chamber infestations are already "aged in" — breaking them releases an adult. */
	private void trackMatureInfestation(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof InfestedBlock)) {
			return;
		}
		ServerLevel server = level.getLevel();
		ColonyInfestationData.get(server).register(
				pos.immutable(),
				server.getGameTime() - ColonyInfestationData.TICKS_TO_ADULT
		);
	}

	private static boolean touchesCavity(Grid grid, int x, int y, int z) {
		for (Direction direction : Direction.values()) {
			byte neighbour = grid.get(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
			if (neighbour == CELL_AIR) {
				return true;
			}
		}
		return false;
	}

	private static boolean canReplaceWall(BlockState state) {
		if (state.isAir() || state.is(Blocks.BEDROCK)) {
			return false;
		}
		if (!state.getFluidState().isEmpty()) {
			return true;
		}
		return state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(BlockTags.DIRT)
				|| state.is(Blocks.GRAVEL)
				|| state.is(Blocks.SAND)
				|| state.is(Blocks.CLAY)
				|| state.is(Blocks.CALCITE)
				|| state.is(Blocks.SMOOTH_BASALT);
	}

	private BlockState baseRock(int x, int y, int z) {
		return isDeepslate(x, y, z) ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	private boolean isDeepslate(int x, int y, int z) {
		if (y >= 8) {
			return false;
		}
		if (y < 0) {
			return true;
		}
		return posRand(x, y, z, SALT_DEEPSLATE) < (8 - y) / 8.0F;
	}

	/**
	 * Stone-first floor with sparse pale-moss/gravel/tuff patches. Patch edges are dithered
	 * (soft threshold + fine noise) so materials bleed into each other instead of
	 * forming hard blobs like a painted biome border.
	 */
	private BlockState floorSurface(int x, int y, int z, boolean supported) {
		boolean deep = isDeepslate(x, y, z);
		float damp = valueNoise2D(x, z, SALT_PUDDLE, PUDDLE_CELL);
		// Former "puddle" dips: gravel / pale moss hollows instead of water.
		float gravelBias = damp > 0.70F ? 0.06F : 0.0F;

		float gravelField = valueNoise2D(x, z, SALT_FLOOR_PATCH, 14) + gravelBias;
		float dirtField = valueNoise2D(x, z, SALT_FLOOR_MATERIAL, 12);
		float tuffField = valueNoise2D(x, z, SALT_FLOOR_TUFF, 10);

		// Along the miner's trail and camp: dug-up gravel and loose cobble.
		if (supported && inRemainsArea(x, y + 1, z)) {
			float roll = posRand(x, y, z, SALT_REMAINS_FLOOR);
			if (roll < 0.07F) {
				return Blocks.SUSPICIOUS_GRAVEL.defaultBlockState();
			}
			if (roll < 0.17F) {
				return deep ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
			}
			if (roll < 0.25F) {
				return Blocks.GRAVEL.defaultBlockState();
			}
		}

		// Hollow floors are almost always loose sediment — looks damp without fluids.
		if (isHollow(x, z) && supported) {
			if (posRand(x, y, z, SALT_FLOOR_MATERIAL) < 0.28F) {
				return Blocks.PALE_MOSS_BLOCK.defaultBlockState();
			}
			return gravel(x, y, z);
		}

		if (supported && softPatch2D(gravelField, 0.855F, 0.09F, x, z, SALT_FLOOR_DITHER)) {
			if (posRand(x, y, z, SALT_FLOOR_MATERIAL) < 0.14F) {
				return Blocks.PALE_MOSS_BLOCK.defaultBlockState();
			}
			return gravel(x, y, z);
		}
		if (softPatch2D(dirtField, 0.875F, 0.08F, x, z, SALT_FLOOR_DITHER + 3)) {
			return Blocks.PALE_MOSS_BLOCK.defaultBlockState();
		}
		if (softPatch2D(tuffField, 0.83F, 0.10F, x, z, SALT_FLOOR_DITHER + 7)) {
			return Blocks.TUFF.defaultBlockState();
		}
		if (posRand(x, y, z, SALT_FLOOR_MATERIAL + 90) < 0.035F) {
			return deep ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
		}
		return baseRock(x, y, z);
	}

	/**
	 * Soft membership test: solid core above {@code threshold + edge}, empty below
	 * {@code threshold - edge}, and a speckled probability ramp in between.
	 */
	private boolean softPatch2D(float field, float threshold, float edge, int x, int z, int ditherSalt) {
		if (field >= threshold + edge) {
			return true;
		}
		if (field <= threshold - edge) {
			return false;
		}
		float t = (field - (threshold - edge)) / (edge * 2.0F);
		float fine = valueNoise2D(x, z, ditherSalt, 3);
		float chance = Mth.clamp(t * 0.65F + fine * 0.35F, 0.0F, 1.0F);
		return posRand(x, 0, z, ditherSalt + 101) < chance;
	}

	private boolean softPatch3D(float field, float threshold, float edge, int x, int y, int z, int ditherSalt) {
		if (field >= threshold + edge) {
			return true;
		}
		if (field <= threshold - edge) {
			return false;
		}
		float t = (field - (threshold - edge)) / (edge * 2.0F);
		float fine = valueNoise3D(x, y, z, ditherSalt, 3);
		float chance = Mth.clamp(t * 0.65F + fine * 0.35F, 0.0F, 1.0F);
		return posRand(x, y, z, ditherSalt + 101) < chance;
	}

	/** Gravel bed: one in five blocks is suspicious so the patches are worth brushing. */
	private BlockState gravel(int x, int y, int z) {
		return posRand(x, y, z, SALT_SUSPICIOUS) < SUSPICIOUS_GRAVEL_CHANCE
				? Blocks.SUSPICIOUS_GRAVEL.defaultBlockState()
				: Blocks.GRAVEL.defaultBlockState();
	}

	private BlockState wallMaterial(int x, int y, int z) {
		boolean deep = isDeepslate(x, y, z);

		if (isOre(x, y, z)) {
			return oreFor(deep, posRand(x >> 2, y >> 2, z >> 2, SALT_ORE_TYPE));
		}
		if (posRand(x, y, z, SALT_INFESTED) < 0.008F) {
			return deep ? Blocks.INFESTED_DEEPSLATE.defaultBlockState() : Blocks.INFESTED_STONE.defaultBlockState();
		}

		float patch = valueNoise3D(x, y, z, SALT_WALL_PATCH, 9);
		if (softPatch3D(patch, 0.78F, 0.10F, x, y, z, SALT_WALL_DITHER)) {
			return Blocks.TUFF.defaultBlockState();
		}
		if (!deep && softPatch3D(1.0F - patch, 0.78F, 0.09F, x, y, z, SALT_WALL_DITHER + 5)) {
			return Blocks.ANDESITE.defaultBlockState();
		}
		if (posRand(x, y, z, SALT_WALL_MATERIAL) < 0.04F) {
			return deep ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
		}
		return baseRock(x, y, z);
	}

	/** Small veins: a rare seed block plus a chance to spread one step off it. */
	private boolean isOre(int x, int y, int z) {
		if (posRand(x, y, z, SALT_ORE_SEED) < 0.0035F) {
			return true;
		}
		if (posRand(x, y, z, SALT_ORE_GROW) > 0.55F) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			if (posRand(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ(), SALT_ORE_SEED) < 0.0035F) {
				return true;
			}
		}
		return false;
	}

	private static BlockState oreFor(boolean deep, float roll) {
		if (roll < 0.42F) {
			return deep ? Blocks.DEEPSLATE_COAL_ORE.defaultBlockState() : Blocks.COAL_ORE.defaultBlockState();
		}
		if (roll < 0.72F) {
			return deep ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState() : Blocks.IRON_ORE.defaultBlockState();
		}
		if (roll < 0.94F) {
			return deep ? Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState() : Blocks.COPPER_ORE.defaultBlockState();
		}
		return deep ? Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState() : Blocks.REDSTONE_ORE.defaultBlockState();
	}

	// ------------------------------------------------------------------
	// Decoration pass
	// ------------------------------------------------------------------

	private void placeDecoration(
			WorldGenLevel level,
			Grid grid,
			BlockPos.MutableBlockPos cursor,
			BlockPos.MutableBlockPos probe,
			int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ
	) {
		for (int y = minY; y <= maxY; y++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					if (grid.get(x, y, z) != CELL_AIR) {
						continue;
					}
					cursor.set(x, y, z);
					if (!level.getBlockState(cursor).isAir()) {
						continue;
					}

					if (tryPlaceRemains(level, grid, cursor, probe, x, y, z, maxY)) {
						continue;
					}
					if (tryPlaceEggs(level, grid, cursor, probe, x, y, z)) {
						continue;
					}
					if (tryPlaceHangingRoots(level, grid, cursor, probe, x, y, z, minX, minY, minZ, maxX, maxY, maxZ)) {
						continue;
					}
					if (tryPlaceHangingMoss(level, grid, probe, x, y, z, minX, minY, minZ, maxX, maxY, maxZ)) {
						continue;
					}
					if (tryPlacePaleMossCarpet(level, grid, cursor, probe, x, y, z)) {
						continue;
					}
					if (tryPlaceGlowLichen(level, grid, cursor, probe, x, y, z)) {
						continue;
					}
					tryPlaceMushroom(level, grid, cursor, probe, x, y, z);
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Miner's remains
	// ------------------------------------------------------------------

	private boolean inRemainsArea(int x, int y, int z) {
		for (RemainsSite site : this.layout.remains()) {
			if (inRemainsRoom(site, x, y, z) || nearestTrailIndex(site, x, y, z) >= 0) {
				return true;
			}
		}
		return false;
	}

	private static boolean inRemainsRoom(RemainsSite site, int x, int y, int z) {
		double dx = x + 0.5 - site.x();
		double dz = z + 0.5 - site.z();
		double reach = site.radius() + 3.0;
		return dx * dx + dz * dz < reach * reach && Math.abs(y + 0.5 - site.y()) < 7.0;
	}

	/** Index of the closest trail blob whose tunnel contains this cell, or -1. */
	private static int nearestTrailIndex(RemainsSite site, int x, int y, int z) {
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		List<Blob> trail = site.trail();
		for (int i = 0; i < trail.size(); i++) {
			Blob blob = trail.get(i);
			if (Math.abs(y + 0.5 - blob.y()) > 3.5) {
				continue;
			}
			double dx = x + 0.5 - blob.x();
			double dz = z + 0.5 - blob.z();
			double dist = dx * dx + dz * dz;
			double reach = blob.radius() + 1.0;
			if (dist < reach * reach && dist < bestDist) {
				best = i;
				bestDist = dist;
			}
		}
		return best;
	}

	/**
	 * The miner's trail runs down the tunnel into the room: mine frames at intervals, a rail line
	 * with broken runs, fallen planks and cobwebs spread over the whole way, not one corner.
	 */
	private boolean tryPlaceRemains(
			WorldGenLevel level,
			Grid grid,
			BlockPos.MutableBlockPos cursor,
			BlockPos.MutableBlockPos probe,
			int x, int y, int z,
			int maxY
	) {
		List<RemainsSite> sites = this.layout.remains();
		for (int siteIndex = 0; siteIndex < sites.size(); siteIndex++) {
			RemainsSite site = sites.get(siteIndex);
			int trailIndex = nearestTrailIndex(site, x, y, z);
			if (trailIndex < 0 && !inRemainsRoom(site, x, y, z)) {
				continue;
			}

			if (trailIndex >= 0 && tryPlaceMineFrame(level, grid, cursor, probe, site, siteIndex, x, y, z, maxY)) {
				return true;
			}
			boolean onFloor = isSupported(level, grid, probe, x, y, z, Direction.DOWN);
			if (onFloor && trailIndex >= 0) {
				RailShape rail = railAt(site, siteIndex, trailIndex, x, y, z);
				if (rail != null) {
					level.setBlock(cursor, Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, rail), FLAGS);
					return true;
				}
			}
			if (onFloor && posRand(x, y, z, SALT_REMAINS_SLAB) < 0.025F) {
				level.setBlock(cursor, Blocks.DARK_OAK_SLAB.defaultBlockState(), FLAGS);
				return true;
			}
			if (posRand(x, y, z, SALT_REMAINS_COBWEB) < 0.07F && touchesWallOrCeiling(level, grid, probe, x, y, z)) {
				level.setBlock(cursor, Blocks.COBWEB.defaultBlockState(), FLAGS);
				return true;
			}
			return false;
		}
		return false;
	}

	/**
	 * Mine frame across the tunnel: a post against each wall and a plank lintel along the ceiling.
	 * Some frames are snapped (short posts, no lintel) and some collapsed into loose slabs.
	 */
	private boolean tryPlaceMineFrame(
			WorldGenLevel level,
			Grid grid,
			BlockPos.MutableBlockPos cursor,
			BlockPos.MutableBlockPos probe,
			RemainsSite site,
			int siteIndex,
			int x, int y, int z,
			int maxY
	) {
		List<Blob> trail = site.trail();
		for (int k = 0; k < trail.size(); k++) {
			if (!site.isSupport(k)) {
				continue;
			}
			Blob blob = trail.get(k);
			Blob next = trail.get(k + 1);
			boolean alongX = Math.abs(next.x() - blob.x()) >= Math.abs(next.z() - blob.z());
			int planeX = Mth.floor(blob.x());
			int planeZ = Mth.floor(blob.z());
			if (alongX ? x != planeX : z != planeZ) {
				continue;
			}
			int across = alongX ? z - planeZ : x - planeX;
			if (Math.abs(across) > 3 || Math.abs(y + 0.5 - blob.y()) > 4.0) {
				continue;
			}

			boolean onFloor = isSupported(level, grid, probe, x, y, z, Direction.DOWN);
			float condition = posRand(k, siteIndex, 0, SALT_REMAINS_FRAME);
			if (condition < 0.18F) {
				if (onFloor && posRand(x, y, z, SALT_REMAINS_FRAME) < 0.5F) {
					level.setBlock(cursor, Blocks.DARK_OAK_SLAB.defaultBlockState(), FLAGS);
					return true;
				}
				return false;
			}
			boolean intact = condition >= 0.45F;

			int sideX = alongX ? 0 : 1;
			int sideZ = alongX ? 1 : 0;
			boolean againstWall = isSolid(grid.get(x + sideX, y, z + sideZ)) || isSolid(grid.get(x - sideX, y, z - sideZ));
			if (onFloor && againstWall) {
				placeSupportPost(level, grid, cursor, x, y, z, maxY, intact);
				return true;
			}
			if (intact && isSolid(grid.get(x, y + 1, z))) {
				level.setBlock(cursor, Blocks.DARK_OAK_PLANKS.defaultBlockState(), FLAGS);
				return true;
			}
			return false;
		}
		return false;
	}

	/** Rail along the trail centreline, missing in whole runs as well as single gaps. */
	@Nullable
	private RailShape railAt(RemainsSite site, int siteIndex, int trailIndex, int x, int y, int z) {
		List<Blob> trail = site.trail();
		if (trail.size() < 2) {
			return null;
		}
		int from = Math.min(trailIndex, trail.size() - 2);
		Blob a = trail.get(from);
		Blob b = trail.get(from + 1);
		double segX = b.x() - a.x();
		double segZ = b.z() - a.z();
		double lengthSq = segX * segX + segZ * segZ;
		double px = x + 0.5 - a.x();
		double pz = z + 0.5 - a.z();
		double t = lengthSq > 0.0 ? Mth.clamp((px * segX + pz * segZ) / lengthSq, 0.0, 1.0) : 0.0;
		double offX = px - segX * t;
		double offZ = pz - segZ * t;
		if (offX * offX + offZ * offZ > 0.36) {
			return null;
		}
		if (posRand(trailIndex / 3, siteIndex, 1, SALT_REMAINS_RAIL) < 0.35F || posRand(x, y, z, SALT_REMAINS_RAIL) < 0.12F) {
			return null;
		}
		return Math.abs(segX) >= Math.abs(segZ) ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
	}

	/** Fence post wedged under a plank against the ceiling; snapped ones stop short. */
	private void placeSupportPost(WorldGenLevel level, Grid grid, BlockPos.MutableBlockPos cursor, int x, int y, int z, int maxY, boolean intact) {
		int ceiling = -1;
		for (int k = 1; k <= 5; k++) {
			if (grid.get(x, y + k, z) != CELL_AIR) {
				ceiling = k;
				break;
			}
		}
		boolean snapped = !intact || ceiling < 2;
		int height = snapped ? 1 + (posRand(x, y + 2, z, SALT_REMAINS_POST) < 0.5F ? 1 : 0) : ceiling;

		for (int k = 0; k < height; k++) {
			int by = y + k;
			if (by > maxY) {
				return;
			}
			cursor.set(x, by, z);
			if (!level.getBlockState(cursor).isAir()) {
				return;
			}
			boolean beam = !snapped && k == height - 1;
			level.setBlock(cursor, beam ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.DARK_OAK_FENCE.defaultBlockState(), FLAGS);
		}
	}

	private boolean touchesWallOrCeiling(WorldGenLevel level, Grid grid, BlockPos.MutableBlockPos probe, int x, int y, int z) {
		if (isSupported(level, grid, probe, x, y, z, Direction.UP)) {
			return true;
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (isSupported(level, grid, probe, x, y, z, direction)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Pot, candle and skull spread evenly around each camp room, a second pot beside one of the
	 * trail's mine frames, plus the rare barrel at the end of an explorer tunnel. Each item has a
	 * single target column, so only the chunk owning it ever places it.
	 */
	private void placeRemainsFeatures(WorldGenLevel level, BoundingBox chunkBox) {
		List<RemainsSite> sites = this.layout.remains();
		for (int i = 0; i < sites.size(); i++) {
			RemainsSite site = sites.get(i);
			RandomSource remains = RandomSource.create(this.seed ^ 0x4D1E5CA7L * (i + 1));

			double baseAngle = remains.nextDouble() * Math.PI * 2.0;
			Direction potFacing = Direction.Plane.HORIZONTAL.getRandomDirection(remains);
			int candles = 1 + remains.nextInt(3);
			boolean skull = remains.nextFloat() < 0.35F;
			int skullRotation = remains.nextInt(16);
			boolean trailPot = remains.nextFloat() < 0.6F;
			Direction trailPotFacing = Direction.Plane.HORIZONTAL.getRandomDirection(remains);

			BlockPos potStand = findOwnedStand(level, chunkBox, roomSpot(site, baseAngle, 0.6), 6);
			if (potStand != null) {
				placeRemainsPot(level, potStand, potFacing);
			}

			BlockPos candleStand = findOwnedStand(level, chunkBox, roomSpot(site, baseAngle + Math.PI * 2.0 / 3.0, 0.5), 6);
			if (candleStand != null) {
				level.setBlock(candleStand, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, candles), FLAGS);
			}

			if (skull) {
				BlockPos skullStand = findOwnedStand(level, chunkBox, roomSpot(site, baseAngle + Math.PI * 4.0 / 3.0, 0.65), 6);
				if (skullStand != null) {
					level.setBlock(skullStand, Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, skullRotation), FLAGS);
				}
			}

			if (trailPot) {
				BlockPos spot = trailSpot(site, remains);
				if (spot != null) {
					BlockPos stand = findOwnedStand(level, chunkBox, spot, 4);
					if (stand != null) {
						placeRemainsPot(level, stand, trailPotFacing);
					}
				}
			}
		}

		int tipIndex = this.layout.barrelTip();
		if (tipIndex >= 0) {
			TunnelTip tip = this.layout.tips().get(tipIndex);
			BlockPos stand = findOwnedStand(level, chunkBox, BlockPos.containing(tip.x(), tip.y(), tip.z()), 3);
			if (stand != null) {
				BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
				placeLootContainer(level, stand, barrel, LOST_MINER_BARREL_LOOT);
			}
		}
	}

	private static BlockPos roomSpot(RemainsSite site, double angle, double reach) {
		double distance = site.radius() * reach;
		return BlockPos.containing(site.x() + Math.cos(angle) * distance, site.y(), site.z() + Math.sin(angle) * distance);
	}

	/** One block past a mine frame and off the rail line, as if left leaning against the post. */
	@Nullable
	private static BlockPos trailSpot(RemainsSite site, RandomSource random) {
		List<Blob> trail = site.trail();
		List<Integer> supports = new ArrayList<>();
		for (int k = 0; k < trail.size(); k++) {
			if (site.isSupport(k)) {
				supports.add(k);
			}
		}
		int pick = random.nextInt(Math.max(1, supports.size()));
		int side = random.nextBoolean() ? 1 : -1;
		if (supports.isEmpty()) {
			return null;
		}
		int k = supports.get(pick);
		Blob blob = trail.get(k);
		Blob next = trail.get(k + 1);
		boolean alongX = Math.abs(next.x() - blob.x()) >= Math.abs(next.z() - blob.z());
		int stepX = alongX ? (next.x() > blob.x() ? 1 : -1) : side;
		int stepZ = alongX ? side : (next.z() > blob.z() ? 1 : -1);
		return new BlockPos(Mth.floor(blob.x()) + stepX, Mth.floor(blob.y()), Mth.floor(blob.z()) + stepZ);
	}

	private void placeRemainsPot(WorldGenLevel level, BlockPos stand, Direction facing) {
		BlockState pot = Blocks.DECORATED_POT.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		placeLootContainer(level, stand, pot, REMAINS_POT_LOOT);
	}

	/** Searches only the spot's own column, nearest height first. */
	@Nullable
	private static BlockPos findOwnedStand(WorldGenLevel level, BoundingBox chunkBox, BlockPos spot, int reachY) {
		if (spot.getX() < chunkBox.minX() || spot.getX() > chunkBox.maxX()
				|| spot.getZ() < chunkBox.minZ() || spot.getZ() > chunkBox.maxZ()) {
			return null;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int i = 0; i <= reachY * 2; i++) {
			int dy = (i & 1) == 0 ? -(i >> 1) : (i + 1) >> 1;
			cursor.set(spot.getX(), spot.getY() + dy, spot.getZ());
			if (!canAccess(level, cursor) || !canAccess(level, cursor.below())) {
				continue;
			}
			if (!level.getBlockState(cursor).isAir()) {
				continue;
			}
			BlockPos floor = cursor.below();
			if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
				return cursor.immutable();
			}
		}
		return null;
	}

	private void placeLootContainer(WorldGenLevel level, BlockPos pos, BlockState state, ResourceKey<LootTable> lootTable) {
		level.setBlock(pos, state, FLAGS);
		if (level.getBlockEntity(pos) instanceof RandomizableContainer container) {
			container.setLootTable(lootTable, this.seed + pos.asLong());
		}
	}

	private double nearestNodeDistance(int x, int z) {
		double best = Double.MAX_VALUE;
		for (ColonyNode node : this.layout.nodes()) {
			double dx = x - node.x();
			double dz = z - node.z();
			best = Math.min(best, dx * dx + dz * dz);
		}
		return Math.sqrt(best);
	}

	private boolean tryPlaceEggs(
			WorldGenLevel level,
			Grid grid,
			BlockPos.MutableBlockPos cursor,
			BlockPos.MutableBlockPos probe,
			int x, int y, int z
	) {
		double distance = nearestNodeDistance(x, z);
		double closeness = Mth.clamp(1.0 - distance / 10.0, 0.0, 1.0);
		float pick = closeness > 0.0 ? 0.055F : 0.018F;
		float threshold = 0.74F - (float) (closeness * 0.20);
		if (valueNoise3D(x, y, z, SALT_EGG_MASK, 7) <= threshold || posRand(x, y, z, SALT_EGG_PICK) >= pick) {
			return false;
		}

		int start = (int) (posRand(x, y, z, SALT_EGG_FACE) * EGG_FACES.length);
		for (int i = 0; i < EGG_FACES.length; i++) {
			Direction support = EGG_FACES[(start + i) % EGG_FACES.length];
			if (!isSupported(level, grid, probe, x, y, z, support)) {
				continue;
			}
			int count = Mth.clamp(1 + (int) (posRand(x, y, z, SALT_EGG_COUNT) * 4.0F), 1, SilverfishEggsBlock.MAX_EGGS);
			BlockState state = ModBlocks.SILVERFISH_EGGS.defaultBlockState()
					.setValue(SilverfishEggsBlock.FACING, support.getOpposite())
					.setValue(SilverfishEggsBlock.EGGS, count);
			level.setBlock(cursor, state, FLAGS);
			return true;
		}
		return false;
	}

	private boolean tryPlaceHangingRoots(
			WorldGenLevel level,
			Grid grid,
			BlockPos.MutableBlockPos cursor,
			BlockPos.MutableBlockPos probe,
			int x, int y, int z,
			int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ
	) {
		if (!isSolid(grid.get(x, y + 1, z))) {
			return false;
		}
		if (valueNoise3D(x, y, z, SALT_ROOTS_MASK, 7) <= 0.55F || posRand(x, y, z, SALT_ROOTS_PICK) >= 0.07F) {
			return false;
		}
		if (y + 1 < minY || y + 1 > maxY || x < minX || x > maxX || z < minZ || z > maxZ) {
			return false;
		}

		probe.set(x, y + 1, z);
		BlockState above = level.getBlockState(probe);
		if (!canReplaceWall(above) || !above.getFluidState().isEmpty()) {
			return false;
		}

		level.setBlock(probe, Blocks.PALE_MOSS_BLOCK.defaultBlockState(), FLAGS);
		level.setBlock(cursor, Blocks.HANGING_ROOTS.defaultBlockState(), FLAGS);
		return true;
	}

	private boolean tryPlaceHangingMoss(
			WorldGenLevel level,
			Grid grid,
			BlockPos.MutableBlockPos probe,
			int x, int y, int z,
			int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ
	) {
		if (!isSolid(grid.get(x, y + 1, z))) {
			return false;
		}
		// Strands gather over the damp ground, which is the only humidity cue left in the chamber.
		boolean damp = valueNoise2D(x, z, SALT_PUDDLE, PUDDLE_CELL) > 0.66F;
		float mask = valueNoise2D(x, z, SALT_MOSS_MASK, 10);
		if (!damp && mask <= 0.74F) {
			return false;
		}
		if (posRand(x, y, z, SALT_MOSS_PICK) >= (damp ? 0.12F : 0.05F)) {
			return false;
		}

		probe.set(x, y + 1, z);
		if (!level.getBlockState(probe).isFaceSturdy(level, probe, Direction.DOWN)) {
			return false;
		}

		int length = 1 + (int) (posRand(x, y, z, SALT_MOSS_LENGTH) * 3.0F);
		boolean placed = false;
		for (int i = 0; i < length; i++) {
			int yy = y - i;
			if (yy < minY || yy > maxY || x < minX || x > maxX || z < minZ || z > maxZ) {
				break;
			}
			if (grid.get(x, yy, z) != CELL_AIR) {
				break;
			}
			probe.set(x, yy, z);
			if (!level.getBlockState(probe).isAir()) {
				break;
			}
			boolean tip = i == length - 1 || grid.get(x, yy - 1, z) != CELL_AIR;
			level.setBlock(probe, Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(BlockStateProperties.TIP, tip), FLAGS);
			placed = true;
			if (tip) {
				break;
			}
		}
		return placed;
	}

	private boolean tryPlacePaleMossCarpet(
			WorldGenLevel level,
			Grid grid,
			BlockPos.MutableBlockPos cursor,
			BlockPos.MutableBlockPos probe,
			int x, int y, int z
	) {
		if (!isSolid(grid.get(x, y - 1, z))) {
			return false;
		}
		if (posRand(x, y, z, SALT_MOSS_CARPET) >= MOSS_CARPET_CHANCE) {
			return false;
		}
		probe.set(x, y - 1, z);
		if (!level.getBlockState(probe).is(Blocks.PALE_MOSS_BLOCK)) {
			return false;
		}
		BlockState carpet = Blocks.PALE_MOSS_CARPET.defaultBlockState();
		if (!carpet.canSurvive(level, cursor)) {
			return false;
		}
		level.setBlock(cursor, carpet, FLAGS);
		return true;
	}

	private boolean tryPlaceGlowLichen(
			WorldGenLevel level,
			Grid grid,
			BlockPos.MutableBlockPos cursor,
			BlockPos.MutableBlockPos probe,
			int x, int y, int z
	) {
		boolean nestCenter = nearestNodeDistance(x, z) < NEST_LICHEN_RADIUS;
		float maskLimit = nestCenter ? 0.62F : 0.76F;
		float pickLimit = nestCenter ? 0.22F : 0.12F;
		if (valueNoise3D(x, y, z, SALT_LICHEN_MASK, 5) <= maskLimit || posRand(x, y, z, SALT_LICHEN_PICK) >= pickLimit) {
			return false;
		}

		BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState();
		int start = (int) (posRand(x, y, z, SALT_LICHEN_FACE) * 6.0F);
		int maxFaces = nestCenter ? 3 : 2;
		int faces = 0;
		for (int i = 0; i < 6 && faces < maxFaces; i++) {
			Direction direction = Direction.values()[(start + i) % 6];
			if (isSupported(level, grid, probe, x, y, z, direction)) {
				lichen = lichen.setValue(MultifaceBlock.getFaceProperty(direction), true);
				faces++;
			}
		}
		if (faces == 0) {
			return false;
		}

		level.setBlock(cursor, lichen, FLAGS);
		return true;
	}

	private void tryPlaceMushroom(
			WorldGenLevel level,
			Grid grid,
			BlockPos.MutableBlockPos cursor,
			BlockPos.MutableBlockPos probe,
			int x, int y, int z
	) {
		if (!isSolid(grid.get(x, y - 1, z))) {
			return;
		}
		probe.set(x, y - 1, z);
		BlockState below = level.getBlockState(probe);
		boolean softFloor = below.is(Blocks.PALE_MOSS_BLOCK)
				|| below.is(Blocks.GRAVEL);
		boolean stoneFloor = below.is(Blocks.STONE)
				|| below.is(Blocks.DEEPSLATE)
				|| below.is(Blocks.TUFF)
				|| below.is(Blocks.COBBLESTONE)
				|| below.is(Blocks.COBBLED_DEEPSLATE);
		float chance = softFloor ? 0.10F : stoneFloor ? 0.018F : 0.0F;
		if (chance <= 0.0F || posRand(x, y, z, SALT_MUSHROOM) >= chance) {
			return;
		}
		BlockState mushroom = posRand(x, y, z, SALT_MUSHROOM_TYPE) < 0.6F
				? Blocks.BROWN_MUSHROOM.defaultBlockState()
				: Blocks.RED_MUSHROOM.defaultBlockState();
		level.setBlock(cursor, mushroom, FLAGS);
	}

	private boolean isSupported(
			WorldGenLevel level,
			Grid grid,
			BlockPos.MutableBlockPos probe,
			int x, int y, int z,
			Direction direction
	) {
		int nx = x + direction.getStepX();
		int ny = y + direction.getStepY();
		int nz = z + direction.getStepZ();
		if (!isSolid(grid.get(nx, ny, nz))) {
			return false;
		}
		probe.set(nx, ny, nz);
		return level.getBlockState(probe).isFaceSturdy(level, probe, direction.getOpposite());
	}

	private static boolean isSolid(byte cell) {
		return cell == CELL_ROCK || cell == CELL_FLOOR;
	}

	// ------------------------------------------------------------------
	// Deterministic noise
	// ------------------------------------------------------------------

	private double shapeNoise(int x, int y, int z) {
		double coarse = valueNoise3D(x, y, z, SALT_SHAPE_COARSE, 11) * 2.0 - 1.0;
		double fine = valueNoise3D(x, y, z, SALT_SHAPE_FINE, 5) * 2.0 - 1.0;
		return (coarse * 0.72 + fine * 0.28) * SHAPE_NOISE_STRENGTH;
	}

	private float valueNoise2D(int x, int z, int salt, int cellSize) {
		int x0 = Math.floorDiv(x, cellSize);
		int z0 = Math.floorDiv(z, cellSize);
		float tx = smoothStep((x - x0 * cellSize) / (float) cellSize);
		float tz = smoothStep((z - z0 * cellSize) / (float) cellSize);
		float near = Mth.lerp(tx, posRand(x0, 0, z0, salt), posRand(x0 + 1, 0, z0, salt));
		float far = Mth.lerp(tx, posRand(x0, 0, z0 + 1, salt), posRand(x0 + 1, 0, z0 + 1, salt));
		return Mth.lerp(tz, near, far);
	}

	private float valueNoise3D(int x, int y, int z, int salt, int cellSize) {
		int x0 = Math.floorDiv(x, cellSize);
		int y0 = Math.floorDiv(y, cellSize);
		int z0 = Math.floorDiv(z, cellSize);
		float tx = smoothStep((x - x0 * cellSize) / (float) cellSize);
		float ty = smoothStep((y - y0 * cellSize) / (float) cellSize);
		float tz = smoothStep((z - z0 * cellSize) / (float) cellSize);

		float lowNear = Mth.lerp(tx, posRand(x0, y0, z0, salt), posRand(x0 + 1, y0, z0, salt));
		float lowFar = Mth.lerp(tx, posRand(x0, y0, z0 + 1, salt), posRand(x0 + 1, y0, z0 + 1, salt));
		float highNear = Mth.lerp(tx, posRand(x0, y0 + 1, z0, salt), posRand(x0 + 1, y0 + 1, z0, salt));
		float highFar = Mth.lerp(tx, posRand(x0, y0 + 1, z0 + 1, salt), posRand(x0 + 1, y0 + 1, z0 + 1, salt));

		return Mth.lerp(ty, Mth.lerp(tz, lowNear, lowFar), Mth.lerp(tz, highNear, highFar));
	}

	private static float smoothStep(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	private float posRand(int x, int y, int z, int salt) {
		long h = this.seed ^ salt * 0x9E3779B97F4A7C15L;
		h += x * 0xFF51AFD7ED558CCDL;
		h ^= h >>> 29;
		h += y * 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 32;
		h += z * 0xD6E8FEB86659FD93L;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 40) * 0x1p-24F;
	}

	// ------------------------------------------------------------------

	private record Grid(int originX, int originY, int originZ, int sizeX, int sizeY, int sizeZ, byte[] data) {
		byte get(int x, int y, int z) {
			int lx = x - this.originX;
			int ly = y - this.originY;
			int lz = z - this.originZ;
			if (lx < 0 || ly < 0 || lz < 0 || lx >= this.sizeX || ly >= this.sizeY || lz >= this.sizeZ) {
				return CELL_ROCK;
			}
			return this.data[(ly * this.sizeZ + lz) * this.sizeX + lx];
		}
	}
}
