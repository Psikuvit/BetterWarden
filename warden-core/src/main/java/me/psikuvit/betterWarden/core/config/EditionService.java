package me.psikuvit.betterWarden.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Properties;

/**
 * docs/spec/08-TIERS-AND-LICENSING.txt - which edition this jar was built as. Read from
 * edition.properties, baked in at build time via Maven resource filtering (root pom.xml's `free`
 * profile) - deliberately NOT read from config.yml, so a free-edition user editing config.yml
 * cannot unlock paid features. Every paid subsystem checks isPaid()/isFree() before activating;
 * see the spec doc for the full list of gate points.
 */
@Service
public class EditionService {

    private static final Logger log = LoggerFactory.getLogger(EditionService.class);

    private final boolean paid;

    public EditionService() {
        this.paid = !"free".equalsIgnoreCase(load());
        if (paid) {
            log.info("Running the Standard/Network edition.");
        } else {
            log.info("Running the FREE edition - punishments only, single server. "
                    + "See betterwarden.dev for Standard/Network (panel, Discord bot, reports/tickets/appeals, "
                    + "chat filter, alt detection, templates & escalation, staff notes, MySQL).");
        }
    }

    public boolean isPaid() {
        return paid;
    }

    public boolean isFree() {
        return !paid;
    }

    private static String load() {
        try (InputStream in = EditionService.class.getResourceAsStream("/edition.properties")) {
            if (in == null) {
                return "paid";
            }
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("edition", "paid");
        } catch (Exception e) {
            // Fail toward the fuller edition rather than silently downgrading a paid buyer's install.
            return "paid";
        }
    }
}
