package jease.site;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/** For preventing XSS */
public class HtmlSanitizer {

    private HtmlSanitizer() {}

    private static final PolicyFactory sanitizer = Sanitizers.FORMATTING.and(Sanitizers.BLOCKS)
            .and(Sanitizers.IMAGES).and(Sanitizers.LINKS)
            .and(Sanitizers.STYLES).and(Sanitizers.TABLES);

    public static String sanitize(String html) {
        String v = sanitizer.sanitize(html);
        return v;
    }

}
