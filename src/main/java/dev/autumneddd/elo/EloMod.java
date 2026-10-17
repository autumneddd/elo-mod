package dev.autumneddd.elo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class EloMod implements ModInitializer {
	public static final String MOD_ID = "elo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static EloStorage storage;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			storage = new EloStorage(server.getWorldPath(LevelResource.ROOT).resolve("elo.json"));
			storage.load();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (storage != null) {
				storage.save();
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			EloStorage.Entry entry = entry(player);
			EloDisplay.update(player, entry.elo);
			storage.save();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> EloDisplay.remove(server, handler.player));

		ServerLivingEntityEvents.AFTER_DEATH.register(EloMod::onDeath);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
	}

	private static EloStorage.Entry entry(ServerPlayer player) {
		return storage.get(player.getUUID(), player.getScoreboardName());
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer victim) || storage == null) {
			return;
		}
		ServerPlayer killer = findKiller(victim, source);
		if (killer == null || killer == victim) {
			// Natural causes (fall, lava, mobs, /kill, ...) or suicide: no Elo change.
			return;
		}

		EloStorage.Entry killerEntry = entry(killer);
		EloStorage.Entry victimEntry = entry(victim);

		double gain = EloCalculator.change(killerEntry.elo, victimEntry.elo, 1.0);
		double loss = EloCalculator.change(victimEntry.elo, killerEntry.elo, 0.0);

		killerEntry.elo = EloCalculator.round1(killerEntry.elo + gain);
		victimEntry.elo = EloCalculator.round1(victimEntry.elo + loss);
		killerEntry.kills++;
		victimEntry.deaths++;
		storage.save();

		EloDisplay.update(killer, killerEntry.elo);
		EloDisplay.update(victim, victimEntry.elo);

		killer.sendSystemMessage(Component.literal("+" + format(gain) + " Elo ").withStyle(ChatFormatting.GREEN)
				.append(Component.literal("for killing " + victim.getScoreboardName() + " (now " + format(killerEntry.elo) + ")").withStyle(ChatFormatting.GRAY)));
		victim.sendSystemMessage(Component.literal(format(loss) + " Elo ").withStyle(ChatFormatting.RED)
				.append(Component.literal("for dying to " + killer.getScoreboardName() + " (now " + format(victimEntry.elo) + ")").withStyle(ChatFormatting.GRAY)));
	}

	private static ServerPlayer findKiller(ServerPlayer victim, DamageSource source) {
		// Direct kills, including projectiles (getEntity() is the shooter).
		if (source.getEntity() instanceof ServerPlayer player) {
			return player;
		}
		// Player hit the victim shortly before they died (e.g. knocked into lava / off a cliff),
		// same rule vanilla uses for "was doomed to fall by ..." death messages.
		if (victim.getKillCredit() instanceof ServerPlayer player) {
			return player;
		}
		return null;
	}

	private static String format(double value) {
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("elo")
				.executes(ctx -> showSelf(ctx))
				.then(Commands.literal("top").executes(EloMod::showTop))
				.then(Commands.literal("set")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("player", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(storage.names(), builder))
								.then(Commands.argument("elo", DoubleArgumentType.doubleArg(0))
										.executes(ctx -> setElo(ctx, DoubleArgumentType.getDouble(ctx, "elo"))))))
				.then(Commands.literal("reset")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("player", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(storage.names(), builder))
								.executes(ctx -> setElo(ctx, EloStorage.BASE_ELO))))
				.then(Commands.argument("player", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(storage.names(), builder))
						.executes(EloMod::showOther)));
	}

	private static int showSelf(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		sendStats(ctx.getSource(), entry(player));
		return 1;
	}

	private static int showOther(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "player");
		Optional<EloStorage.Entry> entry = storage.findByName(name);
		if (entry.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("No Elo data for " + name));
			return 0;
		}
		sendStats(ctx.getSource(), entry.get());
		return 1;
	}

	private static void sendStats(CommandSourceStack source, EloStorage.Entry entry) {
		source.sendSuccess(() -> Component.literal(entry.name + ": ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(format(entry.elo) + " Elo").withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" (" + entry.kills + " kills, " + entry.deaths + " deaths)").withStyle(ChatFormatting.GRAY)), false);
	}

	private static int showTop(CommandContext<CommandSourceStack> ctx) {
		List<EloStorage.Entry> top = storage.top(10);
		MutableComponent message = Component.literal("Top Elo").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		for (int i = 0; i < top.size(); i++) {
			EloStorage.Entry e = top.get(i);
			message.append(Component.literal("\n" + (i + 1) + ". ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(e.name + " ").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(format(e.elo)).withStyle(ChatFormatting.GOLD));
		}
		ctx.getSource().sendSuccess(() -> message, false);
		return top.size();
	}

	private static int setElo(CommandContext<CommandSourceStack> ctx, double value) {
		String name = StringArgumentType.getString(ctx, "player");
		MinecraftServer server = ctx.getSource().getServer();
		ServerPlayer online = server.getPlayerList().getPlayerByName(name);
		EloStorage.Entry entry = online != null ? entry(online) : storage.findByName(name).orElse(null);
		if (entry == null) {
			ctx.getSource().sendFailure(Component.literal("No Elo data for " + name));
			return 0;
		}
		entry.elo = EloCalculator.round1(value);
		storage.save();
		if (online != null) {
			EloDisplay.update(online, entry.elo);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Set " + entry.name + "'s Elo to " + format(entry.elo)), true);
		return 1;
	}
}
