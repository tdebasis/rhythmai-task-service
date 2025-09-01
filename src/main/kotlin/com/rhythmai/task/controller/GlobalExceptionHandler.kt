package com.rhythmai.task.controller

import com.rhythmai.task.model.TaskAccessDeniedException
import com.rhythmai.task.model.TaskNotFoundException
import com.rhythmai.task.model.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {
    
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    
    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorizedException(ex: UnauthorizedException): ResponseEntity<ErrorResponse> {
        logger.warn("Unauthorized access attempt: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse("UNAUTHORIZED", ex.message ?: "Unauthorized"))
    }
    
    @ExceptionHandler(TaskNotFoundException::class)
    fun handleTaskNotFoundException(ex: TaskNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("Task not found: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("NOT_FOUND", ex.message ?: "Task not found"))
    }
    
    @ExceptionHandler(TaskAccessDeniedException::class)
    fun handleTaskAccessDeniedException(ex: TaskAccessDeniedException): ResponseEntity<ErrorResponse> {
        logger.warn("Task access denied: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse("ACCESS_DENIED", ex.message ?: "Access denied"))
    }
    
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ValidationErrorResponse> {
        logger.warn("Validation failed: {}", ex.message)
        
        val errors = ex.bindingResult.allErrors.associate { error ->
            val fieldName = (error as? FieldError)?.field ?: "unknown"
            val errorMessage = error.defaultMessage ?: "Validation failed"
            fieldName to errorMessage
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ValidationErrorResponse("VALIDATION_FAILED", "Request validation failed", errors))
    }
    
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid argument: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("INVALID_ARGUMENT", ex.message ?: "Invalid argument"))
    }
    
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error occurred", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"))
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class ValidationErrorResponse(
    val code: String,
    val message: String,
    val errors: Map<String, String>,
    val timestamp: LocalDateTime = LocalDateTime.now()
)