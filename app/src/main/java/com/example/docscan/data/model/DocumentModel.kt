package com.example.docscan.data.model

data class DocumentModel(
    val id: String,
    val name: String,
    val filePath: String,
    val dateFormatted: String,
    val sizeInKb: Long,
    val pageCount: Int,
    val category: String = "Recientes",
    val isLocked: Boolean = false,
    val isVerified: Boolean = true
)
