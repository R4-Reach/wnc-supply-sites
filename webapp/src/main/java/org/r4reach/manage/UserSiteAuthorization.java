package org.r4reach.manage;

import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.supplies.site.details.SiteDetailDao;
import org.r4reach.util.PhoneNumberUtil;

public class UserSiteAuthorization {
  /** checks if user is authorized for current site, if so, returns the site name. */
  public static Optional<SiteDetailDao.SiteDetailData> isAuthorizedForSite(
      Jdbi jdbi, List<Long> authorizedSites, String currentSite) {
    if (currentSite == null
        || currentSite.isBlank()
        || PhoneNumberUtil.removeNonNumeric(currentSite).length() != currentSite.length()
        || !authorizedSites.contains(Long.parseLong(currentSite))) {
      return Optional.empty();
    }
    return Optional.ofNullable(SiteDetailDao.lookupSiteById(jdbi, Long.parseLong(currentSite)));
  }
}
