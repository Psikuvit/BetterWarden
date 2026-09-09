package me.psikuvit.betterWarden.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads lang/&lt;code&gt;.yml (dot-flattened) and resolves {0}, {1}, ... placeholders.
 * docs/spec - locale is warden.language in config.yml (default "en"), read as a system property
 * since this is constructed both as a Spring bean (HOST mode) and as a plain `new LangService()`
 * (CLIENT mode has no Spring context) - ConfigBootstrap.applyToSystemProperties() runs before
 * either, in every boot path, so the property is always set by the time this reads it.
 *
 * Three layers, each filling gaps the one before left, so a message key is never missing:
 * 1. Bundled English (/lang/en.yml on the classpath) - the base, always loaded.
 * 2. The chosen locale's bundled file (/lang/&lt;code&gt;.yml), if BetterWarden ships one (en, fr, de).
 * 3. A user-provided override at <data folder>/lang/&lt;code&gt;.yml on disk - lets a server owner
 *    fix a translation, or add an entirely new language BetterWarden doesn't ship (e.g. "es"),
 *    with no code changes needed on their end - just the file and warden.language: es.
 */
@Service
public class LangService {

    private static final Logger log = LoggerFactory.getLogger(LangService.class);

    /** Locales shipped in the jar - see warden-core/src/main/resources/lang/. */
    private static final List<String> BUNDLED_LOCALES = List.of("en", "fr", "de");

    private final Map<String, String> messages;

    public LangService() {
        String locale = System.getProperty("warden.language", "en").trim().toLowerCase(Locale.ROOT);

        Map<String, String> resolved = new LinkedHashMap<>(loadBundled("en"));
        if ("en".equals(locale)) {
            log.info("Language: en (bundled, {} messages)", resolved.size());
        } else {
            Map<String, String> bundled = BUNDLED_LOCALES.contains(locale) ? loadBundled(locale) : Map.of();
            Map<String, String> external = loadExternal(locale);
            resolved.putAll(bundled);
            resolved.putAll(external);
            if (bundled.isEmpty() && external.isEmpty()) {
                log.warn("warden.language '{}' not found (bundled: {}) and no lang/{}.yml in the data "
                                + "folder either - falling back to English. Drop a lang/{}.yml file there to add it.",
                        locale, BUNDLED_LOCALES, locale, locale);
            } else {
                log.info("Language: {} ({} bundled + {} data-folder override message(s))",
                        locale, bundled.size(), external.size());
            }
        }
        this.messages = resolved;
    }

    public String get(String key, Object... args) {
        String template = messages.getOrDefault(key, key);
        for (int i = 0; i < args.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return template;
    }

    /** From the jar's own /lang/ resources - en, fr, de. Empty map for anything else (a locale BetterWarden doesn't bundle). */
    private Map<String, String> loadBundled(String locale) {
        try (InputStream in = getClass().getResourceAsStream("/lang/" + locale + ".yml")) {
            if (in == null) {
                return Map.of();
            }
            return flattenYaml(in);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** A server owner's own lang/&lt;code&gt;.yml on disk, next to config.yml - absent unless they've added one. */
    private Map<String, String> loadExternal(String locale) {
        String dataFolder = System.getProperty("warden.dataFolder");
        if (dataFolder == null || dataFolder.isBlank()) {
            return Map.of();
        }
        File file = new File(new File(dataFolder, "lang"), locale + ".yml");
        if (!file.isFile()) {
            return Map.of();
        }
        try (InputStream in = new FileInputStream(file)) {
            return flattenYaml(in);
        } catch (Exception e) {
            log.warn("Could not read {}: {}", file, e.getMessage());
            return Map.of();
        }
    }

    private Map<String, String> flattenYaml(InputStream in) {
        Object root = new Yaml().load(in);
        Map<String, String> flat = new LinkedHashMap<>();
        flatten(flat, "", root);
        return flat;
    }

    private void flatten(Map<String, String> out, String prefix, Object node) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
                flatten(out, key, entry.getValue());
            }
        } else if (node != null) {
            out.put(prefix, String.valueOf(node));
        }
    }
}
