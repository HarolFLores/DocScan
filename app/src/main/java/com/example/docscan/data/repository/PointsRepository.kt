package com.example.docscan.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.docscan.data.model.TransactionModel
import org.json.JSONArray
import org.json.JSONObject

class PointsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("docscan_points_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_POINTS = "user_points_balance"
        private const val KEY_TRANSACTIONS = "user_points_transactions"
        private const val KEY_INITIAL_RESET_DONE_V2 = "initial_reset_done_v2"
        const val COST_PER_SCAN = 10
        const val REWARD_PER_AD = 25
        private const val DEFAULT_POINTS = 0 // Inicia en 0 en blanco para pruebas
    }

    init {
        // Aseguramos que inicie en blanco la primera vez tras la petición del usuario
        if (!prefs.getBoolean(KEY_INITIAL_RESET_DONE_V2, false)) {
            prefs.edit()
                .putInt(KEY_POINTS, DEFAULT_POINTS)
                .putString(KEY_TRANSACTIONS, "[]")
                .putBoolean(KEY_INITIAL_RESET_DONE_V2, true)
                .apply()
        }
    }

    fun getPoints(): Int {
        return prefs.getInt(KEY_POINTS, DEFAULT_POINTS)
    }

    fun addPoints(amount: Int, reason: String = "Anuncio Recompensado Interstellar completado"): Int {
        val current = getPoints()
        val updated = current + amount
        prefs.edit().putInt(KEY_POINTS, updated).apply()
        addTransaction(
            TransactionModel(
                id = System.currentTimeMillis().toString(),
                title = reason,
                timeAgo = "Justo ahora",
                pointsChange = amount
            )
        )
        return updated
    }

    fun consumePointsForScan(documentName: String): Boolean {
        val current = getPoints()
        if (current >= COST_PER_SCAN) {
            val updated = current - COST_PER_SCAN
            prefs.edit().putInt(KEY_POINTS, updated).apply()
            addTransaction(
                TransactionModel(
                    id = System.currentTimeMillis().toString(),
                    title = "Escaneo PDF: $documentName",
                    timeAgo = "Justo ahora",
                    pointsChange = -COST_PER_SCAN
                )
            )
            return true
        }
        return false
    }

    fun hasEnoughPoints(): Boolean {
        return getPoints() >= COST_PER_SCAN
    }

    fun getTransactions(): List<TransactionModel> {
        val jsonStr = prefs.getString(KEY_TRANSACTIONS, null)
        if (jsonStr.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<TransactionModel>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TransactionModel(
                        id = obj.optString("id", i.toString()),
                        title = obj.getString("title"),
                        timeAgo = obj.getString("timeAgo"),
                        pointsChange = obj.getInt("pointsChange")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun addTransaction(tx: TransactionModel) {
        val currentList = getTransactions().toMutableList()
        currentList.add(0, tx)
        val array = JSONArray()
        for (item in currentList.take(20)) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("timeAgo", item.timeAgo)
                put("pointsChange", item.pointsChange)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_TRANSACTIONS, array.toString()).apply()
    }

    fun clearAllData() {
        prefs.edit()
            .putInt(KEY_POINTS, 0)
            .putString(KEY_TRANSACTIONS, "[]")
            .apply()
    }
}
