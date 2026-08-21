package net.zavyn.zavyncore.util;

import java.security.SecureRandom;

/**
 * Gera identificadores curtos e legiveis para punicoes (ex: BAN-7F3K9Q).
 */
public final class IdGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sem O/0/I/1
    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    public static String punishmentId(String typePrefix) {
        StringBuilder sb = new StringBuilder(typePrefix).append('-');
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
