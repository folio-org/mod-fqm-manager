package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.folio.querytool.domain.dto.EntityType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class PermissionsBypassServiceTest {

  // all methods should work with zero context
  private final PermissionsBypassService permissionsService = new PermissionsBypassService();

  @Test
  void testAllMethodsBypassed() {
    assertThat(permissionsService.getUserPermissions(), is(empty()));
    assertThat(permissionsService.getUserPermissions("foo", UUID.randomUUID()), is(empty()));
    assertThat(permissionsService.getRequiredPermissions(new EntityType()), is(empty()));

    assertDoesNotThrow(() -> {
      permissionsService.verifyUserHasNecessaryPermissions(new EntityType(), true);
      permissionsService.verifyUserHasNecessaryPermissions("tenant_01", new EntityType(), UUID.randomUUID(),true);
    });
  }
}
