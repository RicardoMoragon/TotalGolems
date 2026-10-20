package com.totalgolems.block;

import com.totalgolems.entity.WorkerGolem;
import com.totalgolems.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Holds the golem a statue was, saved whole, so that it comes back as it stopped: with its flower, its tool, its
 * chest and whatever it was carrying. The statue keeps it when broken and placed again (as the item's custom data).
 */
public class GolemStatueBlockEntity extends BlockEntity {
	private @Nullable CompoundTag golem;

	public GolemStatueBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
		super(ModBlockEntities.GOLEM_STATUE, worldPosition, blockState);
	}

	public void keep(final WorkerGolem golem) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, golem.registryAccess());
		golem.saveWithoutId(output);
		this.golem = output.buildResult();
		this.setChanged();
	}

	/** The golem standing where the statue was, or null if it could not be made. */
	public @Nullable WorkerGolem revive(final BlockState state) {
		if (!(this.level instanceof ServerLevel level) || !(state.getBlock() instanceof GolemStatueBlock block)) {
			return null;
		}

		WorkerGolem golem = block.getGolemType().create(level, EntitySpawnReason.TRIGGERED);
		if (golem == null) {
			return null;
		}

		if (this.golem != null) {
			golem.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), this.golem));
			// A statue copied in creative would otherwise bring the same golem back twice.
			if (level.getEntity(golem.getUUID()) != null) {
				golem.setUUID(UUID.randomUUID());
			}
		}

		BlockPos pos = this.getBlockPos();
		float yRot = state.getValue(GolemStatueBlock.FACING).toYRot();
		golem.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yRot, 0.0F);
		golem.yHeadRot = yRot;
		golem.yBodyRot = yRot;
		golem.setDeltaMovement(Vec3.ZERO);
		golem.wakeFromStatue();
		golem.playSound(SoundEvents.COPPER_GOLEM_SPAWN);
		return golem;
	}

	/** {@code stack} as the item of this statue: with the golem in it and the statue's pose, wear and wax. */
	public ItemStack withState(final ItemStack stack, final BlockState state) {
		stack.applyComponents(this.collectComponents());
		stack.set(
			DataComponents.BLOCK_STATE,
			BlockItemStateProperties.EMPTY
				.with(GolemStatueBlock.POSE, state.getValue(GolemStatueBlock.POSE))
				.with(GolemStatueBlock.AGE, state.getValue(GolemStatueBlock.AGE))
				.with(GolemStatueBlock.WAXED, state.getValue(GolemStatueBlock.WAXED))
		);
		return stack;
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.golem = input.read("golem", CompoundTag.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("golem", CompoundTag.CODEC, this.golem);
	}

	@Override
	protected void applyImplicitComponents(final DataComponentGetter components) {
		super.applyImplicitComponents(components);
		CustomData data = components.get(DataComponents.CUSTOM_DATA);
		this.golem = data == null ? null : data.copyTag();
	}

	@Override
	protected void collectImplicitComponents(final DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		if (this.golem != null) {
			components.set(DataComponents.CUSTOM_DATA, CustomData.of(this.golem));
		}
	}

	@Override
	public void removeComponentsFromTag(final ValueOutput output) {
		super.removeComponentsFromTag(output);
		output.discard("golem");
	}
}
