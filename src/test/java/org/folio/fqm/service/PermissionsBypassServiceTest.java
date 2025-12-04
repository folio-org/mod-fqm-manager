package org.folio.fqm.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.UUID;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionsBypassServiceTest {

  @Mock
  EntityTypeFlatteningService entityTypeFlatteningService;

  @Mock
  FolioExecutionContext context;

  @InjectMocks
  PermissionsBypassService permissionsService;

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
      permissionsService.verifyUserHasNecessaryPermissions(
        new EntityType().id("930b5d71-e927-5d74-beda-9f9da1e07188"),
        true
      );
      permissionsService.verifyUserHasNecessaryPermissions(
        "tenant_01",
        new EntityType().id("bc7e53b6-8b31-5897-822f-b02fc4db7aa1"),
        UUID.fromString("6374ed0d-275d-53b3-9a34-b2d524ecf7d6"),
        true
      );
      permissionsService.verifyUserCanAccessCustomEntityType(
        new CustomEntityType().id("3b0f1feb-b1bd-5d82-bfdc-78c77f8a952a")
      );
    });
  }
}
