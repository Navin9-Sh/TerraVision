package ai.terravision.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the connection string hosting providers hand out (Neon, Render, Railway, Heroku:
 * {@code postgresql://user:password@host:5432/db?sslmode=require}) into the three values
 * Spring needs: a JDBC URL, a username and a password. It also tolerates what people
 * actually paste: a {@code psql '...'} command, surrounding quotes, or the {@code postgres://}
 * spelling.
 */
public final class DatabaseUrlConverter {

    public record Converted(String jdbcUrl, String username, String password) {
    }

    private static final Pattern POSTGRES_URL = Pattern.compile("postgres(?:ql)?://[^\\s'\"]+");

    private DatabaseUrlConverter() {
    }

    /** Empty when the text isn't a postgres:// style URL (e.g. it's already a jdbc: URL). */
    public static Optional<Converted> convert(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().startsWith("jdbc:")) {
            return Optional.empty();
        }
        Matcher match = POSTGRES_URL.matcher(raw);
        if (!match.find()) {
            return Optional.empty();
        }

        URI uri;
        try {
            uri = URI.create(match.group());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (uri.getHost() == null) {
            return Optional.empty();
        }

        String username = null;
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            username = decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
            password = colon < 0 ? null : decode(userInfo.substring(colon + 1));
        }

        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String database = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + database + query;

        return Optional.of(new Converted(jdbcUrl, username, password));
    }

    private static String decode(String value) {
        // Percent-decode only: a literal '+' in a password must stay a '+'.
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
