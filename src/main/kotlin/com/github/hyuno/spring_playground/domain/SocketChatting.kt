package com.github.hyuno.spring_playground.domain

import com.github.hyuno.spring_playground.domain.MessageType

data class ChatMessage (
    var type: MessageType,
    var content: String?,
    var sender: String
)