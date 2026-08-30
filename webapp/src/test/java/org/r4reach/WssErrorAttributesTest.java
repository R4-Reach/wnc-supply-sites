package org.r4reach;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class WssErrorAttributesTest {

  private static ServletWebRequest errorRequest(int status, Throwable error) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
    if (error != null) {
      request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, error);
      request.setAttribute(RequestDispatcher.ERROR_MESSAGE, error.getMessage());
    }
    request.setAttribute(WssErrorAttributes.REQUEST_ID_ATTRIBUTE, "ab12c");
    return new ServletWebRequest(request);
  }

  @Test
  void surfacesCorrelationIdAndStatusSpecificCopy() {
    WssErrorAttributes errorAttributes = new WssErrorAttributes(new MockEnvironment());

    Map<String, Object> model =
        errorAttributes.getErrorAttributes(
            errorRequest(404, null), ErrorAttributeOptions.defaults());

    assertThat(model.get("requestId")).isEqualTo("ab12c");
    assertThat(model.get("headline")).isEqualTo("Page not found");
    assertThat(model.get("status")).isEqualTo(404);
  }

  @Test
  void withholdsExceptionDetailOutsideLocalProfile() {
    WssErrorAttributes errorAttributes = new WssErrorAttributes(new MockEnvironment());

    // Even when Boot asks to include the trace/message, a non-local environment strips them.
    Map<String, Object> model =
        errorAttributes.getErrorAttributes(
            errorRequest(500, new IllegalStateException("boom")),
            ErrorAttributeOptions.of(Include.STACK_TRACE, Include.MESSAGE));

    assertThat(model).doesNotContainKeys("trace", "message", "exception");
    assertThat(model.get("headline")).isEqualTo("Something went wrong");
  }

  @Test
  void includesStackTraceUnderLocalProfile() {
    MockEnvironment local = new MockEnvironment();
    local.setActiveProfiles("local");
    WssErrorAttributes errorAttributes = new WssErrorAttributes(local);

    // Boot asks for nothing extra, but the local profile forces the trace/message in anyway.
    Map<String, Object> model =
        errorAttributes.getErrorAttributes(
            errorRequest(500, new IllegalStateException("boom")), ErrorAttributeOptions.defaults());

    assertThat(model).containsKey("trace");
    assertThat((String) model.get("trace")).contains("IllegalStateException");
    assertThat(model.get("message")).isEqualTo("boom");
  }
}
