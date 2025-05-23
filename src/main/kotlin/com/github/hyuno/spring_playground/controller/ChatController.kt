package com.github.hyuno.spring_playground.controller

import com.github.hyuno.spring_playground.domain.ChatMessage
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.web.bind.annotation.RestController

@RestController
class ChatController {
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    fun sendMessage(@Payload chatMessage: ChatMessage?): ChatMessage? {
        println("Received message: $chatMessage")
        return chatMessage
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    fun addUser(@Payload chatMessage: ChatMessage, headerAccessor: SimpMessageHeaderAccessor): ChatMessage? {
        headerAccessor.sessionAttributes!!["username"] = chatMessage.sender
        return chatMessage
    }
}