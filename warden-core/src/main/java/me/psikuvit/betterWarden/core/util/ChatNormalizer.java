package me.psikuvit.betterWarden.core.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Collapses a chat message down to bare a-z0-9 so blocked-word matching survives the usual
 * evasion tricks: spacing ("f u c k"), punctuation ("f.u.c.k"), leetspeak ("fu(k", "5hit"),
 * unicode homoglyphs (Cyrillic а/е/о/р/с/х that render identically to Latin), and combining-mark
 * zalgo text. Deliberately collapses word boundaries too - a blocked phrase like "buy gold" is
 * stored and matched the same way ("buygold"), so this only ever needs one form, not two.
 */
public final class ChatNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");

    // Cyrillic/Greek letters that render identically or near-identically to Latin ones in most
    // Minecraft fonts - not exhaustive, just the common troll substitutions actually seen in the wild.
    private static final Map<Character, Character> HOMOGLYPHS = Map.ofEntries(
            Map.entry('а', 'a'), Map.entry('е', 'e'), Map.entry('о', 'o'), Map.entry('р', 'p'),
            Map.entry('с', 'c'), Map.entry('х', 'x'), Map.entry('у', 'y'), Map.entry('і', 'i'),
            Map.entry('ѕ', 's'), Map.entry('т', 't'), Map.entry('к', 'k'), Map.entry('м', 'm'),
            Map.entry('н', 'h'), Map.entry('в', 'b'), Map.entry('α', 'a'), Map.entry('ο', 'o'),
            Map.entry('ρ', 'p'), Map.entry('ε', 'e'));

    private static final Map<Character, Character> LEETSPEAK = Map.of(
            '0', 'o', '1', 'i', '3', 'e', '4', 'a', '5', 's', '7', 't', '$', 's', '@', 'a');

    private ChatNormalizer() {
    }

    public static String normalize(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        // NFKD splits accented letters into base + combining mark, and folds full/halfwidth
        // and other compatibility variants down to their plain ASCII-ish equivalents.
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFKD);
        String noMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");

        StringBuilder substituted = new StringBuilder(noMarks.length());
        for (int i = 0; i < noMarks.length(); i++) {
            char c = noMarks.charAt(i);
            c = HOMOGLYPHS.getOrDefault(c, c);
            c = LEETSPEAK.getOrDefault(c, c);
            substituted.append(c);
        }

        return NON_ALPHANUMERIC.matcher(substituted).replaceAll("");
    }
}
