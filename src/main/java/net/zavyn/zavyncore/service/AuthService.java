package net.zavyn.zavyncore.service;

import net.zavyn.zavyncore.database.dao.LogDao;
import net.zavyn.zavyncore.database.dao.PlayerDao;
import net.zavyn.zavyncore.model.PlayerAccount;
import net.zavyn.zavyncore.util.PasswordUtil;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.Map;

/**
 * Sistema de login/register para contas offline. Senhas nunca sao mantidas em texto puro em
 * nenhum momento alem do array de char passado pelo comando, que e sempre "wiped" apos o hash.
 */
public final class AuthService {

    private final PlayerDao playerDao;
    private final LogDao logDao;
    private final Executor executor;

    // Estado de sessao "logado" em memoria (evita ir ao banco a cada pacote/chat).
    private final Map<UUID, Boolean> loggedInCache = new ConcurrentHashMap<>();

    public AuthService(PlayerDao playerDao, LogDao logDao, Executor executor) {
        this.playerDao = playerDao;
        this.logDao = logDao;
        this.executor = executor;
    }

    public boolean isLoggedIn(UUID uuid) {
        return loggedInCache.getOrDefault(uuid, false);
    }

    public void markLoggedOutInMemory(UUID uuid) {
        loggedInCache.remove(uuid);
    }

    public CompletableFuture<AuthResult> register(UUID uuid, char[] password, char[] confirmPassword) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<PlayerAccount> account = playerDao.find(uuid);
                if (account.isEmpty()) return AuthResult.failure("Conta nao encontrada.");
                if (account.get().isRegistered()) return AuthResult.failure("Voce ja possui uma conta registrada.");
                if (!java.util.Arrays.equals(password, confirmPassword)) {
                    return AuthResult.failure("As senhas nao coincidem.");
                }
                if (password.length < 6) {
                    return AuthResult.failure("A senha deve ter pelo menos 6 caracteres.");
                }

                String hash = PasswordUtil.hash(password);
                playerDao.setPasswordHash(uuid, hash);
                playerDao.updateLastConfirmation(uuid, System.currentTimeMillis());
                playerDao.setLoggedIn(uuid, true);
                loggedInCache.put(uuid, true);
                logDao.log(account.get().name(), "REGISTER", account.get().name(), null);
                return AuthResult.success();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                java.util.Arrays.fill(confirmPassword, '\0');
            }
        }, executor);
    }

    public CompletableFuture<AuthResult> login(UUID uuid, char[] password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<PlayerAccount> account = playerDao.find(uuid);
                if (account.isEmpty() || !account.get().isRegistered()) {
                    return AuthResult.failure("Voce ainda nao possui uma conta registrada. Use /register.");
                }
                boolean valid = PasswordUtil.verify(account.get().passwordHash(), password);
                if (!valid) return AuthResult.failure("Senha incorreta.");

                playerDao.setLoggedIn(uuid, true);
                playerDao.updateLastConfirmation(uuid, System.currentTimeMillis());
                loggedInCache.put(uuid, true);
                logDao.log(account.get().name(), "LOGIN", account.get().name(), null);
                return AuthResult.success();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<AuthResult> changePassword(UUID uuid, char[] oldPassword, char[] newPassword) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<PlayerAccount> account = playerDao.find(uuid);
                if (account.isEmpty() || !account.get().isRegistered()) {
                    return AuthResult.failure("Voce nao possui uma conta registrada.");
                }
                if (!PasswordUtil.verify(account.get().passwordHash(), oldPassword)) {
                    return AuthResult.failure("Senha atual incorreta.");
                }
                if (newPassword.length < 6) {
                    return AuthResult.failure("A nova senha deve ter pelo menos 6 caracteres.");
                }
                String hash = PasswordUtil.hash(newPassword);
                playerDao.setPasswordHash(uuid, hash);
                logDao.log(account.get().name(), "CHANGEPASSWORD", account.get().name(), null);
                return AuthResult.success();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /** Define/reseta a senha de um jogador via comando administrativo. Nunca expoe a senha antiga. */
    public CompletableFuture<AuthResult> adminSetPassword(UUID targetUuid, String targetName, char[] newPassword, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (newPassword.length < 6) {
                    return AuthResult.failure("A nova senha deve ter pelo menos 6 caracteres.");
                }
                String hash = PasswordUtil.hash(newPassword);
                playerDao.setPasswordHash(targetUuid, hash);
                playerDao.setLoggedIn(targetUuid, false);
                loggedInCache.remove(targetUuid);
                logDao.log(staffName, "SETPASSWORD", targetName, null);
                return AuthResult.success();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<AuthResult> unregister(UUID uuid, char[] password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<PlayerAccount> account = playerDao.find(uuid);
                if (account.isEmpty() || !account.get().isRegistered()) {
                    return AuthResult.failure("Voce nao possui uma conta registrada.");
                }
                if (!PasswordUtil.verify(account.get().passwordHash(), password)) {
                    return AuthResult.failure("Senha incorreta.");
                }
                playerDao.setPasswordHash(uuid, null);
                playerDao.setLoggedIn(uuid, false);
                loggedInCache.remove(uuid);
                logDao.log(account.get().name(), "UNREGISTER", account.get().name(), null);
                return AuthResult.success();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public void logout(UUID uuid, String name) {
        loggedInCache.remove(uuid);
        try {
            playerDao.setLoggedIn(uuid, false);
        } catch (SQLException e) {
            logDao.log(name, "LOGOUT_ERROR", name, e.getMessage());
        }
    }

    public record AuthResult(boolean success, String errorMessage) {
        public static AuthResult success() { return new AuthResult(true, null); }
        public static AuthResult failure(String message) { return new AuthResult(false, message); }
    }
}
