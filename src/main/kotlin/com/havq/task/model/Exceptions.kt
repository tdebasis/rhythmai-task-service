package com.havq.task.model

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.UNAUTHORIZED)
class UnauthorizedException(message: String) : RuntimeException(message)

@ResponseStatus(HttpStatus.NOT_FOUND)
class TaskNotFoundException(message: String) : RuntimeException(message)

@ResponseStatus(HttpStatus.FORBIDDEN)
class TaskAccessDeniedException(message: String) : RuntimeException(message)

@ResponseStatus(HttpStatus.BAD_REQUEST)
class BadRequestException(message: String) : RuntimeException(message)