package com.example.docscan.data.repository

import android.content.Context
import android.content.SharedPreferences

class PointsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("docscan_points_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_POINTS = "user_points_balance"
        private const val DEFAULT_POINTS = 0 // Inicia con 0 puntos según requerimiento
    }

    fun getPoints(): Int {
        return prefs.getInt(KEY_POINTS, DEFAULT_POINTS)
    }

    fun addPoints(amount: Int): Int {
        val current = getPoints()
        val updated = current + amount
        prefs.edit().putInt(KEY_POINTS, updated).apply()
        return updated
    }

    fun consumePoint(): Boolean {
        val current = getPoints()
        if (current > 0) {
            prefs.edit().putInt(KEY_POINTS, current - 1).apply()
            return true
        }
        return false
    }

    fun hasEnoughPoints(): Boolean {
        return getPoints() > 0
    }
}
