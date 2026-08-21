package net.zavyn.zavyncore.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte strings de duracao (30m, 2h, 7d, 30d, 1mo ...) em milissegundos
 * e formata milissegundos de volta em texto legivel.
 */
public final class TimeUtil {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)(mo|s|m|h|d|w)");

    private TimeUtil() {
    }

    /**
     * @return duracao em milissegundos, ou -1 se a string representar "permanente"
     *         (perm, permanent, permanente, forever, -1).
     * @throws IllegalArgumentException se o formato for invalido.
     */
    public static long parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Duracao vazia");
        }
        String normalized = input.trim().toLowerCase();
        if (normalized.equals("perm") || normalized.equals("permanent")
                || normalized.equals("permanente") || normalized.equals("forever")
                || normalized.equals("-1")) {
            return -1L;
        }

        Matcher matcher = TOKEN.matcher(normalized);
        long totalMillis = 0L;
        boolean matchedAny = false;
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() != lastEnd) {
                throw new IllegalArgumentException("Formato de duracao invalido: " + input);
            }
            matchedAny = true;
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            totalMillis += amount * unitToMillis(unit);
            lastEnd = matcher.end();
        }

        if (!matchedAny || lastEnd != normalized.length()) {
            throw new IllegalArgumentException("Formato de duracao invalido: " + input);
        }

        return totalMillis;
    }

    private static long unitToMillis(String unit) {
        return switch (unit) {
            case "s" -> 1_000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> 86_400_000L;
            case "w" -> 604_800_000L;
            case "mo" -> 2_592_000_000L; // 30 dias
            default -> throw new IllegalArgumentException("Unidade desconhecida: " + unit);
        };
    }

    public static String formatRemaining(long millis) {
        if (millis < 0) {
            return "permanente";
        }
        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.isEmpty()) sb.append(seconds).append("s");
        return sb.toString().trim();
    }
}
