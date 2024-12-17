package org.folio.fqm.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

@Service
@FeignClient(name = "groups")
public interface PatronGroupsClient {
  @GetMapping(params = "limit=1000")
  public PatronGroupsResponse getGroups();

  public record PatronGroupsResponse(List<PatronGroup> usergroups) {}

  public record PatronGroup(String id, String group) {}
}
