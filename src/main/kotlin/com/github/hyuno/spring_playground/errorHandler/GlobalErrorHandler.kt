package com.github.hyuno.spring_playground.errorHandler

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

open class CommonException(
    message: String,
    val status: HttpStatus
) : RuntimeException(message)