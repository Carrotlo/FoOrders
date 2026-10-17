package me.foesio.foOrders;

import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

final class PublicNumberFormat {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final String localeTag;
    private final String plainPattern;
    private final String compactPattern;
    private final List<String> suffixes;
    private final BigDecimal roundingThreshold;

    private PublicNumberFormat(String localeTag, String plainPattern, String compactPattern, List<String> suffixes) {
        this.localeTag = localeTag;
        this.plainPattern = plainPattern;
        this.compactPattern = compactPattern;
        this.suffixes = suffixes;
        DecimalFormat formatter = new DecimalFormat(compactPattern,
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag(localeTag)));
        BigDecimal halfLastDigit = BigDecimal.ONE.movePointLeft(formatter.getMaximumFractionDigits())
            .divide(BigDecimal.valueOf(2));
        this.roundingThreshold = THOUSAND.subtract(halfLastDigit);
    }

    static PublicNumberFormat load(FileConfiguration config) {
        if (firstInvalidPath(config) != null) return null;
        String tag = config.getString("numbers.locale", "");
        String plain = config.getString("numbers.plain-pattern", "");
        String compact = config.getString("numbers.compact-pattern", "");
        List<String> suffixes = config.getStringList("numbers.compact-suffixes");
        return new PublicNumberFormat(tag, plain, compact, List.copyOf(suffixes));
    }

    static String firstInvalidPath(FileConfiguration config) {
        if (config.isSet("numbers") && !config.isConfigurationSection("numbers")) return "numbers";
        Object tag = config.get("numbers.locale");
        if (!(tag instanceof String language) || language.isBlank()) return "numbers.locale";
        try {
            new Locale.Builder().setLanguageTag(language).build();
        } catch (java.util.IllformedLocaleException invalid) {
            return "numbers.locale";
        }
        Locale locale = Locale.forLanguageTag(language);
        if (locale.getLanguage().isBlank()) return "numbers.locale";
        Object plain = config.get("numbers.plain-pattern");
        if (!validPattern(plain, locale)) return "numbers.plain-pattern";
        Object compact = config.get("numbers.compact-pattern");
        if (!validPattern(compact, locale)) return "numbers.compact-pattern";
        Object suffixes = config.get("numbers.compact-suffixes");
        if (!(suffixes instanceof List<?> list) || list.isEmpty()) return "numbers.compact-suffixes";
        for (int index = 0; index < list.size(); index++) {
            if (!(list.get(index) instanceof String)) return "numbers.compact-suffixes[" + index + "]";
        }
        return null;
    }

    private static boolean validPattern(Object value, Locale locale) {
        if (!(value instanceof String pattern) || pattern.isBlank()) return false;
        try {
            new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale));
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    String plain(double value) {
        return Double.isFinite(value) ? format(value, plainPattern) : "";
    }

    String compact(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        BigDecimal absolute = BigDecimal.valueOf(value).abs();
        if (absolute.compareTo(THOUSAND) < 0) {
            return plain(value);
        }
        int suffixIndex = -1;
        BigDecimal scaled = absolute;
        while (scaled.compareTo(THOUSAND) >= 0 && suffixIndex < suffixes.size() - 1) {
            scaled = scaled.divide(THOUSAND);
            suffixIndex++;
        }
        if (suffixIndex < suffixes.size() - 1 && scaled.compareTo(roundingThreshold) >= 0) {
            scaled = scaled.divide(THOUSAND);
            suffixIndex++;
        }
        double signedScaled = value < 0 ? -scaled.doubleValue() : scaled.doubleValue();
        return format(signedScaled, compactPattern) + suffixes.get(suffixIndex);
    }

    private String format(double value, String pattern) {
        DecimalFormat formatter = new DecimalFormat(pattern,
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag(localeTag)));
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        return formatter.format(value);
    }

}
