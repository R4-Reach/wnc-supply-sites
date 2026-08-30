package org.r4reach;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Ensure request is by appropriate domain name, otherwise issue a redirect. */
@Configuration
public class DomainNameInterceptor implements WebMvcConfigurer {

  public DomainNameInterceptor() {}

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new DomainInterceptor());
  }

  @AllArgsConstructor
  static class DomainInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

      String host = request.getHeader("host");

      if (host.contains("wnc-supply-sites.com") || host.contains("localhost")) {
        return true;
      } else {
        response.sendRedirect("https://wnc-supply-sites.com");
        return false;
      }
    }
  }
}
