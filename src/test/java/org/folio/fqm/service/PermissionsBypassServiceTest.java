package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.UUID;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;
import org.junit.jupiter.api.Test;

class PermissionsBypassServiceTest {

  // all methods should work with zero context
  private final PermissionsBypassService permissionsService = new PermissionsBypassService();

  @Test
  void testAllMethodsBypassed() {
    assertThat(permissionsService.getUserPermissions(), is(empty()));
    assertThat(
      permissionsService.getUserPermissions("foo", UUID.fromString("f8983a3b-268e-5649-a03f-e7e4a856412e")),
      is(empty())
    );
    assertThat(permissionsService.getRequiredPermissions(new EntityType()), is(empty()));
    assertThat(
      permissionsService.canUserAccessCustomEntityType(
        new CustomEntityType().id("3b0f1feb-b1bd-5d82-bfdc-78c77f8a952a")
      ),
      is(true)
    );

    assertDoesNotThrow(() -> {
      permissionsService.verifyUserHasNecessaryPermissions(new EntityType(), true);
      permissionsService.verifyUserHasNecessaryPermissions(
        "tenant_01",
        new EntityType(),
        UUID.fromString("6374ed0d-275d-53b3-9a34-b2d524ecf7d6"),
        true
      );
      permissionsService.verifyUserCanAccessCustomEntityType(
        new CustomEntityType().id("3b0f1feb-b1bd-5d82-bfdc-78c77f8a952a")
      );
    });
  }
}
