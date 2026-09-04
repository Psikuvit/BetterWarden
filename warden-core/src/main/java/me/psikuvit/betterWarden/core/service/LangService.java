package me.psikuvit.betterWarden.core.service;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads lang/en.yml (dot-flattened) and resolves {0}, {1}, ... placeholders. No per-locale selection yet - "en" only. */
@Service
public class LangService {

    private final Map<String, String> messages;

    public LangService() {
        this.messages = load();
    }

    public String get(String key, Object... args) {
        String template = messages.getOrDefault(key, key);
        for (int i = 0; i < args.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return template;
    }

    private Map<String, String> load() {
        try (InputStream in = getClass().getResourceAsStream("/lang/en.yml")) {
            if (in == null) {
                return Map.of();
            }
            Object root = new Yaml().load(in);
            Map<String, String> flat = new LinkedHashMap<>();
            flatten(flat, "", root);
            return flat;
        } catch (Exception e) {
            return Map.of();
        }
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
