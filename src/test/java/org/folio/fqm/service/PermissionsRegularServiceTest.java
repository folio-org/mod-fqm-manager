package org.folio.fqm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.*;
import org.folio.fqm.client.ModPermissionsClient;
import org.folio.fqm.client.ModRolesKeycloakClient;
import org.folio.fqm.exception.CustomEntityTypeAccessDeniedException;
import org.folio.fqm.exception.MissingPermissionsException;
import org.folio.querytool.domain.dto.CustomEntityType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PermissionsRegularServiceTest {

  private final FolioExecutionContext context = mock(FolioExecutionContext.class);
  private final ModPermissionsClient modPermissionsClient = mock(ModPermissionsClient.class);
  private final ModRolesKeycloakClient modRolesKeycloakClient = mock(ModRolesKeycloakClient.class);
  private final EntityTypeFlatteningService entityTypeFlatteningService = mock(EntityTypeFlatteningService.class);
  private final PermissionsRegularService permissionsService = new PermissionsRegularService(
    context,
    modPermissionsClient,
    modRolesKeycloakClient,
    entityTypeFlatteningService
  );
  private static final String TENANT_ID = "tenant_01";
  private static final UUID USER_ID = UUID.randomUUID();

  void setUpMocks(String... permissions) {
    when(context.getUserId()).thenReturn(USER_ID);
    when(context.getTenantId()).thenReturn(TENANT_ID);

    if (permissionsService.isEureka) {
      when(modRolesKeycloakClient.getPermissionsUser(TENANT_ID, USER_ID))
        .thenReturn(new ModRolesKeycloakClient.UserPermissions(List.of(permissions), USER_ID));
    } else {
      when(modPermissionsClient.getPermissionsForUser(TENANT_ID, USER_ID.toString()))
        .thenReturn(new ModPermissionsClient.UserPermissions(List.of(permissions), permissions.length));
    }
  }

  private EntityType getTestEntityType() {
    EntityType entityType = new EntityType(UUID.randomUUID().toString(), "entity type name", true)
      .sources(List.of(new EntityTypeSource("db", "source_alias")));
    when(entityTypeFlatteningService.getFlattenedEntityType(any(UUID.class), eq(TENANT_ID), eq(false)))
      .thenReturn(entityType);
    return entityType;
  }

  @Test
  void testThePermissionsServiceShouldUseTheModPermissionsClientByDefault() {
    setUpMocks("permission1", "permission2");
    assertEquals(2, permissionsService.getUserPermissions().size());
  }

  @Test
  void testUserWithNoPermissionsCanOnlyAccessEntityTypesWithNoPermissions() {
    setUpMocks(); // User has no permissions
    EntityType entityType = getTestEntityType();

    when(context.getTenantId()).thenReturn(TENANT_ID);
    assertDoesNotThrow(
      () -> permissionsService.verifyUserHasNecessaryPermissions(TENANT_ID, entityType, USER_ID, false),
      "No permissions are required"
    );

    entityType.requiredPermissions(List.of("permission1"));
    assertThrows(
      MissingPermissionsException.class,
      () -> permissionsService.verifyUserHasNecessaryPermissions(TENANT_ID, entityType, USER_ID, false),
      "The does not have the required permission"
    );
  }

  @Test
  void testUserHasNecessaryPermissions() {
    // Given a user with 2 permissions
    setUpMocks("permission1", "permission2");
    // When the entity type requires various possible permissions (which the user has)
    Map
      .<List<String>, String>of(
        List.of(),
        "No permissions are required",
        List.of("permission1"),
        "A single permission is required and the user has it",
        List.of("permission2"),
        "The user's 2nd permission is the required one",
        List.of("permission1", "permission2"),
        "Both of the user's permissions are required"
      )
      .forEach((permissions, message) -> {
        EntityType entityType = getTestEntityType().requiredPermissions(permissions);
        // Then the user should be able to perform the operation
        assertDoesNotThrow(() -> permissionsService.verifyUserHasNecessaryPermissions(entityType, false), message);
      });
  }

  @Test
  void testUserDoesNotHaveNecessaryPermissions() {
    setUpMocks("permission1", "permission2");
    EntityType entityType = getTestEntityType();
    UUID userId = UUID.randomUUID();
    when(context.getUserId()).thenReturn(userId);

    entityType.requiredPermissions(List.of("permission3"));
    assertThrows(
      MissingPermissionsException.class,
      () -> permissionsService.verifyUserHasNecessaryPermissions(TENANT_ID, entityType, USER_ID, false),
      "The user does not have the required permission"
    );

    entityType.requiredPermissions(List.of("permission1", "permission3"));
    MissingPermissionsException exception = assertThrows(
      MissingPermissionsException.class,
      () -> permissionsService.verifyUserHasNecessaryPermissions(TENANT_ID, entityType, USER_ID, false)
    );

    assertEquals(Set.of("permission3"), exception.getMissingPermissions());
    assertTrue(exception.getMessage().contains("permission3"));
    assertFalse(exception.getMessage().contains("permission1"));
  }

  @Test
  void testCheckingFqmPermissionsIfRequested() {
    setUpMocks("permission1", "permission2", "fqm.entityTypes.item.get");
    EntityType entityType = getTestEntityType();
    entityType.requiredPermissions(List.of("permission1"));
    Set<String> expectedMissingPermissions = Set.of("fqm.query.async.results.get", "fqm.query.async.post");

    MissingPermissionsException exception = assertThrows(
      MissingPermissionsException.class,
      () -> permissionsService.verifyUserHasNecessaryPermissions(TENANT_ID, entityType, USER_ID, true)
    );

    assertEquals(expectedMissingPermissions, exception.getMissingPermissions());
  }

  @Test
  void testGetPermissionsWhenEurekaIsTrue() {
    permissionsService.isEureka = true;
    setUpMocks("permission1", "permission2");
    Set<String> userPermissions = permissionsService.getUserPermissions();

    ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
    verify(modRolesKeycloakClient).getPermissionsUser(eq(TENANT_ID), userIdCaptor.capture());
    assertEquals(2, userPermissions.size());

    // Ensure no interactions with modPermissionsClient
    verifyNoInteractions(modPermissionsClient);
  }

  @Test
  void testGetPermissionsWhenEurekaIsFalse() {
    permissionsService.isEureka = false;
    setUpMocks("permission1", "permission2");
    Set<String> userPermissions = permissionsService.getUserPermissions();

    ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(modPermissionsClient).getPermissionsForUser(eq(TENANT_ID), userIdCaptor.capture());
    assertEquals(2, userPermissions.size());

    // Ensure no interactions with modRolesKeyclockClient
    verifyNoInteractions(modRolesKeycloakClient);
  }

  @Test
  void testUserCanAccessCustomEntityTypeWhenShared() {
    CustomEntityType customEntityType = new CustomEntityType()
      .id("8026b64b-568c-5f1a-b5a1-6743fd741335")
      .owner(UUID.fromString("c580ad80-d30c-5be6-bf5b-e21698fda458"))
      .shared(true)
      .isCustom(true);

    permissionsService.verifyUserCanAccessCustomEntityType(customEntityType);
  }

  @Test
  void testUserCanAccessCustomEntityTypeWhenOwned() {
    CustomEntityType customEntityType = new CustomEntityType()
      .id("34d371d2-8fbc-5eb6-bddb-ce3f1d493dba")
      .owner(UUID.fromString("0e5221c9-ac62-5c2d-ac7c-6d38ad035418"))
      .shared(false)
      .isCustom(true);

    when(context.getUserId()).thenReturn(UUID.fromString("0e5221c9-ac62-5c2d-ac7c-6d38ad035418")); // Same as owner

    permissionsService.verifyUserCanAccessCustomEntityType(customEntityType);
  }

  @Test
  void testUserCanNotAccessCustomEntityTypeWhenNotOwnedNorShared() {
    CustomEntityType customEntityType = new CustomEntityType()
      .id("8f016369-cedf-57cf-962d-e8e5f325abc7")
      .owner(UUID.fromString("f7d2631a-7701-554b-b9bd-ee681345e2bb"))
      .shared(false)
      .isCustom(true);

    when(context.getUserId()).thenReturn(UUID.fromString("c682b66a-6900-5aca-8e13-ae27dba77ca4"));

    assertThrows(
      CustomEntityTypeAccessDeniedException.class,
      () -> permissionsService.verifyUserCanAccessCustomEntityType(customEntityType),
      "Should throw CustomEntityTypeAccessDeniedException when custom entity type is not accessible"
    );
  }
}
