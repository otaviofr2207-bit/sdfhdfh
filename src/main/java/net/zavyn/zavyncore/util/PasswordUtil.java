package net.zavyn.zavyncore.util;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

/**
 * Wrapper para hashing de senhas com Argon2id.
 * A senha em texto puro NUNCA deve ser logada, persistida ou retornada.
 */
public final class PasswordUtil {

    private static final Argon2 ARGON2 = Argon2Factory.create(
            Argon2Factory.Argon2Types.ARGON2id, 16, 32);

    private static final int ITERATIONS = 3;
    private static final int MEMORY_KB = 65536; // 64 MB
    private static final int PARALLELISM = 1;

    private PasswordUtil() {
    }

    public static String hash(char[] plainPassword) {
        try {
            return ARGON2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, plainPassword);
        } finally {
            ARGON2.wipeArray(plainPassword);
        }
    }

    public static boolean verify(String hash, char[] plainPassword) {
        try {
            return ARGON2.verify(hash, plainPassword);
        } finally {
            ARGON2.wipeArray(plainPassword);
        }
    }
}
