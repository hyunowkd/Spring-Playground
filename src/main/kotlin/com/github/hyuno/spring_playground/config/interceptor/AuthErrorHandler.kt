package com.github.hyuno.spring_playground.config.interceptor

import com.github.hyuno.spring_playground.errorHandler.CommonException
import org.springframework.http.HttpStatus

class AuthenticationException : CommonException("AUTH-100", HttpStatus.UNAUTHORIZED)
