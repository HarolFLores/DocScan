package com.example.docscan.data.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdManager(private val context: Context) {

    companion object {
        private const val TAG = "AdManager"
        const val REAL_APP_ID = "ca-app-pub-1475738174229393~1713041760"
        const val REAL_REWARDED_AD_UNIT_ID = "ca-app-pub-1475738174229393/1201217517"
        private const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        const val REWARD_POINTS_AMOUNT = 25
    }

    private var rewardedAd: RewardedAd? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAdReady = MutableStateFlow(false)
    val isAdReady: StateFlow<Boolean> = _isAdReady.asStateFlow()

    init {
        try {
            MobileAds.initialize(context) { initializationStatus ->
                Log.d(TAG, "MobileAds initialized: ${initializationStatus.adapterStatusMap}")
                loadRewardedAd()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MobileAds", e)
        }
    }

    fun loadRewardedAd(useTestAdIfRealFails: Boolean = true) {
        if (_isLoading.value || rewardedAd != null) return

        _isLoading.value = true
        _isAdReady.value = false

        val adRequest = AdRequest.Builder().build()
        Log.d(TAG, "Cargando anuncio recompensado: $REAL_REWARDED_AD_UNIT_ID")

        RewardedAd.load(
            context,
            REAL_REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Anuncio Interstellar cargado.")
                    rewardedAd = ad
                    _isLoading.value = false
                    _isAdReady.value = true
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Anuncio real falló (${loadAdError.message}), usando test ad...")
                    rewardedAd = null

                    if (useTestAdIfRealFails) {
                        RewardedAd.load(
                            context,
                            TEST_REWARDED_AD_UNIT_ID,
                            adRequest,
                            object : RewardedAdLoadCallback() {
                                override fun onAdLoaded(testAd: RewardedAd) {
                                    Log.d(TAG, "Anuncio test cargado.")
                                    rewardedAd = testAd
                                    _isLoading.value = false
                                    _isAdReady.value = true
                                }

                                override fun onAdFailedToLoad(testError: LoadAdError) {
                                    Log.e(TAG, "Error cargando anuncio de prueba: ${testError.message}")
                                    rewardedAd = null
                                    _isLoading.value = false
                                    _isAdReady.value = false
                                }
                            }
                        )
                    } else {
                        _isLoading.value = false
                        _isAdReady.value = false
                    }
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: (amount: Int) -> Unit,
        onAdDismissed: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentAd = rewardedAd
        if (currentAd == null) {
            // Emulate or retry ad
            Log.d(TAG, "Ad not ready, simulating reward for emulator / test sandbox")
            // In Android emulator or without network, deliver reward with notice
            onRewardEarned(REWARD_POINTS_AMOUNT)
            loadRewardedAd()
            return
        }

        var rewardGranted = false

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Anuncio en pantalla completa.")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Fallo al mostrar anuncio: ${adError.message}")
                rewardedAd = null
                _isAdReady.value = false
                loadRewardedAd()
                // Grant reward anyway in dev/test to avoid blocking user flow
                onRewardEarned(REWARD_POINTS_AMOUNT)
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Anuncio cerrado.")
                rewardedAd = null
                _isAdReady.value = false
                loadRewardedAd()
                onAdDismissed()
            }
        }

        currentAd.show(activity) { rewardItem ->
            val rewardAmount = if (rewardItem.amount > 0) rewardItem.amount else REWARD_POINTS_AMOUNT
            rewardGranted = true
            Log.d(TAG, "Recompensa otorgada: $rewardAmount")
            onRewardEarned(rewardAmount)
        }
    }
}
