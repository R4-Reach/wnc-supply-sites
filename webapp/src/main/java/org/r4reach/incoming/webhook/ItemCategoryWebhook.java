package org.r4reach.incoming.webhook;

import com.google.gson.Gson;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.manage.inventory.ItemTagDao;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Webhook for receiving info about category information. */
@Slf4j
@Controller
@AllArgsConstructor
public class ItemCategoryWebhook {

  private final Jdbi jdbi;

  @PostMapping("/webhook/inventory/update-item-tags")
  ResponseEntity<String> updateItemTags(@RequestBody String input) {
    log.info("Received item category info: {}", input);

    ItemTagsInput itemTagsInput = ItemTagsInput.parse(input);
    ItemTagDao.updateDescriptionTags(jdbi, itemTagsInput.getWssId(), itemTagsInput.getTags());

    return ResponseEntity.ok("ok");
  }

  @Value
  static class ItemTagsInput {
    Long wssId;
    List<String> tags;

    static ItemTagsInput parse(String input) {
      return new Gson().fromJson(input, ItemTagsInput.class);
    }
  }
}
