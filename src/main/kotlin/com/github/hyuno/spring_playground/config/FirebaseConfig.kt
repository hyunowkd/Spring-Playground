package com.github.hyuno.spring_playground.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct


@Service
@Configuration
class FirebaseConfig {
    @Value("\${firebasePath}")
    private val firebaseFilePath: String? = null

    @PostConstruct
    fun init() {
        try {
            val stream =
                Thread.currentThread().contextClassLoader.getResourceAsStream(firebaseFilePath)
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build()

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}