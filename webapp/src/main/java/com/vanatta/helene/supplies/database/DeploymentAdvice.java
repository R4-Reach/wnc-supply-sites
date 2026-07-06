package com.vanatta.helene.supplies.database;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * A 'deployment' is a different geographical deployment of the software. It is a different region,
 * conceptually a different 'instance.' (except not, we run just one website instance). Each
 * deployment typically will have a unique domain name associated with it.
 *
 * <p>Configures parameters based upon the requested domain. Different deployments are partitioned
 * by the domain name.
 */
@ControllerAdvice
@Slf4j
@AllArgsConstructor
public class DeploymentAdvice {
  public static final String DEPLOYMENT_STATE_LIST = "deploymentStateList";
  public static final String DEPLOYMENT_FULL_STATE_LIST = "deploymentFullStateList";
  private final Jdbi jdbi;

  @ModelAttribute(DEPLOYMENT_STATE_LIST)
  public List<String> stateList() {
    return jdbi.withHandle(
        h ->
            h.createQuery(
                    """
              select distinct state
              from county c
              join site s on s.county_id = c.id
              where s.active is true
              order by state
            """)
                .mapTo(String.class)
                .list());
  }

  @ModelAttribute(DEPLOYMENT_FULL_STATE_LIST)
  public List<String> allStates() {
    return jdbi.withHandle(
        h ->
            h.createQuery(
                    """
              select distinct state
              from county c
              order by state
            """)
                .mapTo(String.class)
                .list());
  }
}
