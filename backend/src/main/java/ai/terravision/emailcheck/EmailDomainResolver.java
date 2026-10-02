package ai.terravision.emailcheck;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.time.Duration;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmailDomainResolver {

    public enum Result { DELIVERABLE, NO_SUCH_DOMAIN, NO_MAIL_SERVER, UNKNOWN }

    private static final Logger log = LoggerFactory.getLogger(EmailDomainResolver.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_CACHE_ENTRIES = 5000;

    private record Cached(Result result, long expiresAtMillis) {
    }

    private final EmailValidationProperties properties;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public EmailDomainResolver(EmailValidationProperties properties) {
        this.properties = properties;
    }

    public boolean isDnsCheckEnabled() {
        return properties.dnsCheck();
    }

    public Result check(String domain) {
        String key = domain.toLowerCase();
        long now = System.currentTimeMillis();

        Cached cached = cache.get(key);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.result();
        }

        Result result = lookup(key);
        if (result != Result.UNKNOWN) {
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(key, new Cached(result, now + CACHE_TTL.toMillis()));
        }
        return result;
    }

    private Result lookup(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "1500");
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        DirContext context = null;
        try {
            context = new InitialDirContext(env);

            Attribute mx = context.getAttributes(domain, new String[]{"MX"}).get("MX");
            if (mx != null && mx.size() > 0) {
                return hasUsableMailServer(mx) ? Result.DELIVERABLE : Result.NO_MAIL_SERVER;
            }
            Attributes addresses = context.getAttributes(domain, new String[]{"A", "AAAA"});
            if (addresses.get("A") != null || addresses.get("AAAA") != null) {
                return Result.DELIVERABLE;
            }
            return Result.NO_MAIL_SERVER;
        } catch (NameNotFoundException e) {
            return Result.NO_SUCH_DOMAIN;
        } catch (NamingException e) {
            log.warn("DNS lookup for email domain '{}' failed ({}); allowing the address", domain, e.toString());
            return Result.UNKNOWN;
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }

    private boolean hasUsableMailServer(Attribute mx) throws NamingException {
        NamingEnumeration<?> records = mx.getAll();
        while (records.hasMore()) {
            String record = String.valueOf(records.next()).trim();
            if (!record.endsWith(" .") && !record.equals(".")) {
                return true;
            }
        }
        return false;
    }
}
