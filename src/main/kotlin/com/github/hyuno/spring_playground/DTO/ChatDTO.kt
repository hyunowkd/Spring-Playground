package com.github.hyuno.spring_playground.DTO

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ChatDTO {
}

data class ChatVideoResponseDTO (
    var url : String,
    var thumbnail : String,
    var duration : Int,
)