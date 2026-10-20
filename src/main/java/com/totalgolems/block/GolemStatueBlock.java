package com.totalgolems.block;

import com.totalgolems.entity.WorkerGolem;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A golem of the mod that has stood still for good, as the copper golem's statue but in the golem's own skin. It keeps
 * wearing while it stands, from {@link #AGE} 0 (as new) to 3, unless waxed with honeycomb. An axe takes the wax off, then
 * scrapes off a stage of wear at a time, and on a statue with no wear left it brings the golem back to life, the same
 * golem it was. Anything else in hand turns it to its next pose.
 */
public class GolemStatueBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<CopperGolemStatueBlock.Pose> POSE = BlockStateProperties.COPPER_GOLEM_POSE;
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
	public static final BooleanProperty WAXED = BooleanProperty.create("waxed");
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final VoxelShape SHAPE = Block.column(10.0, 0.0, 14.0);
	/** The chance per random tick of wearing a stage further, as for a lone block of copper. */
	private static final float WEATHER_CHANCE = 0.05688889F;

	private final String textureId;
	private final Supplier<EntityType<? extends WorkerGolem>> golemType;

	public GolemStatueBlock(final String textureId, final Supplier<EntityType<? extends WorkerGolem>> golemType, final BlockBehaviour.Properties properties) {
		super(properties);
		this.textureId = textureId;
		this.golemType = golemType;
		this.registerDefaultState(
			this.defaultBlockState()
				.setValue(FACING, Direction.NORTH)
				.setValue(POSE, CopperGolemStatueBlock.Pose.STANDING)
				.setValue(AGE, 0)
				.setValue(WAXED, false)
				.setValue(WATERLOGGED, false)
		);
	}

	/** The golem's textures are named after this, as {@link WorkerGolem#getTextureId()}. */
	public String getTextureId() {
		return this.textureId;
	}

	public EntityType<? extends WorkerGolem> getGolemType() {
		return this.golemType.get();
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING, POSE, AGE, WAXED, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(final BlockPlaceContext context) {
		FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, fluid.is(Fluids.WATER));
	}

	@Override
	protected boolean isRandomlyTicking(final BlockState state) {
		return !state.getValue(WAXED) && state.getValue(AGE) < 3;
	}

	@Override
	protected void randomTick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
		if (random.nextFloat() < WEATHER_CHANCE) {
			level.setBlockAndUpdate(pos, state.setValue(AGE, state.getValue(AGE) + 1));
		}
	}

	@Override
	protected InteractionResult useItemOn(
		final ItemStack itemStack,
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player,
		final InteractionHand hand,
		final BlockHitResult hitResult
	) {
		if (itemStack.is(Items.HONEYCOMB)) {
			if (state.getValue(WAXED)) {
				return InteractionResult.PASS;
			}

			if (!level.isClientSide()) {
				level.setBlockAndUpdate(pos, state.setValue(WAXED, true));
				level.levelEvent(null, LevelEvent.PARTICLES_WAX_ON, pos, 0);
				level.playSound(null, pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0F, 1.0F);
				itemStack.consume(1, player);
			}

			return InteractionResult.SUCCESS;
		}

		if (!itemStack.is(ItemTags.AXES)) {
			level.playSound(null, pos, SoundEvents.COPPER_GOLEM_BECOME_STATUE, SoundSource.BLOCKS);
			level.setBlockAndUpdate(pos, state.setValue(POSE, state.getValue(POSE).getNextPose()));
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
			return InteractionResult.SUCCESS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (state.getValue(WAXED)) {
			level.setBlockAndUpdate(pos, state.setValue(WAXED, false));
			level.levelEvent(null, LevelEvent.PARTICLES_WAX_OFF, pos, 0);
			level.playSound(null, pos, SoundEvents.AXE_WAX_OFF.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
		} else if (state.getValue(AGE) > 0) {
			level.setBlockAndUpdate(pos, state.setValue(AGE, state.getValue(AGE) - 1));
			level.levelEvent(null, LevelEvent.PARTICLES_SCRAPE, pos, 0);
			level.playSound(null, pos, SoundEvents.AXE_SCRAPE.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
		} else if (level.getBlockEntity(pos) instanceof GolemStatueBlockEntity statue) {
			WorkerGolem golem = statue.revive(state);
			if (golem == null) {
				return InteractionResult.PASS;
			}

			level.removeBlock(pos, false);
			level.addFreshEntity(golem);
		}

		itemStack.hurtAndBreak(1, player, hand.asEquipmentSlot());
		return InteractionResult.SUCCESS;
	}

	@Override
	protected ItemStack getCloneItemStack(final LevelReader level, final BlockPos pos, final BlockState state, final boolean includeData) {
		ItemStack stack = super.getCloneItemStack(level, pos, state, includeData);
		return level.getBlockEntity(pos) instanceof GolemStatueBlockEntity statue ? statue.withState(stack, state) : stack;
	}

	@Override
	protected BlockState rotate(final BlockState state, final Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(final BlockState state, final Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean isPathfindable(final BlockState state, final PathComputationType type) {
		return type == PathComputationType.WATER && state.getFluidState().is(FluidTags.WATER);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
		return new GolemStatueBlockEntity(worldPosition, blockState);
	}

	@Override
	protected FluidState getFluidState(final BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState updateShape(
		final BlockState state,
		final LevelReader level,
		final ScheduledTickAccess ticks,
		final BlockPos pos,
		final Direction directionToNeighbour,
		final BlockPos neighbourPos,
		final BlockState neighbourState,
		final RandomSource random
	) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}

		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}
}
