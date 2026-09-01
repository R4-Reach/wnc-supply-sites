package org.r4reach.twilio.sms;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.siteconfig.SiteConfigKey;
import org.r4reach.siteconfig.SiteConfigService;
import org.r4reach.util.PhoneNumberUtil;
import org.r4reach.util.TruncateString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SmsSender {

  // @VisibleForTesting
  public static SmsSender newDisabled(Jdbi jdbi) {
    return new SmsSender(false, null, jdbi);
  }

  private final boolean twilioSmsEnabled;
  private final SiteConfigService siteConfigService;
  private final Jdbi jdbi;

  SmsSender(
      @Value("${twilio.sms.enabled}") boolean twilioSmsEnabled,
      SiteConfigService siteConfigService,
      Jdbi jdbi) {
    this.twilioSmsEnabled = twilioSmsEnabled;
    this.siteConfigService = siteConfigService;
    this.jdbi = jdbi;
  }

  public boolean send(String phoneNumber, String message) {
    if (phoneNumber == null || message == null) {
      throw new IllegalArgumentException(
          String.format(
              "Null input, phoneNumber present: %s, message present: %s",
              phoneNumber != null, message != null));
    }

    if (!twilioSmsEnabled) {
      // Never log the message body — it carries the one-time passcode — or the recipient phone.
      log.info("SMS disabled, skipping send (message length: {})", message.length());
      recordMessage(
          jdbi,
          MessageResult.builder()
              .toNumber(phoneNumber)
              .messageLength(message.length())
              .messageLink("/fake/uri.json")
              .success(false)
              .errorCode(-1)
              .errorMessage("SMS not enabled")
              .build());
      return true;
    } else {
      log.info("Sending SMS (message length: {})", message.length());

      String rawFromNumber = siteConfigService.getOrEmpty(SiteConfigKey.TWILIO_FROM_NUMBER);
      Optional<String> twilioFromNumber = toFromE164(rawFromNumber);
      if (twilioFromNumber.isEmpty()) {
        log.warn("Twilio from number is not set / invalid in site config; cannot send SMS");
        recordMessage(
            jdbi,
            MessageResult.builder()
                .toNumber(phoneNumber)
                .messageLength(message.length())
                .errorMessage("Twilio from number not configured")
                .build());
        return false;
      }
      Twilio.init(
          siteConfigService.getOrEmpty(SiteConfigKey.TWILIO_ACCOUNT_SID),
          siteConfigService.getOrEmpty(SiteConfigKey.TWILIO_AUTH_TOKEN));

      try {
        // toCanonical yields 11 digits with the leading country code (1XXXXXXXXXX); Twilio wants
        // E.164, so prefix a '+'. This is correct whether the caller passed 10 digits, 11 digits,
        // or an already-"+1"-prefixed value.
        String e164 = "+" + PhoneNumberUtil.toCanonical(phoneNumber);
        Message smsMessage =
            Message.creator(
                    new PhoneNumber(e164),
                    new PhoneNumber(twilioFromNumber.get()),
                    TruncateString.truncate(message, 1500))
                .create();
        recordMessage(jdbi, new MessageResult(smsMessage, message.length()));
        return true;
      } catch (Exception e) {
        log.warn("Failed to send SMS", e);
        recordMessage(
            jdbi,
            MessageResult.builder()
                .toNumber(phoneNumber)
                .messageLength(message.length())
                .errorMessage(
                    "Potentially invalid phone number. Failed to send SMS: " + e.getMessage())
                .build());
        return false;
      }
    }
  }

  /**
   * Normalizes the configured Twilio "from" number to E.164 ({@code +1XXXXXXXXXX}), the same way
   * the recipient number is normalized, so a stored value of {@code 18287444191}, {@code
   * 8287444191}, or {@code +18287444191} all resolve alike. Empty when the config value is unset or
   * not a valid 10- or 11-digit US number.
   */
  // @VisibleForTesting
  static Optional<String> toFromE164(String rawFromNumber) {
    return PhoneNumberUtil.isValid(rawFromNumber)
        ? Optional.of("+" + PhoneNumberUtil.toCanonical(rawFromNumber))
        : Optional.empty();
  }

  @Builder
  @AllArgsConstructor
  @lombok.Value
  static class MessageResult {
    String toNumber;
    int messageLength;
    boolean success;
    String messageLink;
    Integer errorCode;
    String errorMessage;

    MessageResult(Message smsMessage, int messageLength) {
      toNumber = smsMessage.getTo();
      this.messageLength = messageLength;
      success = smsMessage.getErrorCode() == null;
      messageLink = smsMessage.getUri();
      errorCode = smsMessage.getErrorCode();
      errorMessage = smsMessage.getErrorMessage();
    }
  }

  // @VisibleForTesting
  static void recordMessage(Jdbi jdbi, MessageResult result) {
    String insert =
        """
        insert into sms_send_history(number, message_length, success, message_link, error_code, error_message)
        values(:number, :messageLength, :success, :messageLink, :errorCode, :errorMessage)
        """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(insert)
                .bind("number", result.getToNumber())
                .bind("messageLength", result.getMessageLength())
                .bind("success", result.isSuccess())
                .bind("messageLink", result.getMessageLink())
                .bind("errorCode", result.getErrorCode())
                .bind("errorMessage", result.getErrorMessage())
                .execute());
  }
}
