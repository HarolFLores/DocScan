package com.example.docscan.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscan.data.ads.AdManager
import com.example.docscan.data.model.DocumentModel
import com.example.docscan.data.repository.PdfRepository
import com.example.docscan.data.repository.PointsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val pointsRepository = PointsRepository(application)
    private val pdfRepository = PdfRepository(application)
    private val adManager = AdManager(application)

    private val _points = MutableStateFlow(0)
    val points: StateFlow<Int> = _points.asStateFlow()

    private val _savedDocuments = MutableStateFlow<List<DocumentModel>>(emptyList())
    val savedDocuments: StateFlow<List<DocumentModel>> = _savedDocuments.asStateFlow()

    private val _capturedImages = MutableStateFlow<List<Uri>>(emptyList())
    val capturedImages: StateFlow<List<Uri>> = _capturedImages.asStateFlow()

    private val _scanMode = MutableStateFlow("SINGLE") // "SINGLE" o "BATCH"
    val scanMode: StateFlow<String> = _scanMode.asStateFlow()

    private val _isCameraOpen = MutableStateFlow(false)
    val isCameraOpen: StateFlow<Boolean> = _isCameraOpen.asStateFlow()

    private val _isPreviewOpen = MutableStateFlow(false)
    val isPreviewOpen: StateFlow<Boolean> = _isPreviewOpen.asStateFlow()

    private val _isGeneratingPdf = MutableStateFlow(false)
    val isGeneratingPdf: StateFlow<Boolean> = _isGeneratingPdf.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val isAdLoading: StateFlow<Boolean> = adManager.isLoading
    val isAdReady: StateFlow<Boolean> = adManager.isAdReady

    init {
        loadData()
    }

    fun loadData() {
        _points.value = pointsRepository.getPoints()
        refreshSavedDocuments()
    }

    fun refreshSavedDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            val docs = pdfRepository.getSavedDocuments()
            _savedDocuments.value = docs
        }
    }

    /**
     * Restringe el inicio del escaneo si no hay puntos suficientes.
     */
    fun startScan(mode: String): Boolean {
        if (!pointsRepository.hasEnoughPoints()) {
            _userMessage.value = "⚠️ Puntos insuficientes. Ve un anuncio para ganar puntos y poder escanear."
            return false
        }
        _scanMode.value = mode
        _capturedImages.value = emptyList()
        _isCameraOpen.value = true
        return true
    }

    fun closeCamera() {
        _isCameraOpen.value = false
    }

    fun onImageCaptured(uri: Uri) {
        val currentList = _capturedImages.value.toMutableList()
        currentList.add(uri)
        _capturedImages.value = currentList

        if (_scanMode.value == "SINGLE") {
            _isCameraOpen.value = false
            _isPreviewOpen.value = true
        }
    }

    fun removeCapturedImage(index: Int) {
        val currentList = _capturedImages.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _capturedImages.value = currentList
        }
    }

    fun clearCapturedImages() {
        _capturedImages.value = emptyList()
    }

    fun openPreview() {
        if (_capturedImages.value.isNotEmpty()) {
            _isCameraOpen.value = false
            _isPreviewOpen.value = true
        } else {
            _userMessage.value = "No has capturado ninguna imagen aún."
        }
    }

    fun closePreview() {
        _isPreviewOpen.value = false
    }

    fun generatePdf(docName: String) {
        if (_capturedImages.value.isEmpty()) {
            _userMessage.value = "No hay imágenes para generar el PDF."
            return
        }

        if (!pointsRepository.hasEnoughPoints()) {
            _userMessage.value = "⚠️ Puntos insuficientes para generar el PDF. Ve un anuncio."
            return
        }

        _isGeneratingPdf.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val result = pdfRepository.createPdfFromUris(_capturedImages.value, docName)

            withContext(Dispatchers.Main) {
                _isGeneratingPdf.value = false
                result.fold(
                    onSuccess = { doc ->
                        // Consumir 1 punto
                        pointsRepository.consumePoint()
                        _points.value = pointsRepository.getPoints()

                        _isPreviewOpen.value = false
                        _capturedImages.value = emptyList()

                        refreshSavedDocuments()
                        _userMessage.value = "✅ ¡PDF '${doc.name}' creado y guardado con éxito! Se consumió 1 punto."
                    },
                    onFailure = { error ->
                        _userMessage.value = "❌ Error al crear el PDF: ${error.localizedMessage}"
                    }
                )
            }
        }
    }

    fun deleteDocument(doc: DocumentModel) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = pdfRepository.deleteDocument(doc)
            withContext(Dispatchers.Main) {
                if (deleted) {
                    _userMessage.value = "Documento '${doc.name}' eliminado."
                    refreshSavedDocuments()
                } else {
                    _userMessage.value = "No se pudo eliminar el documento."
                }
            }
        }
    }

    fun shareDocument(doc: DocumentModel) {
        pdfRepository.shareDocument(doc)
    }

    fun openDocument(doc: DocumentModel) {
        pdfRepository.openDocument(doc)
    }

    fun watchRewardedAd(activity: Activity) {
        adManager.showRewardedAd(
            activity = activity,
            onRewardEarned = { amount ->
                val newTotal = pointsRepository.addPoints(amount)
                _points.value = newTotal
                _userMessage.value = "🎉 ¡Felicidades! Visualizaste el anuncio Interstellar completo. ¡Ganaste +$amount puntos!"
            },
            onAdDismissed = {
                // Anuncio cerrado
            },
            onError = { errorMessage ->
                _userMessage.value = errorMessage
            }
        )
    }

    fun loadAd() {
        adManager.loadRewardedAd()
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
