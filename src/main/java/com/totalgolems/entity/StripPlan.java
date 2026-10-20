package com.totalgolems.entity;

import com.totalgolems.GolemTier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The poppy pattern: strip mining at the height the golem was given the flower, inside the three by
 * three chunks around the one it stood in.
 * <p>
 * First a main tunnel, from where it stands to the edge of the area in the direction the player was
 * facing, then back the whole way to the other edge. Then branch tunnels across the main one, each from
 * edge to edge, with three blocks left standing between one branch and the next: every four blocks for
 * the iron golem's one-wide tunnel, every six for the three-wide tunnels of the others.
 * <p>
 * The whole route is a list of legs, each a straight run to a target cell. The golem digs (or just walks,
 * where it has dug already) along one leg after another.
 */
public class StripPlan {
	/** Blocks left standing between two branches. */
	public static final int GAP = 3;
	/** How far past the edge of the area it still goes after a vein of ore. */
	public static final int ORE_MARGIN = 8;

	private final BlockPos origin;
	private final Direction forward;
	private final int radius;
	private final int height;
	private final int minX;
	private final int maxX;
	private final int minZ;
	private final int maxZ;
	/** Where the main tunnel runs across the area, on the axis it does not run along. */
	private final int mainCross;
	private final int pitch;
	private final int minBranch;
	private final int maxBranch;
	private final List<Leg> legs = new ArrayList<>();
	private int legIndex;

	/**
	 * A straight run towards {@code target} in {@code direction}. Legs along the main tunnel are marked, so
	 * that when one cannot be walked the branch it leads to can be skipped.
	 */
	public record Leg(BlockPos target, Direction direction, boolean main) {
	}

	private StripPlan(final BlockPos origin, final Direction forward, final GolemTier tier) {
		this.origin = origin.immutable();
		this.forward = forward;
		this.radius = tier.tunnelRadius();
		this.height = tier.tunnelHeight();
		int chunkX = origin.getX() >> 4;
		int chunkZ = origin.getZ() >> 4;
		this.minX = (chunkX - 1) << 4;
		this.maxX = ((chunkX + 2) << 4) - 1;
		this.minZ = (chunkZ - 1) << 4;
		this.maxZ = ((chunkZ + 2) << 4) - 1;
		this.pitch = 2 * this.radius + 1 + GAP;
		this.mainCross = Mth.clamp(this.cross(origin), this.minCross() + this.radius, this.maxCross() - this.radius);
		this.minBranch = this.minAlong() + this.radius;
		this.maxBranch = this.maxAlong() - this.radius;
		this.buildLegs();
	}

	public static StripPlan create(final BlockPos origin, final Direction forward, final GolemTier tier) {
		return new StripPlan(origin, forward, tier);
	}

	public static StripPlan load(final BlockPos origin, final Direction forward, final GolemTier tier, final int legIndex) {
		StripPlan plan = new StripPlan(origin, forward, tier);
		plan.legIndex = Mth.clamp(legIndex, 0, plan.legs.size());
		return plan;
	}

	public int legIndex() {
		return this.legIndex;
	}

	/** The height of the floor of the tunnels: where the golem stands. */
	public int y() {
		return this.origin.getY();
	}

	// ------------------------------------------------------------------------------------ axes

	private boolean alongX() {
		return this.forward.getAxis() == Direction.Axis.X;
	}

	private int along(final BlockPos pos) {
		return this.alongX() ? pos.getX() : pos.getZ();
	}

	private int cross(final BlockPos pos) {
		return this.alongX() ? pos.getZ() : pos.getX();
	}

	private int minAlong() {
		return this.alongX() ? this.minX : this.minZ;
	}

	private int maxAlong() {
		return this.alongX() ? this.maxX : this.maxZ;
	}

	private int minCross() {
		return this.alongX() ? this.minZ : this.minX;
	}

	private int maxCross() {
		return this.alongX() ? this.maxZ : this.maxX;
	}

	private BlockPos cell(final int along, final int cross) {
		return this.alongX() ? new BlockPos(along, this.y(), cross) : new BlockPos(cross, this.y(), along);
	}

	private Direction alongDirection(final boolean positive) {
		return Direction.fromAxisAndDirection(this.forward.getAxis(), positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
	}

	private Direction crossDirection(final boolean positive) {
		Direction.Axis axis = this.alongX() ? Direction.Axis.Z : Direction.Axis.X;
		return Direction.fromAxisAndDirection(axis, positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
	}

	// ------------------------------------------------------------------------------------ route

	private void buildLegs() {
		boolean forwards = this.forward.getAxisDirection() == Direction.AxisDirection.POSITIVE;
		int farEnd = forwards ? this.maxAlong() : this.minAlong();
		int nearEnd = forwards ? this.minAlong() : this.maxAlong();
		this.legs.add(new Leg(this.cell(farEnd, this.mainCross), this.forward, true));
		this.legs.add(new Leg(this.cell(nearEnd, this.mainCross), this.forward.getOpposite(), true));

		// Branches every pitch blocks from where it started, taken from the end it is at after the main tunnel.
		List<Integer> branches = new ArrayList<>();
		int start = this.along(this.origin);
		for (int at = start - (start - this.minBranch) / this.pitch * this.pitch; at <= this.maxBranch; at += this.pitch) {
			if (at >= this.minBranch) {
				branches.add(at);
			}
		}

		if (!forwards) {
			// It finished the main tunnel at the far, high end: work back down.
			branches = branches.reversed();
		} else {
			branches = new ArrayList<>(branches);
		}

		int at = nearEnd;
		for (int branch : branches) {
			if (branch != at) {
				this.legs.add(new Leg(this.cell(branch, this.mainCross), this.alongDirection(branch > at), true));
			}

			BlockPos junction = this.cell(branch, this.mainCross);
			this.legs.add(new Leg(this.cell(branch, this.minCross()), this.crossDirection(false), false));
			this.legs.add(new Leg(junction, this.crossDirection(true), false));
			this.legs.add(new Leg(this.cell(branch, this.maxCross()), this.crossDirection(true), false));
			this.legs.add(new Leg(junction, this.crossDirection(false), false));
			at = branch;
		}
	}

	/** The leg being worked on, or null once the whole route is done. */
	public @Nullable Leg currentLeg() {
		return this.legIndex < this.legs.size() ? this.legs.get(this.legIndex) : null;
	}

	public void nextLeg() {
		this.legIndex++;
	}

	/** A leg along the main tunnel cannot be walked: the branch it leads to is out of reach, so on to the next one. */
	public void skipBranch() {
		this.legIndex++;
		while (this.legIndex < this.legs.size() && !this.legs.get(this.legIndex).main()) {
			this.legIndex++;
		}
	}

	/**
	 * Which way to go from {@code cell} to the target of {@code leg}. Along the main tunnel that depends on
	 * where the golem actually is, since something may have stopped it short of the end; null when it is there.
	 */
	public @Nullable Direction heading(final BlockPos cell, final Leg leg) {
		if (!leg.main() || this.legIndex < 2) {
			return this.isReached(cell, leg) ? null : leg.direction();
		}

		int along = this.along(leg.target()) - this.along(cell);
		return along == 0 ? null : this.alongDirection(along > 0);
	}

	/** The cell on the line of {@code leg} level with {@code cell}. */
	public BlockPos snap(final BlockPos cell, final Leg leg) {
		BlockPos target = leg.target();
		return leg.direction().getAxis() == Direction.Axis.X
			? new BlockPos(cell.getX(), this.y(), target.getZ())
			: new BlockPos(target.getX(), this.y(), cell.getZ());
	}

	/** Whether {@code cell} is at the target of {@code leg}, or beyond it. */
	public boolean isReached(final BlockPos cell, final Leg leg) {
		BlockPos offset = leg.target().subtract(cell);
		return offset.getX() * leg.direction().getStepX() + offset.getZ() * leg.direction().getStepZ() <= 0;
	}

	// ------------------------------------------------------------------------------------ area

	/** Whether {@code pos} is inside the three by three chunks, at any height. */
	public boolean contains(final BlockPos pos) {
		return pos.getX() >= this.minX && pos.getX() <= this.maxX && pos.getZ() >= this.minZ && pos.getZ() <= this.maxZ;
	}

	/** Whether {@code pos} is close enough to the area to go after ore there: within {@value #ORE_MARGIN} blocks of it. */
	public boolean reachesForOre(final BlockPos pos) {
		return pos.getX() >= this.minX - ORE_MARGIN && pos.getX() <= this.maxX + ORE_MARGIN && pos.getZ() >= this.minZ - ORE_MARGIN && pos.getZ() <= this.maxZ + ORE_MARGIN;
	}

	/** Whether {@code pos} is in one of the tunnels of the plan, dug or yet to be. */
	public boolean isInDiggings(final BlockPos pos) {
		if (!this.contains(pos) || pos.getY() < this.y() || pos.getY() >= this.y() + this.height) {
			return false;
		}

		if (Math.abs(this.cross(pos) - this.mainCross) <= this.radius) {
			return true;
		}

		int offset = Math.floorMod(this.along(pos) - this.along(this.origin), this.pitch);
		int branch = this.along(pos) - (offset <= this.radius ? offset : offset - this.pitch);
		return Math.min(offset, this.pitch - offset) <= this.radius && branch >= this.minBranch && branch <= this.maxBranch;
	}

	/** What the golem digs to step into {@code next} going in {@code direction}: the tunnel's cross-section, top first, within the area. */
	public List<BlockPos> slice(final BlockPos next, final Direction direction) {
		Direction side = direction.getClockWise();
		List<BlockPos> blocks = new ArrayList<>();
		for (int up = this.height - 1; up >= 0; up--) {
			for (int offset = -this.radius; offset <= this.radius; offset++) {
				BlockPos pos = next.above(up).relative(side, offset);
				if (this.contains(pos)) {
					blocks.add(pos);
				}
			}
		}

		return blocks;
	}
}
