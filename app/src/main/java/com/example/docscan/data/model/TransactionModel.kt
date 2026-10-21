package com.example.docscan.data.model

data class TransactionModel(
    val id: String,
    val title: String,
    val timeAgo: String,
    val pointsChange: Int,
    val isPositive: Boolean = pointsChange > 0
)
