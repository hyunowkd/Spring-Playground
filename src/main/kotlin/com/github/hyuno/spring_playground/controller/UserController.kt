package com.github.hyuno.spring_playground.controller

import com.github.hyuno.spring_playground.DTO.UserDTO
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("user")
class UserController {

    private val users = mutableListOf<UserDTO>()
    private var idCounter = 1L

    @PostMapping
    fun postUser(
        @RequestBody data: UserDTO
    ): UserDTO {
        val newUser = data.copy(id = idCounter++)
        users.add(newUser)
        return newUser
    }

    @GetMapping("/{id}")
    fun getUser(
        @PathVariable id: Long
    ): UserDTO? {
        return users.find { it.id == id }
    }

    @PutMapping("/{id}")
    fun putUser(
        @PathVariable id: Long, @RequestBody data: UserDTO
    ): UserDTO? {
        val index = users.indexOfFirst { it.id == id }
        return if (index != -1) {
            val user = data.copy(id = id)
            users[index] = user
            user
        } else null
    }

    @PatchMapping("/{id}")
    fun patchUser(
        @PathVariable id: Long,
        @RequestBody data: Map<String, String>
    ): UserDTO? {
        val user = users.find { it.id == id } ?: return null
        val updated = user.copy(
            name = data["name"] ?: user.name,
            email = data["email"] ?: user.email
        )
        users[users.indexOf(user)] = updated
        return updated
    }

    @DeleteMapping("/{id}")
    fun deleteUser(
        @PathVariable id: Long
    ): Boolean {
        return users.removeIf { it.id == id }
    }

}