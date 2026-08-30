package com.vanatta.helene.supplies.database.twilio.sms;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import com.vanatta.helene.supplies.database.siteconfig.SiteConfigKey;
import com.vanatta.helene.supplies.database.siteconfig.SiteConfigService;
import com.vanatta.helene.supplies.database.util.TruncateString;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
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
          String.format("Null input, phoneNumber: %s, message: %s", phoneNumber, message));
    }

    if (!twilioSmsEnabled) {
      log.info("SMS disabled, would have sent to: {}, message: {}", phoneNumber, message);
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
      log.info("Sending SMS to: {}, message length: {}", phoneNumber, message.length());

      String twilioFromNumber = siteConfigService.getOrEmpty(SiteConfigKey.TWILIO_FROM_NUMBER);
      if (!twilioFromNumber.startsWith("+1")) {
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
        Message smsMessage =
            Message.creator(
                    new PhoneNumber(
                        phoneNumber.startsWith("+1") ? phoneNumber : "+1" + phoneNumber),
                    new PhoneNumber(twilioFromNumber),
                    TruncateString.truncate(message, 1500))
                .create();
        recordMessage(jdbi, new MessageResult(smsMessage, message.length()));
        return true;
      } catch (Exception e) {
        log.warn("Failed to send SMS to: {}, with message: {}", phoneNumber, message, e);
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
