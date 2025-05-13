package com.github.hyuno.spring_playground.config.interceptor

import com.github.hyuno.spring_playground.config.interceptor.Auth.Role.*
import com.github.hyuno.spring_playground.errorHandler.CommonException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.http.HttpStatus
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.io.IOException


class PermissionInterceptor : HandlerInterceptor {

    private fun getFirebaseTokenFromAuthorizationHeader(authorizationHeader: String): FirebaseToken {
        try {
            val idToken = authorizationHeader.substring(7)
            return FirebaseAuth.getInstance().verifyIdToken(idToken)
        } catch (e: FirebaseException) {
            log.error("AUTH-100 : 파이어베이스 토큰 인증 오류 : $e")
            throw CommonException("AUTH-100", HttpStatus.UNAUTHORIZED)
        } catch (e: StringIndexOutOfBoundsException) {
            log.error("AUTH-100 :헤더에 데이터가 없어요. : $e")
            throw CommonException("AUTH-100", HttpStatus.UNAUTHORIZED)
        }
    }

    private fun checkAdmin(firebaseToken: FirebaseToken): Boolean {

        val memberUidList = listOf(
            ""
        )

        return memberUidList.contains(firebaseToken.uid)
    }

    @Throws(IOException::class)
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) {
            return true
        }

        // SkipAuth 처리(인증 우회)
        val skipAuth = handler.getMethodAnnotation(SkipAuth::class.java)

        if (skipAuth != null) {
            // Bearer Token이 있는 경우 `uid` 설정, 없는 경우 `uid`는 null
            val tokenString = request.getHeader("Authorization")
            if (tokenString != null) {
                val firebaseToken = getFirebaseTokenFromAuthorizationHeader(tokenString)
                request.setAttribute("uid", firebaseToken.uid)
            } else {
                request.setAttribute("uid", null)
            }
            return true
        }

        val auth: Auth = handler.getMethodAnnotation(Auth::class.java) ?: return true

        if (auth.role == TEST) {
            request.setAttribute("uid", "2043766769");
            log.info("테스트 API 조회 : [ 컨트롤러 : ${handler.method.declaringClass} 메소드 : ${handler.method.name} ]")
            return true
        }
        val tokenString = request.getHeader("Authorization") ?: throw AuthenticationException()
        val firebaseToken = getFirebaseTokenFromAuthorizationHeader(tokenString)
        request.setAttribute("uid", firebaseToken.uid);

        if (auth.role == USER) {
            log.info("유저 API 사용 : [ 컨트롤러 : ${handler.method.declaringClass} 메소드 : ${handler.method.name} ] \n 유저 정보 : [ uid : ${firebaseToken.uid}, 이름 : ${firebaseToken.name}, 이메일 : ${firebaseToken.email} ]")
            return true
        }

        if (auth.role == ADMIN) {
            if (checkAdmin(firebaseToken)) {
                log.info("어드민 API 사용 : [ 컨트롤러 : ${handler.method.declaringClass} 메소드 : ${handler.method.name} ] \n 유저 정보 : [ uid : ${firebaseToken.uid}, 이름 : ${firebaseToken.name}, 이메일 : ${firebaseToken.email} ]")
                return true
            }
        }

        throw AuthenticationException()
    }

    companion object {
        private val log: Logger = LogManager.getLogger(this.javaClass.name)
    }
}