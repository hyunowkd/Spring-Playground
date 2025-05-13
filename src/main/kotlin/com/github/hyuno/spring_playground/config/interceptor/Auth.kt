package com.github.hyuno.spring_playground.config.interceptor

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Auth(val role: Role = Role.USER) {
    enum class Role {
        USER, ADMIN, TEST
    }
}
