package dev.autumneddd.elo;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Shows "1000 | name" above heads and in the tab list using a per-player scoreboard team prefix.
 */
public final class EloDisplay {
	private EloDisplay() {
	}

	private static String teamName(ServerPlayer player) {
		return "elo_" + player.getUUID().toString().replace("-", "").substring(0, 12);
	}

	public static void update(ServerPlayer player, double elo) {
		ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
		String name = teamName(player);
		PlayerTeam team = scoreboard.getPlayerTeam(name);
		if (team == null) {
			team = scoreboard.addPlayerTeam(name);
		}
		team.setPlayerPrefix(Component.empty()
				.append(Component.literal(Long.toString(Math.round(elo))).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY)));
		if (team != scoreboard.getPlayersTeam(player.getScoreboardName())) {
			scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
		}
	}

	public static void remove(MinecraftServer server, ServerPlayer player) {
		ServerScoreboard scoreboard = server.getScoreboard();
		PlayerTeam team = scoreboard.getPlayerTeam(teamName(player));
		if (team != null) {
			scoreboard.removePlayerTeam(team);
		}
	}
}
