package com.github.hyuno.spring_playground.listener

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import com.github.hyuno.spring_playground.domain.ChatMessage
import com.github.hyuno.spring_playground.domain.MessageType
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessageSendingOperations
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent

@Component
class WebSocketEventListener(
    private val messagingTemplate: SimpMessageSendingOperations?
) {

    @EventListener
    fun handleWebSocketConnectListener(event: SessionConnectedEvent?) {
        log.info("Received a new web socket connection")
    }

    @EventListener
    fun handleWebSocketDisconnectListener(event: SessionDisconnectEvent) {
        val headerAccessor = StompHeaderAccessor.wrap(event.message)
        val username = headerAccessor.sessionAttributes!!["username"] as String?
        if (username != null) {
            log.info("User Disconnected : $username")
            val chatMessage = ChatMessage(MessageType.LEAVE, "", username)

            messagingTemplate!!.convertAndSend("/topic/public", chatMessage)
        }
    }

    companion object {
        private val log: Logger = LogManager.getLogger(Companion::class.java.name)
    }
}