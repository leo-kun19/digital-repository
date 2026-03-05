package com.example.thesisrepo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast connectivity check to provide clearer diagnostics than the default JDBC socket errors.
 * Skipped when RAILWAY_ENVIRONMENT or DATABASE_URL env vars are present (Railway deployment).
 */
public class PostgresPreflightCheck implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(PostgresPreflightCheck.class);
  private static final Pattern JDBC_POSTGRES_PATTERN =
    Pattern.compile("^jdbc:postgresql://(?<host>[^/:?#]+)(?::(?<port>\\d+))?/(?<db>[^?]+).*$");

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    ConfigurableEnvironment environment = applicationContext.getEnvironment();

    // Skip preflight check on Railway or any cloud deployment
    String railwayEnv = System.getenv("RAILWAY_ENVIRONMENT");
    String databaseUrl = System.getenv("DATABASE_URL");
    String dbUrl = System.getenv("DB_URL");
    if (railwayEnv != null || databaseUrl != null || (dbUrl != null && !dbUrl.contains("127.0.0.1"))) {
      log.info("Skipping Postgres preflight check (cloud/Railway deployment detected)");
      return;
    }

    String datasourceUrl = environment.getProperty("spring.datasource.url", "");
    if (!isPostgresDatasource(datasourceUrl)) {
      return;
    }

    Target target = parseTarget(datasourceUrl);

    // Only check local connections
    if (!target.host().equals("127.0.0.1") && !target.host().equals("localhost")) {
      log.info("Skipping Postgres preflight for remote host: {}:{}", target.host(), target.port());
      return;
    }

    if (canConnect(target.host(), target.port(), 1200)) {
      log.info("Postgres preflight passed for {}:{} (database: {})", target.host(), target.port(), target.database());
      return;
    }

    String message = "Postgres is not reachable at %s:%d. Start it with:%n"
      + "  brew services start postgresql@16%n"
      + "or (if your formula differs):%n"
      + "  brew services start postgresql%n"
      + "Then retry.%n"
      + "Check status with:%n"
      + "  brew services list | grep postgres%n"
      + "  pg_isready -h 127.0.0.1 -p 5432";
    String formatted = String.format(message, target.host(), target.port());
    log.error(formatted);
    throw new IllegalStateException(formatted);
  }

  private static boolean isPostgresDatasource(String datasourceUrl) {
    if (datasourceUrl == null) {
      return false;
    }
    return datasourceUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql://");
  }

  private static Target parseTarget(String datasourceUrl) {
    Matcher matcher = JDBC_POSTGRES_PATTERN.matcher(datasourceUrl);
    if (!matcher.matches()) {
      return new Target("127.0.0.1", 5432, "thesis_repo");
    }

    String host = matcher.group("host");
    String portRaw = matcher.group("port");
    String db = matcher.group("db");
    int port = 5432;
    if (portRaw != null && !portRaw.isBlank()) {
      try {
        port = Integer.parseInt(portRaw);
      } catch (NumberFormatException ignored) {
        port = 5432;
      }
    }
    return new Target(host, port, db);
  }

  private static boolean canConnect(String host, int port, int timeoutMs) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), timeoutMs);
      return true;
    } catch (IOException ex) {
      return false;
    }
  }

  private record Target(String host, int port, String database) {}
}
