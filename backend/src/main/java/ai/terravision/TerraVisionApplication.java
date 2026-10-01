package ai.terravision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

// UserDetailsServiceAutoConfiguration is excluded because this app never uses
// Spring Security's username/password model -- JwtAuthFilter populates the
// SecurityContext directly. Without this exclusion, Spring Boot still auto-creates
// an unused in-memory "user" with a randomly generated password on every startup
// (logged at INFO level), which is dead weight and misleading about which auth
// mechanism is actually protecting the API.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableCaching
public class TerraVisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(TerraVisionApplication.class, args);
    }
}
