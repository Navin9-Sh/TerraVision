package ai.terravision.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlConverterTest {

    @Test
    void convertsAProviderStyleUrl() {
        var result = DatabaseUrlConverter.convert(
                "postgresql://neondb_owner:s3cret@ep-cool-123456.us-east-2.aws.neon.tech/neondb?sslmode=require").orElseThrow();

        assertThat(result.jdbcUrl()).isEqualTo(
                "jdbc:postgresql://ep-cool-123456.us-east-2.aws.neon.tech:5432/neondb?sslmode=require");
        assertThat(result.username()).isEqualTo("neondb_owner");
        assertThat(result.password()).isEqualTo("s3cret");
    }

    @Test
    void acceptsThePostgresSchemeAndAnExplicitPort() {
        var result = DatabaseUrlConverter.convert("postgres://app:pw@db.example.com:6543/mydb").orElseThrow();

        assertThat(result.jdbcUrl()).isEqualTo("jdbc:postgresql://db.example.com:6543/mydb");
        assertThat(result.username()).isEqualTo("app");
    }

    @Test
    void toleratesAPastedPsqlCommandWithQuotes() {
        var result = DatabaseUrlConverter.convert(
                "psql 'postgresql://neondb_owner:abc123@ep-x-1.neon.tech/neondb?sslmode=require&channel_binding=require'").orElseThrow();

        assertThat(result.jdbcUrl()).isEqualTo(
                "jdbc:postgresql://ep-x-1.neon.tech:5432/neondb?sslmode=require&channel_binding=require");
        assertThat(result.password()).isEqualTo("abc123");
    }

    @Test
    void decodesPercentEncodedCredentialsButKeepsALiteralPlus() {
        var result = DatabaseUrlConverter.convert("postgresql://us%40er:p%40ss+word@host.example.com/db").orElseThrow();

        assertThat(result.username()).isEqualTo("us@er");
        assertThat(result.password()).isEqualTo("p@ss+word");
    }

    @Test
    void leavesJdbcUrlsAndNonsenseAlone() {
        assertThat(DatabaseUrlConverter.convert("jdbc:postgresql://host:5432/db")).isEmpty();
        assertThat(DatabaseUrlConverter.convert("not a url")).isEmpty();
        assertThat(DatabaseUrlConverter.convert("")).isEmpty();
        assertThat(DatabaseUrlConverter.convert(null)).isEmpty();
    }

    @Test
    void urlWithoutCredentialsStillConverts() {
        var result = DatabaseUrlConverter.convert("postgresql://host.example.com/db").orElseThrow();

        assertThat(result.jdbcUrl()).isEqualTo("jdbc:postgresql://host.example.com:5432/db");
        assertThat(result.username()).isNull();
        assertThat(result.password()).isNull();
    }
}
