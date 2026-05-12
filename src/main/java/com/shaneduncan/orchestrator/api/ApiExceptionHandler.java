package com.shaneduncan.orchestrator.api;

import com.shaneduncan.orchestrator.domain.job.InvalidJobStateTransitionException;
import com.shaneduncan.orchestrator.domain.job.JobAssignmentOwnershipException;
import com.shaneduncan.orchestrator.domain.job.StaleJobAssignmentException;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoSuchElementException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({
        InvalidJobStateTransitionException.class,
        JobAssignmentOwnershipException.class,
        StaleJobAssignmentException.class
    })
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity
            .status(status)
            .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
