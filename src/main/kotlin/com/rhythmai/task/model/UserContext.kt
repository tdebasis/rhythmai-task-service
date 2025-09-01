package com.rhythmai.task.model

data class UserContext(
    val userId: String,
    val email: String,
    val name: String,
    val profilePicture: String? = null
)