package net.zavyn.zavyncore.service;

import net.zavyn.zavyncore.config.PluginConfig;
import net.zavyn.zavyncore.database.dao.PlayerDao;
import net.zavyn.zavyncore.model.PlayerAccount;
import net.zavyn.zavyncore.util.TimeUtil;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Sistema de "novo IP" e confirmacao periodica (itens 11 e 12 do pedido).
 * Toda a logica aqui e sincrona e deve ser chamada a partir de uma thread ja assincrona
 * (o listener de login e responsavel por isso).
 */
public final class ConfirmationService {

    private final PlayerDao playerDao;
    private final PluginConfig config;

    public ConfirmationService(PlayerDao playerDao, PluginConfig config) {
        this.playerDao = playerDao;
        this.config = config;
    }

    /**
     * @return true se o jogador precisa se autenticar novamente (via /login) antes de poder jogar,
     *         seja por estar em um IP novo, seja por o intervalo de confirmacao ter expirado.
     */
    public ConfirmationRequirement evaluate(PlayerAccount account, String currentIp) throws SQLException {
        if (!account.isRegistered()) {
            // Conta ainda nao registrada - o fluxo normal de /register cuida disso, nao ha o que confirmar.
            return ConfirmationRequirement.none();
        }

        boolean newIpEnabled = config.getBoolean("new-ip.enabled", true);
        boolean newIpRequireConfirmation = config.getBoolean("new-ip.require-confirmation", true);
        if (newIpEnabled && newIpRequireConfirmation) {
            boolean knownIp = playerDao.hasEverConnectedFromIp(account.uuid(), currentIp);
            if (!knownIp) {
                return ConfirmationRequirement.required("new-ip");
            }
        }

        boolean periodicEnabled = config.getBoolean("security.require-confirmation-on-new-ip", true);
        if (periodicEnabled) {
            long intervalMillis = TimeUtil.parse(config.getStringDuration("security.confirmation-interval", "3d"));
            long elapsed = System.currentTimeMillis() - account.lastConfirmationAt();
            if (intervalMillis >= 0 && elapsed >= intervalMillis) {
                return ConfirmationRequirement.required("periodic");
            }
        }

        return ConfirmationRequirement.none();
    }

    public void confirm(UUID uuid) throws SQLException {
        playerDao.updateLastConfirmation(uuid, System.currentTimeMillis());
    }

    public record ConfirmationRequirement(boolean required, String reason) {
        public static ConfirmationRequirement none() { return new ConfirmationRequirement(false, null); }
        public static ConfirmationRequirement required(String reason) { return new ConfirmationRequirement(true, reason); }
    }
}
