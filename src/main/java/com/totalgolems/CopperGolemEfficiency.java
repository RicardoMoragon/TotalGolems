package com.totalgolems;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Efficiency for the vanilla copper golem, given one enchanted book at a time and strictly in order.
 * <ul>
 * <li>I, II and III: it carries up to 32, 48 and a whole stack of 64 at a time instead of 16, never
 * more than the item stacks to.</li>
 * <li>IV: the pauses to open, take from and put into a chest last half as long.</li>
 * <li>V: those pauses are as short as they can be, and it walks a fifth faster.</li>
 * </ul>
 * The level is kept as a Fabric data attachment, saved with the golem, sent to the client for the
 * glint and the copper dots on its forehead, and kept in its statue when it oxidises into one.
 */
public final class CopperGolemEfficiency {
	public static final int MAX_LEVEL = 5;
	/** How long vanilla's golem spends at a chest, and the least it can be made to spend. */
	public static final int VANILLA_INTERACTION_TICKS = 60;
	private static final int QUICKEST_INTERACTION_TICKS = 1;
	private static final int VANILLA_ITEMS_PER_TRIP = 16;
	private static final Identifier SPEED_MODIFIER = TotalGolems.id("efficiency_speed");
	private static final double SPEED_BONUS = 0.2;

	public static final AttachmentType<Integer> LEVEL = AttachmentRegistry.create(
		TotalGolems.id("copper_golem_efficiency"),
		builder -> builder.persistent(Codec.intRange(0, MAX_LEVEL)).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
	);

	private CopperGolemEfficiency() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			// It gives back what was spent on it, as a single book of its level.
			int level = level(entity);
			if (entity instanceof CopperGolem golem && level > 0 && golem.level() instanceof ServerLevel serverLevel) {
				golem.spawnAtLocation(serverLevel, book(serverLevel, level));
			}
		});
	}

	public static int level(final AttachmentTarget target) {
		return target.getAttachedOrElse(LEVEL, 0);
	}

	public static void setLevel(final CopperGolem golem, final int level) {
		if (level <= 0) {
			golem.removeAttached(LEVEL);
		} else {
			golem.setAttached(LEVEL, Math.min(level, MAX_LEVEL));
		}

		AttributeInstance speed = golem.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED_MODIFIER);
			if (level >= MAX_LEVEL) {
				speed.addPermanentModifier(new AttributeModifier(SPEED_MODIFIER, SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
	}

	/** How many items it takes from a chest in one go, before the item's own stack size has its say. */
	public static int itemsPerTrip(final int level) {
		return VANILLA_ITEMS_PER_TRIP + 16 * Math.min(level, 3);
	}

	/** How many ticks it spends at a chest each time. */
	public static int interactionTicks(final int level) {
		return level >= 5 ? QUICKEST_INTERACTION_TICKS : level == 4 ? VANILLA_INTERACTION_TICKS / 2 : VANILLA_INTERACTION_TICKS;
	}

	private static Holder<Enchantment> efficiency(final Level level) {
		HolderLookup.RegistryLookup<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		return enchantments.getOrThrow(Enchantments.EFFICIENCY);
	}

	public static ItemStack book(final Level level, final int efficiencyLevel) {
		return EnchantmentHelper.createBook(new EnchantmentInstance(efficiency(level), efficiencyLevel));
	}

	/**
	 * Right-clicking a copper golem with an enchanted book. Only an Efficiency book of exactly the next
	 * level is taken; any other is refused, kept, and the player told which one it needs. Returns null
	 * for anything that is not an Efficiency book, so that the golem's own interactions carry on.
	 */
	public static @Nullable InteractionResult interact(final CopperGolem golem, final Player player, final InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Items.ENCHANTED_BOOK)) {
			return null;
		}

		Level world = golem.level();
		int offered = held.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(efficiency(world));
		if (offered <= 0) {
			return null;
		}

		if (world instanceof ServerLevel serverLevel) {
			int current = level(golem);
			if (current >= MAX_LEVEL) {
				Compat.overlay(player, Component.translatable("message.totalgolems.efficiency_max"));
			} else if (offered != current + 1) {
				Compat.overlay(player, Component.translatable("message.totalgolems.efficiency_needed", Component.translatable("enchantment.level." + (current + 1))));
			} else {
				held.consume(1, player);
				setLevel(golem, offered);
				serverLevel.playSound(null, golem, SoundEvents.ENCHANTMENT_TABLE_USE, golem.getSoundSource(), 1.0F, 1.0F);
			}
		}

		return InteractionResult.SUCCESS;
	}
}
