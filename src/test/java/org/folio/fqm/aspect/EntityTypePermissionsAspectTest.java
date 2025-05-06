package org.folio.fqm.aspect;

import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.folio.fqm.annotation.EntityTypePermissionsRequired;
import org.folio.fqm.domain.Query;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.service.PermissionsService;
import org.folio.querytool.domain.dto.ContentsRequest;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.SubmitQuery;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EntityTypePermissionsAspectTest {

  private final EntityTypeRepository entityTypeRepository = mock(EntityTypeRepository.class);
  private final QueryRepository queryRepository = mock(QueryRepository.class);
  private final PermissionsService permissionsService = mock(PermissionsService.class);
  private final FolioExecutionContext executionContext = mock(FolioExecutionContext.class);

  @Test
  void testUuidsCanBeHandled() {
    testMethodSignatureCanBeHandled(EntityTypePermissionsAspect::validatePermissionsWithId,
      "methodWithUuidParam",
      entityType -> new Object[]{UUID.fromString(entityType.getId())},
      UUID.class);
  }

  @Test
  void testMultipleUuidsCanBeHandled() {
    testMethodSignatureCanBeHandled(EntityTypePermissionsAspect::validatePermissionsWithId,
      "methodWithMultipleUuidParams",
      entityType -> new Object[]{UUID.randomUUID(), UUID.fromString(entityType.getId())},
      UUID.class, UUID.class);
  }

  @Test
  void testQueryIdCanBeHandled() {
    var queryId = UUID.randomUUID();
    var entityTypeId = UUID.randomUUID();
    Query query = new Query(queryId, entityTypeId, null, null, null, null, null, null, null);
    when(queryRepository.getQuery(any(UUID.class), anyBoolean())).thenReturn(Optional.of(query));
    testMethodSignatureCanBeHandled(EntityTypePermissionsAspect::validatePermissionsWithId,
      "methodWithQueryIdParam",
      entityType -> new Object[]{queryId},
      UUID.class);
  }

  @Test
  void testEntityTypesCanBeHandled() {
    testMethodSignatureCanBeHandled(EntityTypePermissionsAspect::validatePermissionsWithEntityType,
      "methodWithEntityTypeParam",
      entityType -> new Object[]{entityType},
      EntityType.class);
  }

  @Test
  void testSubmitQueriesCanBeHandled() {
    testMethodSignatureCanBeHandled(EntityTypePermissionsAspect::validatePermissionsWithSubmitQuery,
      "methodWithSubmitQueryParam",
      entityType -> new Object[]{new SubmitQuery(null, UUID.fromString(entityType.getId()))},
      SubmitQuery.class);
  }

  @Test
  void testContentsRequestsCanBeHandled() {
    testMethodSignatureCanBeHandled(EntityTypePermissionsAspect::validatePermissionsWithContentsRequest,
      "methodWithContentsRequestParam",
      entityType -> new Object[]{new ContentsRequest(UUID.fromString(entityType.getId()), null, null)},
      ContentsRequest.class);
  }

  /**
   * Verify that the aspect can handle the given method signature
   * <p>
   * Example: To handle an entity type ID passed to the "handleEntityTypeId(UUID entityTypeId, UUID queryId)" method, use
   * <code>
   * testMethodSignatureCanBeHandled(EntityTypePermissionsAspect::validatePermissionsWithId, // Aspect handler method
   *   "handleEntityTypeId", // The dummy annotated method to test with
   *   entityType -> new Object[]{UUID.fromString(entityType.getId()), null}, // Actual target method params for the dummy method
   *   UUID.class, UUID.class); // The parameter types for the dummy method
   * </code>
   *
   * @param methodHandler   - The method within the aspect should be called to handle the annotated method.
   * @param methodName      - The name of the annotated method.
   * @param paramsConverter - A function to build the arguments for the annotated method; the entity type is provided.
   * @param paramTypes      - The parameter types for the annotated method.
   */
  @SneakyThrows
  void testMethodSignatureCanBeHandled(AspectMethodHandler methodHandler, String methodName, Function<EntityType, Object[]> paramsConverter, Class<?>... paramTypes) {
    // Create a mock ProceedingJoinPoint for a dummy method, and use it to call the aspect
    // No permission checking is performed, so we can just verify that the permission check would hav happened and that the aspect calls proceed()
    EntityTypePermissionsAspect aspect = new EntityTypePermissionsAspect(entityTypeRepository, queryRepository, permissionsService, executionContext);
    EntityType entityType = new EntityType(UUID.randomUUID().toString(), "name", true);
    when(entityTypeRepository.getEntityTypeDefinition(any(UUID.class), any(String.class))).thenReturn(Optional.of(entityType));
    when(entityTypeRepository.getEntityTypeDefinition(any(UUID.class), eq(null))).thenReturn(Optional.of(entityType));
    ProceedingJoinPoint joinPoint = mockJoinPoint(this.getClass().getDeclaredMethod(methodName, paramTypes), paramsConverter.apply(entityType));
    methodHandler.accept(aspect, joinPoint);
    verify(permissionsService).verifyUserHasNecessaryPermissions(any(EntityType.class), eq(false));
    verify(joinPoint).proceed();
  }

  /**
   * Creates a mock ProceedingJoinPoint for the given entity type and method
   */
  private ProceedingJoinPoint mockJoinPoint(Method target, Object[] methodArgs) {
    MethodSignature signature = mock(MethodSignature.class);
    when(signature.getMethod()).thenReturn(target);
    when(signature.getParameterTypes()).thenReturn(target.getParameterTypes());
    when(signature.getParameterNames()).thenReturn(Arrays.stream(target.getParameters()).map(Parameter::getName).toArray(String[]::new));
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.getSignature()).thenReturn(signature);
    when(joinPoint.getArgs()).thenReturn(methodArgs);
    return joinPoint;
  }

  // These methods are just here to build Method objects for testing, to validate that the aspect can handle the annotation
  // and access the method parameter(s). These don't even actually get called.
  @EntityTypePermissionsRequired
  private void methodWithUuidParam(UUID entityTypeId) {
  }

  @EntityTypePermissionsRequired(parameterName = "entityTypeId")
  private void methodWithMultipleUuidParams(UUID someOtherId, UUID entityTypeId) {
  }

  @EntityTypePermissionsRequired(idType = EntityTypePermissionsRequired.IdType.QUERY)
  private void methodWithQueryIdParam(UUID queryId) {
  }

  @EntityTypePermissionsRequired(EntityType.class)
  private void methodWithEntityTypeParam(EntityType entityType) {
  }

  @EntityTypePermissionsRequired(SubmitQuery.class)
  private void methodWithSubmitQueryParam(SubmitQuery submitQuery) {
  }

  @EntityTypePermissionsRequired(ContentsRequest.class)
  private void methodWithContentsRequestParam(ContentsRequest contentsRequest) {
  }

  // Functional interface for the handlers in {@link EntityTypePermissionsAspect}
  // Use this instead of a BiConsumer because the handlers can throw checked exceptions
  @FunctionalInterface
  private interface AspectMethodHandler {
    void accept(EntityTypePermissionsAspect entityTypePermissionsAspect, ProceedingJoinPoint joinPoint) throws Throwable;
  }
}
