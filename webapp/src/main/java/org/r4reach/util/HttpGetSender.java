package org.r4reach.util;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/** Utility class for sending HTTP POST messages. */
@Slf4j
public class HttpGetSender {

  // @VisibleForTesting
  static String buildUrl(String url, Map<String, String> params) {
    if (url == null) {
      throw new NullPointerException("Illegal null url");
    }
    if (params.isEmpty()) {
      return url;
    } else {
      String queryString =
          params.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .map(e -> e.getKey() + "=" + UrlEncode.encode(e.getValue().trim()))
              .collect(Collectors.joining("&"));
      return url + "?" + queryString;
    }
  }

  public static <T> T sendRequest(String url, Map<String, String> params, Class<T> responseClass) {
    if (!url.startsWith("http")) {
      throw new IllegalArgumentException("Invalid url: " + url);
    }

    var uri = URI.create(buildUrl(url, params));
    // Log only scheme/host/path — the query string can carry secrets (e.g. the Maps API key).
    log.info("Sending get request to: {}://{}{}", uri.getScheme(), uri.getHost(), uri.getPath());
    try (var client = HttpClient.newHttpClient()) {
      var request = HttpRequest.newBuilder(uri).GET().build();

      try {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
          log.info("Response success: 200, response length: {}", response.body().length());
        } else {
          log.error(
              "Failed, bad response received: {}, {}", response.statusCode(), response.body());
        }
        log.debug("raw response: {}", response.body());
        return new Gson().fromJson(response.body(), responseClass);
      } catch (IOException | InterruptedException e) {
        log.error("Failed to send data to URL: {}", url, e);
        throw new RuntimeException(e);
      }
    }
  }
}
