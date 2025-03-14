package org.folio.fqm.aspect;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.folio.fqm.annotation.EntityTypePermissionsRequired;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.service.PermissionsService;
import org.folio.querytool.domain.dto.ContentsRequest;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.SubmitQuery;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Aspect
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EntityTypePermissionsAspect {

  private final EntityTypeRepository entityTypeRepository;
  private final QueryRepository queryRepository;
  private final PermissionsService permissionsService;
  private final FolioExecutionContext executionContext;

  private final Map<MethodSignature, Integer> indexCache = new ConcurrentHashMap<>();

  /**
   * Handle methods that accept an entity type or query ID
   */
  // package-private, to make this visible for testing
  @Around("@annotation(org.folio.fqm.annotation.EntityTypePermissionsRequired) && execution(* *(.., java.util.UUID, ..))")
  Object validatePermissionsWithId(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature methodSignature = ((MethodSignature) joinPoint.getSignature());
    EntityTypePermissionsRequired annotation = methodSignature.getMethod().getAnnotation(EntityTypePermissionsRequired.class);

    return validatePermissions(joinPoint, (UUID id) -> {
      var entityTypeId = switch (annotation.idType()) {
        case ENTITY_TYPE -> id;
        case QUERY -> queryRepository.getQuery(id, true)
          .orElseThrow(() -> new EntityTypeNotFoundException(id))
          .entityTypeId();
      };
      return getEntityTypeFromId(entityTypeId);
    });
  }

  /**
   * Handle methods that accept an EntityType object
   */
  // package-private, to make this visible for testing
  @Around("@annotation(org.folio.fqm.annotation.EntityTypePermissionsRequired) && execution(* *(.., org.folio.querytool.domain.dto.EntityType, ..))")
  Object validatePermissionsWithEntityType(ProceedingJoinPoint joinPoint) throws Throwable {
    return validatePermissions(joinPoint, Function.identity());
  }

  /**
   * Handle methods that accept a SubmitQuery object
   */
  // package-private, to make this visible for testing
  @Around("@annotation(org.folio.fqm.annotation.EntityTypePermissionsRequired) && execution(* *(.., org.folio.querytool.domain.dto.SubmitQuery, ..))")
  Object validatePermissionsWithSubmitQuery(ProceedingJoinPoint joinPoint) throws Throwable {
    return validatePermissions(joinPoint, (SubmitQuery query) -> getEntityTypeFromId(query.getEntityTypeId()));
  }

  /**
   * Handle methods that accept a ContentsRequest object
   */
  // package-private, to make this visible for testing
  @Around("@annotation(org.folio.fqm.annotation.EntityTypePermissionsRequired) && execution(* *(.., org.folio.querytool.domain.dto.ContentsRequest, ..))")
  Object validatePermissionsWithContentsRequest(ProceedingJoinPoint joinPoint) throws Throwable {
    return validatePermissions(joinPoint, (ContentsRequest request) -> getEntityTypeFromId(request.getEntityTypeId()));
  }

  private EntityType getEntityTypeFromId(UUID entityTypeId) {
    return entityTypeRepository.getEntityTypeDefinition(entityTypeId, executionContext.getTenantId())
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
  }

  /**
   * Validates that the user has the necessary permissions to perform the operation.
   *
   * @param entityTypeConverter - A function to retrieve an EntityType object from the target method parameter.
   */
  @SuppressWarnings("unchecked") // If the types are wrong, we want to fail loudly
  private <T> Object validatePermissions(ProceedingJoinPoint joinPoint, Function<T, EntityType> entityTypeConverter) throws Throwable {
    // 1. Retrieve the annotated method and annotation details.
    MethodSignature methodSignature = ((MethodSignature) joinPoint.getSignature());
    EntityTypePermissionsRequired annotation = methodSignature.getMethod().getAnnotation(EntityTypePermissionsRequired.class);
    String entityTypeParamName = annotation.parameterName();
    Class<?> entityTypeParamType = annotation.value();

    // 2. Find the parameter with the entity type (as described by the annotation).
    int paramIndex = indexCache.computeIfAbsent(methodSignature,
      signature -> {
        if (annotation.parameterName() != null && !annotation.parameterName().isEmpty())
          return Arrays.asList(signature.getParameterNames()).indexOf(entityTypeParamName);
        return Arrays.asList(signature.getParameterTypes()).indexOf(entityTypeParamType);
      });
    if (paramIndex == -1) {
      throw new EntityTypeParameterNotFoundException();
    }

    // 3. Get the actual parameter value and use it to retrieve the entity type.
    T param = (T) joinPoint.getArgs()[paramIndex]; // Don't bother checking before casting, since if this is broken, we want it to break very loudly
    EntityType entityType = entityTypeConverter.apply(param);

    // 4. Validate the permissions. An exception will be thrown if the user does not have the necessary permissions.
    permissionsService.verifyUserHasNecessaryPermissions(entityType, false);

    // 5. Proceed with the original method call.
    return joinPoint.proceed();
  }

  private static class EntityTypeParameterNotFoundException extends RuntimeException {
    public EntityTypeParameterNotFoundException() {
      super("Could not find the parameter for accessing entity type");
    }
  }
}
