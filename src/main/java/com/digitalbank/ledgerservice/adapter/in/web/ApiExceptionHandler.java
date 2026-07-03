package com.digitalbank.ledgerservice.adapter.in.web;

import com.digitalbank.ledgerservice.domain.exception.DuplicatePostingRequestException;
import com.digitalbank.ledgerservice.domain.exception.LedgerEntryNotFoundException;
import com.digitalbank.ledgerservice.domain.exception.UnbalancedLedgerEntryException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(LedgerEntryNotFoundException.class)
    ResponseEntity<ProblemDetail> handleLedgerEntryNotFound(LedgerEntryNotFoundException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Ledger entry not found");
        problem.setType(URI.create("https://digital-bank-java.local/problems/ledger-entry-not-found"));
        problem.setProperty("ledgerEntryId", exception.ledgerEntryId().value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler({DuplicatePostingRequestException.class, DataIntegrityViolationException.class})
    ResponseEntity<ProblemDetail> handlePostingConflict(RuntimeException exception) {
        var problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Posting request conflicts with existing data");
        problem.setTitle("Ledger posting conflict");
        problem.setType(URI.create("https://digital-bank-java.local/problems/ledger-posting-conflict"));
        if (exception instanceof DuplicatePostingRequestException duplicatePostingRequestException) {
            problem.setProperty("postingRequestId", duplicatePostingRequestException.postingRequestId());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(UnbalancedLedgerEntryException.class)
    ResponseEntity<ProblemDetail> handleUnbalancedLedgerEntry(UnbalancedLedgerEntryException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid ledger entry");
        problem.setType(URI.create("https://digital-bank-java.local/problems/unbalanced-ledger-entry"));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidationFailure(MethodArgumentNotValidException exception) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://digital-bank-java.local/problems/validation-error"));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception) {
        var errors = exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()))
                .toList();

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://digital-bank-java.local/problems/validation-error"));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }
}
