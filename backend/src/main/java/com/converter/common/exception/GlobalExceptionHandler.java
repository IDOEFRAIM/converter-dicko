package com.converter.common.exception;

import com.converter.common.api.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Point unique de traduction des exceptions en reponses HTTP.
 *
 * <p>Deux principes gouvernent cette classe :
 * <ol>
 *   <li><b>Aucune fuite d'information.</b> Les details techniques
 *       (stack trace, message SQL, nom de contrainte) restent dans les
 *       journaux. Le client recoit un message generique accompagne d'un
 *       {@code traceId} qui permet au support de retrouver l'incident.</li>
 *   <li><b>Un code stable par situation.</b> Le frontend teste
 *       {@code code}, jamais {@code message}, qui reste librement
 *       reformulable.</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -----------------------------------------------------------------
    // Erreurs metier
    // -----------------------------------------------------------------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex,
                                                        HttpServletRequest request) {
        ErrorCode code = ex.errorCode();
        // Une erreur metier est un fonctionnement nominal : journalisee en
        // DEBUG pour ne pas noyer les journaux d'exploitation.
        log.debug("Erreur metier {} sur {} : {}", code, request.getRequestURI(), ex.getMessage());
        return build(code, ex.getMessage(), request, null, null);
    }

    // -----------------------------------------------------------------
    // Validation des entrees
    // -----------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        List<ErrorResponse.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldViolation(
                        error.getField(),
                        error.getDefaultMessage() == null ? "Valeur invalide" : error.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR,
                "Certains champs sont invalides.", request, violations, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(ConstraintViolationException ex,
                                                               HttpServletRequest request) {
        List<ErrorResponse.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldViolation(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR,
                "Certains parametres sont invalides.", request, violations, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        // Le message d'origine peut divulguer la structure interne des DTO.
        log.debug("Corps de requete illisible sur {}", request.getRequestURI(), ex);
        return build(ErrorCode.MALFORMED_REQUEST,
                "Le corps de la requete est absent ou mal forme.", request, null, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        List<ErrorResponse.FieldViolation> violations = List.of(
                new ErrorResponse.FieldViolation(ex.getName(), "Format attendu non respecte"));
        return build(ErrorCode.VALIDATION_ERROR,
                "Un parametre est au mauvais format.", request, violations, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex,
                                                            HttpServletRequest request) {
        List<ErrorResponse.FieldViolation> violations = List.of(
                new ErrorResponse.FieldViolation(ex.getParameterName(), "Parametre obligatoire"));
        return build(ErrorCode.VALIDATION_ERROR,
                "Un parametre obligatoire est absent.", request, violations, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                              HttpServletRequest request) {
        return build(ErrorCode.INVALID_PAYMENT_PROOF,
                "Le fichier depasse la taille maximale autorisee.", request, null, null);
    }

    // -----------------------------------------------------------------
    // Securite
    // -----------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        log.warn("Acces refuse sur {}", request.getRequestURI());
        return build(ErrorCode.ACCESS_DENIED,
                "Vous n'avez pas les droits requis pour cette operation.", request, null, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex,
                                                              HttpServletRequest request) {
        return build(ErrorCode.AUTHENTICATION_REQUIRED,
                "Authentification requise.", request, null, null);
    }

    // -----------------------------------------------------------------
    // Persistance
    // -----------------------------------------------------------------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex,
                                                         HttpServletRequest request) {
        String traceId = newTraceId();
        // Le message PostgreSQL nomme la contrainte violee : utile au
        // diagnostic, mais il revele le schema. Il reste dans les journaux.
        log.warn("Violation d'integrite [traceId={}] sur {}", traceId, request.getRequestURI(), ex);
        return build(ErrorCode.DUPLICATE_RESOURCE,
                "Cette operation entre en conflit avec une donnee existante.",
                request, null, traceId);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                              HttpServletRequest request) {
        log.info("Conflit de concurrence sur {}", request.getRequestURI());
        return build(ErrorCode.CONCURRENT_MODIFICATION,
                "Cette ressource a ete modifiee entre-temps. Rechargez puis reessayez.",
                request, null, null);
    }

    // -----------------------------------------------------------------
    // Divers
    // -----------------------------------------------------------------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex,
                                                          HttpServletRequest request) {
        return build(ErrorCode.RESOURCE_NOT_FOUND,
                "Ressource introuvable.", request, null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = newTraceId();
        log.error("Erreur inattendue [traceId={}] sur {}", traceId, request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR,
                "Une erreur interne est survenue. Communiquez la reference au support.",
                request, null, traceId);
    }

    // -----------------------------------------------------------------

    private ResponseEntity<ErrorResponse> build(ErrorCode code,
                                                String message,
                                                HttpServletRequest request,
                                                List<ErrorResponse.FieldViolation> violations,
                                                String traceId) {
        HttpStatus status = code.status();
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                code.category(),
                code.name(),
                message,
                request.getRequestURI(),
                traceId,
                violations == null || violations.isEmpty() ? null : violations);
        return ResponseEntity.status(status).body(body);
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
