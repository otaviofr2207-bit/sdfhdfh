package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.config.PluginConfig;
import net.zavyn.zavyncore.database.Database;
import net.zavyn.zavyncore.integration.FloodgateIntegration;
import net.zavyn.zavyncore.util.Msg;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * /zavyncore reload|info|debug|database|version
 * Comando administrativo central do plugin.
 */
public final class ZavynCoreCommand implements SimpleCommand {

    public static final String VERSION = "1.0.0";

    private final ProxyServer proxy;
    private final Logger logger;
    private final Messages messages;
    private final Database database;
    private final FloodgateIntegration floodgate;
    private final Executor executor;
    private final Runnable reloadAction;

    public ZavynCoreCommand(ProxyServer proxy, Logger logger, Messages messages, Database database,
                             FloodgateIntegration floodgate, Executor executor, Runnable reloadAction) {
        this.proxy = proxy;
        this.logger = logger;
        this.messages = messages;
        this.database = database;
        this.floodgate = floodgate;
        this.executor = executor;
        this.reloadAction = reloadAction;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.zavyncore")));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadAction.run();
                source.sendMessage(Msg.parse(messages.get("zavyncore.reload-success")));
            }
            case "version" -> source.sendMessage(Msg.parse(messages.get("zavyncore.version"), Map.of("version", VERSION)));
            case "info" -> source.sendMessage(Msg.parse(messages.get("zavyncore.info"), Map.of(
                    "version", VERSION,
                    "players", String.valueOf(proxy.getPlayerCount()),
                    "servers", String.valueOf(proxy.getAllServers().size()),
                    "floodgate", floodgate.isPresent() ? "ativo" : "inativo"
            )));
            case "debug" -> source.sendMessage(Msg.parse(messages.get("zavyncore.debug"), Map.of(
                    "online-mode", String.valueOf(proxy.getConfiguration().isOnlineMode()),
                    "floodgate", floodgate.isPresent() ? "detectado" : "nao detectado",
                    "servers", String.join(", ", proxy.getAllServers().stream().map(s -> s.getServerInfo().getName()).toList())
            )));
            case "database" -> CompletableFuture.runAsync(() -> {
                try (var connection = database.getConnection()) {
                    boolean valid = connection.isValid(2);
                    source.sendMessage(Msg.parse(valid ? messages.get("zavyncore.database-ok") : messages.get("zavyncore.database-fail")));
                } catch (Exception e) {
                    logger.error("[ZavynCore] Erro ao testar conexao com o banco: {}", e.getMessage());
                    source.sendMessage(Msg.parse(messages.get("zavyncore.database-fail")));
                }
            }, executor);
            default -> source.sendMessage(Msg.parse(messages.get("usage.zavyncore")));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            return List.of("reload", "info", "debug", "database", "version");
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.admin");
    }
}
