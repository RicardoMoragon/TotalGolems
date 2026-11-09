package com.totalgolems;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * The few calls whose names differ between the Minecraft versions the mod is built for, in one place so the rest of
 * the code reads the same on all of them. The {@code //?} comments are Stonecutter's: see stonecutter.gradle.
 */
public final class Compat {
	private Compat() {
	}

	/** The chunk {@code pos} is in. */
	public static ChunkPos chunkAt(final BlockPos pos) {
		//? if >=26.1 {
		return ChunkPos.containing(pos);
		//?} else {
		/*return new ChunkPos(pos);
		*///?}
	}

	public static int chunkX(final ChunkPos chunk) {
		//? if >=26.1 {
		return chunk.x();
		//?} else {
		/*return chunk.x;
		*///?}
	}

	public static int chunkZ(final ChunkPos chunk) {
		//? if >=26.1 {
		return chunk.z();
		//?} else {
		/*return chunk.z;
		*///?}
	}

	/** Shows {@code message} above the player's hotbar. */
	public static void overlay(final Player player, final Component message) {
		//? if >=26.1 {
		player.sendOverlayMessage(message);
		//?} else {
		/*player.displayClientMessage(message, true);
		*///?}
	}

	/** The time of day of the overworld, which counts the days, wherever {@code level} is. */
	public static long overworldTime(final Level level) {
		//? if >=26.1 {
		return level.getOverworldClockTime();
		//?} else {
		/*return level instanceof ServerLevel server ? server.getServer().overworld().getDayTime() : level.getDayTime();
		*///?}
	}
}
