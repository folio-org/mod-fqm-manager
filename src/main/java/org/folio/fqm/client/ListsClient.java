package org.folio.fqm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Service
@FeignClient(name = "lists")
public interface ListsClient {
  @GetMapping(value = "")
  public ListsResponse getLists(@RequestParam String entityTypeId);

  public record ListsResponse(List<ListEntity> lists) {}

  public record ListEntity(String id, String name) {}
}
