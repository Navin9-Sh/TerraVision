package ai.terravision.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lets the database be configured with a single provider-style connection string
 * ({@code DATABASE_URL=postgresql://user:pass@host/db?sslmode=require}), and rescues a
 * {@code spring.datasource.url} that was pasted in that form instead of as a JDBC URL.
 * Runs before the DataSource is created, so nothing else needs to know.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = firstNonBlank(environment.getProperty("DATABASE_URL"), environment.getProperty("spring.datasource.url"));
        Optional<DatabaseUrlConverter.Converted> converted = DatabaseUrlConverter.convert(raw);
        if (converted.isEmpty()) {
            return;
        }

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("spring.datasource.url", converted.get().jdbcUrl());
        if (converted.get().username() != null) {
            overrides.put("spring.datasource.username", converted.get().username());
        }
        if (converted.get().password() != null) {
            overrides.put("spring.datasource.password", converted.get().password());
        }
        environment.getPropertySources().addFirst(new MapPropertySource("terravisionDatabaseUrl", overrides));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
