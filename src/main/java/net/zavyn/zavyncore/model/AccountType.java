package net.zavyn.zavyncore.model;

/**
 * Como o jogador foi autenticado ao conectar no proxy.
 */
public enum AccountType {
    PREMIUM,   // autenticado pela Mojang/Microsoft (OnlineMode do Velocity = true)
    FLOODGATE, // conectado via Geyser/Floodgate (Bedrock)
    OFFLINE    // conta pirata/offline-mode
}
