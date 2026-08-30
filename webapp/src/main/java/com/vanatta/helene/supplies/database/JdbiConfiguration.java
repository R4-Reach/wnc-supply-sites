package com.vanatta.helene.supplies.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Slf4JSqlLogger;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class JdbiConfiguration {

  // Exposed as its own bean so Spring closes the pool on context shutdown (AutoCloseable). Under
  // `just up`, DevTools restarts the context on every recompile; without this the old pool's
  // connections leak on each restart until Postgres runs out of slots.
  @Bean
  public HikariDataSource dataSource(
      @Value("${jdbi.url}") String url,
      @Value("${jdbi.user}") String user,
      @Value("${jdbi.password}") String password) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(url);
    config.setUsername(user);
    config.setPassword(password);

    // config values docs:
    // https://github.com/brettwooldridge/HikariCP?tab=readme-ov-file#gear-configuration-knobs-baby

    // timeout DB connections at 10s instead of default of 30s
    config.setConnectionTimeout(10_000);

    // Keep pool size small (default is 10). Target env is a single core linode.
    // https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
    config.setMaximumPoolSize(4);
    return new HikariDataSource(config);
  }

  @Bean
  public Jdbi jdbi(HikariDataSource dataSource) {
    var jdbi = Jdbi.create(dataSource).installPlugin(new SqlObjectPlugin());
    jdbi.setSqlLogger(new Slf4JSqlLogger());
    return jdbi;
  }
}
