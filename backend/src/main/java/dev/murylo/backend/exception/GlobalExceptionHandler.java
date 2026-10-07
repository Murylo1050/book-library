package dev.murylo.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice 
public class GlobalExceptionHandler {




    @ExceptionHandler (ResourceNotFoundException.class)
    public  ResponseEntity<ApiError> handleResourceNotFound(
        ResourceNotFoundException ex
    ){
        ApiError error = new ApiError(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage()
        );


        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }


@ExceptionHandler (Exception.class)
    public ResponseEntity<ApiError> handleGeneric(
            Exception ex
    ) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(
                        500,
                        "Ocorreu um erro interno no servidor"
                ));
    }




}
