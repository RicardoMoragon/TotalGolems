package com.totalgolems;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Every golem, the copper golem too, waves at a player the first time it sees them each Minecraft day.
 * <p>
 * The server keeps, per golem, the day it last greeted each player (a Fabric data attachment, saved with the
 * golem) and sends the wave as an entity event; the client notes when it came, and the model raises a hand
 * for {@link #WAVE_TICKS}.
 */
public final class GolemGreeting {
	/** The entity event of a wave, clear of vanilla's. */
	public static final byte WAVE_EVENT = 90;
	public static final int WAVE_TICKS = 40;
	private static final double SIGHT_RANGE = 12.0;
	private static final int CHECK_INTERVAL = 10;
	private static final long DAY_TICKS = 24000L;

	private static final AttachmentType<Map<UUID, Long>> GREETED = AttachmentRegistry.create(
		TotalGolems.id("greeted"), builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG))
	);
	/** Client side: the tick the golem last started waving. */
	private static final AttachmentType<Integer> WAVE_START = AttachmentRegistry.create(TotalGolems.id("wave_start"));

	private GolemGreeting() {
	}

	public static void init() {
		// Loads the class, registering the attachments.
	}

	/** Looks out for players now and then, and waves at the first one it has not greeted today. */
	public static void tick(final Mob golem, final ServerLevel level) {
		if ((golem.tickCount + golem.getId()) % CHECK_INTERVAL != 0 || golem.isNoAi()) {
			return;
		}

		long day = Compat.overworldTime(level) / DAY_TICKS;
		Map<UUID, Long> greeted = golem.getAttachedOrElse(GREETED, Map.of());
		for (Player player : level.getEntitiesOfClass(Player.class, golem.getBoundingBox().inflate(SIGHT_RANGE), player -> !player.isSpectator())) {
			Long last = greeted.get(player.getUUID());
			if ((last == null || last != day) && golem.getSensing().hasLineOfSight(player)) {
				// Only today's greetings are worth keeping.
				Map<UUID, Long> updated = new HashMap<>(greeted);
				updated.values().removeIf(other -> other != day);
				updated.put(player.getUUID(), day);
				golem.setAttached(GREETED, updated);
				golem.getLookControl().setLookAt(player);
				level.broadcastEntityEvent(golem, WAVE_EVENT);
				return;
			}
		}
	}

	/** Whether {@code golem} has waved at {@code player} today. */
	public static boolean greetedToday(final Mob golem, final Player player) {
		Long last = golem.getAttachedOrElse(GREETED, Map.<UUID, Long>of()).get(player.getUUID());
		return last != null && last == Compat.overworldTime(golem.level()) / DAY_TICKS;
	}

	/** Client side, on the wave's entity event. */
	public static void startWave(final Entity golem) {
		golem.setAttached(WAVE_START, golem.tickCount);
	}

	/** Client side: how long the golem has been waving, or a negative number if it is not. */
	public static float waveTicks(final Entity golem, final float partialTicks) {
		Integer start = golem.getAttached(WAVE_START);
		float ticks = start == null ? -1.0F : golem.tickCount - start + partialTicks;
		return ticks <= WAVE_TICKS ? ticks : -1.0F;
	}
}
