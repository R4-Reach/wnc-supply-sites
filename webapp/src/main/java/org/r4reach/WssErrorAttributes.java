package org.r4reach;

import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

/**
 * Supplies the model for the branded error page ({@code public/error.html}), which replaces Spring
 * Boot's default whitelabel error page.
 *
 * <p>On top of Boot's standard error attributes it adds:
 *
 * <ul>
 *   <li>{@code requestId} — the per-request correlation id, the same value printed in the logs via
 *       the {@code %X{requestId}} MDC key. Showing it on the page lets a user quote it so we can
 *       find their exact request in the logs.
 *   <li>{@code headline}/{@code detail} — human-readable copy chosen from the status code. Mustache
 *       can't branch on a value, so the 404-vs-error wording is decided here.
 *   <li>{@code showLogin} — true for 401/403, so the page can offer a log-in action instead of the
 *       default back-to-home one for an access-denied error.
 *   <li>{@code contactUsLink} — the shared "contact us" destination, so the page's contact link
 *       matches the one on the home and login pages.
 * </ul>
 *
 * <p>The exception message and stack trace are added to the model only under the {@code local}
 * profile; in every other environment they are withheld so internal detail never reaches a user.
 */
@Component
public class WssErrorAttributes extends DefaultErrorAttributes {

  /**
   * Request attribute carrying the correlation id into the error dispatch. {@link
   * org.r4reach.auth.UserMdcLoggingInterceptor} sets it during the original request. The MDC copy
   * that filter also sets is already removed by the time the error page renders (it is cleared in
   * the filter's {@code finally}, which runs before the container's error dispatch), so the page
   * reads the request attribute instead.
   */
  public static final String REQUEST_ID_ATTRIBUTE = "wss.requestId";

  private final Environment environment;

  public WssErrorAttributes(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Map<String, Object> getErrorAttributes(
      WebRequest webRequest, ErrorAttributeOptions options) {
    boolean local = environment.matchesProfiles("local");
    ErrorAttributeOptions effective =
        local
            ? options.including(Include.MESSAGE, Include.EXCEPTION, Include.STACK_TRACE)
            : options.excluding(
                Include.MESSAGE, Include.EXCEPTION, Include.STACK_TRACE, Include.BINDING_ERRORS);
    Map<String, Object> attributes = super.getErrorAttributes(webRequest, effective);

    attributes.put(
        "requestId",
        webRequest.getAttribute(REQUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));

    int status = ((Number) attributes.getOrDefault("status", 0)).intValue();
    attributes.put("headline", headline(status));
    attributes.put("detail", detail(status));
    attributes.put("showLogin", isAccessDenied(status));
    attributes.put("contactUsLink", SimpleHtmlController.CONTACT_US_LINK);
    return attributes;
  }

  private static boolean isAccessDenied(int status) {
    return status == HttpStatus.FORBIDDEN.value() || status == HttpStatus.UNAUTHORIZED.value();
  }

  private static String headline(int status) {
    if (status == HttpStatus.NOT_FOUND.value()) {
      return "Page not found";
    }
    if (isAccessDenied(status)) {
      return "You can't access that";
    }
    return "Something went wrong";
  }

  private static String detail(int status) {
    if (status == HttpStatus.NOT_FOUND.value()) {
      return "We couldn't find that page. It may have moved, or the link may be out of date.";
    }
    if (isAccessDenied(status)) {
      return "You may need to log in, or you may not have permission to view this page.";
    }
    return "The site ran into a problem completing your request. Please try again in a moment. If"
        + " the problem continues, contact us and include the reference below.";
  }
}
