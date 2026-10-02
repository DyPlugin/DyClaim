package dev.dyclaim.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure rule calculations shared by commands and automatic acquisition. */
public final class Rules {
    private static final Pattern DURATION = Pattern.compile("([1-9][0-9]*)(m|h|d)");
    private Rules() {}

    public static long expiry(String text, long now, long maximumMillis, boolean permanentAllowed) {
        if (permanentAllowed && "permanent".equalsIgnoreCase(text)) return 0;
        Matcher match = DURATION.matcher(text.toLowerCase(Locale.ROOT));
        if (!match.matches()) throw new IllegalArgumentException("Use 30m, 2h, 7d or permanent");
        try {
            long multiplier = switch (match.group(2)) { case "m" -> 60_000L; case "h" -> 3_600_000L; default -> 86_400_000L; };
            long duration = Math.multiplyExact(Long.parseLong(match.group(1)), multiplier);
            if (duration > maximumMillis) throw new IllegalArgumentException("Duration exceeds limit");
            return Math.addExact(now, duration);
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("Duration overflow", ex);
        }
    }

    public static boolean spacing(int x, int z, int otherX, int otherZ, int empty) {
        return Math.max(Math.abs((long) x - otherX), Math.abs((long) z - otherZ)) - 1 >= empty;
    }

    public static double money(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid money amount");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static double marketPrice(String text, double minimum, double maximum) {
        try {
            BigDecimal price = new BigDecimal(text);
            if (price.signum() <= 0 || price.stripTrailingZeros().scale() > 2
                    || price.compareTo(BigDecimal.valueOf(minimum)) < 0 || price.compareTo(BigDecimal.valueOf(maximum)) > 0)
                throw new IllegalArgumentException("Price outside limits or precision");
            return price.doubleValue();
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid price", ex); }
    }
}
