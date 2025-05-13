package com.github.hyuno.spring_playground.service

import com.google.firebase.database.*
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Service
class ChatService {

    suspend fun createChatRoom(uid : String) : String? {

        val userRef = FirebaseDatabase.getInstance("/")

        // DatabaseReference는 내용 없이 url만 가져옴
        val ref: DatabaseReference = userRef.getReference("/")

        val roomId: String? = suspendCoroutine {
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.value == null) {
                        it.resume(null)
                    } else {
                        it.resume(dataSnapshot.value.toString())
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {}
            })
        }

        return roomId

    }

    fun postChat(uid : String) : String? {

        val chatRoom = runBlocking { createChatRoom(uid) }

        return chatRoom

    }

}
