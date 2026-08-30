package org.r4reach.auth.setup.password.send.access.code;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenGenerator {
  private final SecureRandom secureRandom = new SecureRandom();

  public String generate() {
    String accessCode = String.valueOf(secureRandom.nextInt(1_000_000));

    if (accessCode.length() < 6) {
      accessCode = "0".repeat(6 - accessCode.length()) + accessCode;
    }
    return accessCode;
  }
}
