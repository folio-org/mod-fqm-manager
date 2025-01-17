package org.folio.fqm.service;

import org.folio.fqm.client.SimpleHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTenantServiceTest {

  @Mock
  private SimpleHttpClient userTenantsClient;
  @InjectMocks
  private UserTenantService userTenantService;
  @Test
  void shouldGetUserTenantsResponse() {
    String tenantId = "tenant_01";
    String expectedResponse = "{\"totalRecords\": 1}";
    when(userTenantsClient.get(eq("user-tenants"), anyMap())).thenReturn(expectedResponse);
    String actualResponse = userTenantService.getUserTenantsResponse(tenantId);
    assertEquals(expectedResponse, actualResponse);
  }
}
