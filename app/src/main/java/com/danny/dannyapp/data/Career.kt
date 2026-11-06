package com.danny.dannyapp.data

data class Career(
    val id: String,
    val name: String,
    val level: Int,
    val createdAt: Long = System.currentTimeMillis()
)
