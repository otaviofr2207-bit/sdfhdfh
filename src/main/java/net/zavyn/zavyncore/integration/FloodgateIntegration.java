package net.zavyn.zavyncore.integration;

import org.geysermc.floodgate.api.FloodgateApi;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Integracao opcional (soft-depend) com o Floodgate.
 * Se o Floodgate nao estiver presente no proxy, isPresent() retorna false
 * e nenhuma classe do Floodgate e carregada em tempo de uso, apenas o try/catch
 * de inicializacao toca a API - por isso a dependencia e "provided" no pom.xml
 * e o jar final NAO deve ser executado sem o Floodgate se voce usar Bedrock;
 * caso contrario esses metodos simplesmente retornam "nao e floodgate".
 */
public final class FloodgateIntegration {

    private final boolean present;
    private final Logger logger;

    public FloodgateIntegration(Logger logger) {
        this.logger = logger;
        boolean detected;
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            detected = FloodgateApi.getInstance() != null;
        } catch (Throwable t) {
            detected = false;
        }
        this.present = detected;

        if (present) {
            logger.info("[ZavynCore] Floodgate detectado - recursos de deteccao Bedrock ativados.");
        } else {
            logger.warn("[ZavynCore] Floodgate NAO detectado - jogadores Bedrock serao tratados de acordo com o "
                    + "modo online do proxy (premium ou offline). Instale o Floodgate para deteccao correta.");
        }
    }

    public boolean isPresent() {
        return present;
    }

    /**
     * @return true se o UUID pertence a um jogador conectado via Floodgate (Bedrock).
     *         Sempre retorna false se o Floodgate nao estiver presente.
     */
    public boolean isFloodgatePlayer(UUID uuid) {
        if (!present) return false;
        try {
            return FloodgateApi.getInstance().isFloodgateId(uuid);
        } catch (Throwable t) {
            logger.warn("[ZavynCore] Erro ao consultar Floodgate para {}: {}", uuid, t.getMessage());
            return false;
        }
    }
}
