package com.example.chatmagic;

import com.google.gson.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChatMagic implements ModInitializer {
	private static final String PREFIX = "!";
	private static final String MODEL = "claude-haiku-5-5";
	private static final int MAX_COMMANDS = 25;

	// Comandos que a IA nunca pode executar
	private static final Set<String> BLOCKED = Set.of(
		"op", "deop", "stop", "ban", "ban-ip", "pardon", "pardon-ip", "kick",
		"whitelist", "publish", "setidletimeout", "transfer", "jfr", "perf", "debug"
	);

	private static final String SYSTEM_PROMPT = """
		You convert a Minecraft player's request into Minecraft Java Edition 1.21.1 commands.
		Respond with ONLY a JSON array of command strings, no explanation, no markdown fences.
		Rules:
		- Commands have no leading slash.
		- The commands run as the player, so use @s for the player and relative coordinates (~ ~ ~).
		- Use only valid 1.21.1 syntax (e.g. item components like give @s diamond_sword[enchantments={levels:{"minecraft:sharpness":5}}]).
		- For "rain of X" style effects, use a few commands (summon falling items/blocks above the player, particle, etc).
		- Max 25 commands. Never use op, stop, ban, kick, whitelist.
		- If the request is impossible or unclear, respond with [] .
		""";

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@Override
	public void onInitialize() {
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			String text = message.getSignedContent();
			if (text.startsWith(PREFIX)) {
				handle(sender, text.substring(PREFIX.length()).trim());
			}
		});
	}

	private String loadApiKey() {
		String env = System.getenv("ANTHROPIC_API_KEY");
		if (env != null && !env.isBlank()) return env.trim();
		try {
			Path file = FabricLoader.getInstance().getConfigDir().resolve("chatmagic.txt");
			if (Files.exists(file)) return Files.readString(file).trim();
		} catch (Exception ignored) {}
		return null;
	}

	private void handle(ServerPlayerEntity player, String request) {
		if (request.isEmpty()) return;
		MinecraftServer server = player.getServer();
		String key = loadApiKey();
		if (key == null) {
			player.sendMessage(Text.literal("§cChatMagic: coloque sua chave em config/chatmagic.txt"), false);
			return;
		}
		player.sendMessage(Text.literal("§7✨ Pensando..."), false);

		String context = "Player: " + player.getName().getString()
			+ "\nDimension: " + player.getWorld().getRegistryKey().getValue()
			+ "\nRequest: " + request;

		JsonObject body = new JsonObject();
		body.addProperty("model", MODEL);
		body.addProperty("max_tokens", 1000);
		body.addProperty("system", SYSTEM_PROMPT);
		JsonArray msgs = new JsonArray();
		JsonObject m = new JsonObject();
		m.addProperty("role", "user");
		m.addProperty("content", context);
		msgs.add(m);
		body.add("messages", msgs);

		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create("https://api.anthropic.com/v1/messages"))
			.timeout(Duration.ofSeconds(30))
			.header("content-type", "application/json")
			.header("x-api-key", key)
			.header("anthropic-version", "2023-06-01")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
			.build();

		http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
			try {
				if (res.statusCode() != 200) {
					server.execute(() -> player.sendMessage(Text.literal("§cErro da API: " + res.statusCode()), false));
					return;
				}
				JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
				String out = root.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
				out = out.replaceAll("(?s)```(?:json)?", "").trim();
				JsonArray arr = JsonParser.parseString(out).getAsJsonArray();
				List<String> cmds = new ArrayList<>();
				for (JsonElement e : arr) {
					if (cmds.size() >= MAX_COMMANDS) break;
					String c = e.getAsString().trim();
					if (c.startsWith("/")) c = c.substring(1);
					if (!c.isEmpty()) cmds.add(c);
				}
				server.execute(() -> run(server, player, cmds));
			} catch (Exception ex) {
				server.execute(() -> player.sendMessage(Text.literal("§cNão consegui entender a resposta da IA."), false));
			}
		}).exceptionally(ex -> {
			server.execute(() -> player.sendMessage(Text.literal("§cFalha de conexão com a API."), false));
			return null;
		});
	}

	private void run(MinecraftServer server, ServerPlayerEntity player, List<String> cmds) {
		if (cmds.isEmpty()) {
			player.sendMessage(Text.literal("§eNão sei como fazer isso."), false);
			return;
		}
		ServerCommandSource source = player.getCommandSource().withLevel(4);
		for (String cmd : cmds) {
			String root = cmd.split("\\s+")[0].toLowerCase();
			if (root.contains(":")) root = root.substring(root.indexOf(':') + 1);
			if (BLOCKED.contains(root)) {
				player.sendMessage(Text.literal("§cBloqueado: /" + root), false);
				continue;
			}
			try {
				server.getCommandManager().executeWithPrefix(source, cmd);
			} catch (Exception ex) {
				player.sendMessage(Text.literal("§cFalhou: /" + cmd), false);
			}
		}
	}
}
