package com.vanatta.helene.supplies.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Logs the state of the application.properties values at startup. The Google Maps and Twilio
 * credentials no longer live here — they are managed in the site_config table via the Site Config
 * page — so they are intentionally absent.
 */
@Configuration
@Slf4j
public class EnvLoggingConfiguration {

  EnvLoggingConfiguration(
      @Value("${jdbi.url}") String url,
      @Value("${jdbi.user}") String user,
      @Value("${distance.calculator.enabled}") boolean distanceCalculatorEnabled,
      @Value("${distance.calculator.delay.ms}") int distanceCalculatorDelayMs,
      @Value("${twilio.sms.enabled}") boolean twilioSmsEnabled) {
    log.info("ENV - JDBI URL: {}", url);
    log.info("ENV - JDBI USER: {}", user);
    log.info("ENV - DISTANCE CALCULATOR ENABLED: {}", distanceCalculatorEnabled);
    log.info("ENV - DISTANCE CALCULATOR DELAY: {}", distanceCalculatorDelayMs);
    log.info("ENV - TWILIO SMS ENABLED: {}", twilioSmsEnabled);
  }
}
