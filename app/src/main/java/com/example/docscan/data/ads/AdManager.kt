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
        // ID proporcionado en el requerimiento
        const val REAL_REWARDED_AD_UNIT_ID = "ca-app-pub-1475738174229393/1201217517"
        // ID de prueba oficial de Google para Rewarded Ads
        private const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }

    private var rewardedAd: RewardedAd? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAdReady = MutableStateFlow(false)
    val isAdReady: StateFlow<Boolean> = _isAdReady.asStateFlow()

    init {
        MobileAds.initialize(context) { initializationStatus ->
            Log.d(TAG, "MobileAds initialized: ${initializationStatus.adapterStatusMap}")
            loadRewardedAd()
        }
    }

    fun loadRewardedAd(useTestAdIfRealFails: Boolean = true) {
        if (_isLoading.value || rewardedAd != null) return

        _isLoading.value = true
        _isAdReady.value = false

        val adRequest = AdRequest.Builder().build()

        Log.d(TAG, "Intentando cargar anuncio recompensado real: $REAL_REWARDED_AD_UNIT_ID")

        RewardedAd.load(
            context,
            REAL_REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Anuncio Interstellar cargado con éxito.")
                    rewardedAd = ad
                    _isLoading.value = false
                    _isAdReady.value = true
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Error al cargar anuncio real: ${loadAdError.message}")
                    rewardedAd = null

                    if (useTestAdIfRealFails) {
                        Log.d(TAG, "Intentando cargar anuncio de prueba oficial...")
                        RewardedAd.load(
                            context,
                            TEST_REWARDED_AD_UNIT_ID,
                            adRequest,
                            object : RewardedAdLoadCallback() {
                                override fun onAdLoaded(testAd: RewardedAd) {
                                    Log.d(TAG, "Anuncio de prueba cargado con éxito.")
                                    rewardedAd = testAd
                                    _isLoading.value = false
                                    _isAdReady.value = true
                                }

                                override fun onAdFailedToLoad(testError: LoadAdError) {
                                    Log.e(TAG, "Error al cargar anuncio de prueba: ${testError.message}")
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
            onError("El anuncio no está listo aún. Inténtalo de nuevo en unos momentos.")
            loadRewardedAd()
            return
        }

        var rewardGranted = false

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Anuncio se está mostrando en pantalla completa.")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Error al mostrar anuncio: ${adError.message}")
                rewardedAd = null
                _isAdReady.value = false
                loadRewardedAd()
                onError("No se pudo mostrar el anuncio: ${adError.message}")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "El anuncio fue cerrado por el usuario.")
                rewardedAd = null
                _isAdReady.value = false
                loadRewardedAd() // Pre-cargar el siguiente anuncio
                onAdDismissed()
            }
        }

        currentAd.show(activity) { rewardItem ->
            val rewardAmount = if (rewardItem.amount > 0) rewardItem.amount else 5
            rewardGranted = true
            Log.d(TAG, "Usuario recompensado con $rewardAmount puntos.")
            onRewardEarned(rewardAmount)
        }
    }
}
