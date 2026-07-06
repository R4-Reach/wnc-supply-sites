package com.vanatta.helene.supplies.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** This config class just logs out the state of all application.properties values. */
@Configuration
@Slf4j
public class EnvLoggingConfiguration {

  EnvLoggingConfiguration(
      @Value("${jdbi.url}") String url,
      @Value("${jdbi.user}") String user,
      @Value("${make.enabled}") boolean makeEnabled,
      @Value("${distance.calculator.enabled}") boolean distanceCalculatorEnabled,
      @Value("${distance.calculator.delay.ms}") int distanceCalculatorDelayMs,
      @Value("${google.maps.api.key}") String googleApiKey,
      @Value("${twilio.from.number}") String twilioFromNumber,
      @Value("${twilio.account.sid}") String twilioAccountSid,
      @Value("${twilio.sms.enabled}") boolean twilioSmsEnabled) {
    log.info("ENV - JDBI URL: {}", url);
    log.info("ENV - JDBI USER: {}", user);
    log.info("ENV - MAKE ENABLED: {}", makeEnabled);
    log.info("ENV - DISTANCE CALCULATOR ENABLED: {}", distanceCalculatorEnabled);
    log.info("ENV - DISTANCE CALCULATOR DELAY: {}", distanceCalculatorDelayMs);
    log.info("ENV - GOOGLE API KEY: {}", googleApiKey);
    log.info("ENV - TWILIO FROM NUMBER: {}", twilioFromNumber);
    log.info("ENV - TWILIO ACCOUNT SID: {}", twilioAccountSid);
    log.info("ENV - TWILIO SMS ENABLED: {}", twilioSmsEnabled);
  }
}
