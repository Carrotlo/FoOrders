package me.foesio.core.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FoText {
    private static final char SECTION = '§';
    private static final Pattern HEX_PATTERN = Pattern.compile("#([0-9a-fA-F]{6})");
    private static final String COLOR_CHARS = "0123456789abcdefklmnorABCDEFKLMNOR";

    private FoText() {}

    public static String color(String message) {
        if (message == null || message.isEmpty()) {
            return message == null ? "" : message;
        }
        String result = translateHexColors(message);
        result = translateAmpersandCodes(result);
        return result;
    }

    private static String translateHexColors(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length());
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder();
            replacement.append(SECTION).append('x');
            for (char c : hex.toCharArray()) {
                replacement.append(SECTION).append(c);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String translateAmpersandCodes(String message) {
        char[] chars = message.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && COLOR_CHARS.indexOf(chars[i + 1]) > -1) {
                chars[i] = SECTION;
            }
        }
        return new String(chars);
    }
}
