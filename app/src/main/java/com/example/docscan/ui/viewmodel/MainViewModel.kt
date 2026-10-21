package com.example.docscan.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscan.data.ads.AdManager
import com.example.docscan.data.model.DocumentModel
import com.example.docscan.data.model.TransactionModel
import com.example.docscan.data.repository.PdfRepository
import com.example.docscan.data.repository.PointsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val pointsRepository = PointsRepository(application)
    private val pdfRepository = PdfRepository(application)
    private val adManager = AdManager(application)

    // Current Bottom Tab: "DOCUMENTOS", "ESCANEAR", "RECOMPENSAS", "AJUSTES"
    private val _currentTab = MutableStateFlow("DOCUMENTOS")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    // Filter Chips in Mis Documentos: "Todos", "Recientes", "Facturas", "Notas"
    private val _selectedCategory = MutableStateFlow("Todos")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _points = MutableStateFlow(pointsRepository.getPoints())
    val points: StateFlow<Int> = _points.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionModel>>(pointsRepository.getTransactions())
    val transactions: StateFlow<List<TransactionModel>> = _transactions.asStateFlow()

    private val _savedDocuments = MutableStateFlow<List<DocumentModel>>(emptyList())
    val savedDocuments: StateFlow<List<DocumentModel>> = _savedDocuments.asStateFlow()

    // Camera Scan State
    private val _isCameraOpen = MutableStateFlow(false)
    val isCameraOpen: StateFlow<Boolean> = _isCameraOpen.asStateFlow()

    private val _scanMode = MutableStateFlow("BATCH") // "SINGLE" o "BATCH"
    val scanMode: StateFlow<String> = _scanMode.asStateFlow()

    private val _capturedImages = MutableStateFlow<List<Uri>>(emptyList())
    val capturedImages: StateFlow<List<Uri>> = _capturedImages.asStateFlow()

    private val _flashMode = MutableStateFlow("AUTO") // "AUTO", "ON", "OFF"
    val flashMode: StateFlow<String> = _flashMode.asStateFlow()

    private val _gridEnabled = MutableStateFlow(true)
    val gridEnabled: StateFlow<Boolean> = _gridEnabled.asStateFlow()

    private val _showCanjeBanner = MutableStateFlow(true)
    val showCanjeBanner: StateFlow<Boolean> = _showCanjeBanner.asStateFlow()

    // PDF Detail Screen State (Image 4)
    private val _isDetailOpen = MutableStateFlow(false)
    val isDetailOpen: StateFlow<Boolean> = _isDetailOpen.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _pageRotations = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val pageRotations: StateFlow<Map<Int, Int>> = _pageRotations.asStateFlow()

    private val _selectedFilter = MutableStateFlow("Original") // "Original", "B&W Alto Contraste", "Escala de Grises"
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _selectedQuality = MutableStateFlow("Media") // "Alta", "Media", "Baja"
    val selectedQuality: StateFlow<String> = _selectedQuality.asStateFlow()

    private val _saveToPublicDownloads = MutableStateFlow(true)
    val saveToPublicDownloads: StateFlow<Boolean> = _saveToPublicDownloads.asStateFlow()

    private val _documentNameInput = MutableStateFlow("DocuScan_${SimpleDateFormat("yyyy_MM_dd_HHmm", Locale.getDefault()).format(Date())}.pdf")
    val documentNameInput: StateFlow<String> = _documentNameInput.asStateFlow()

    private val _isGeneratingPdf = MutableStateFlow(false)
    val isGeneratingPdf: StateFlow<Boolean> = _isGeneratingPdf.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val isAdLoading: StateFlow<Boolean> = adManager.isLoading
    val isAdReady: StateFlow<Boolean> = adManager.isAdReady

    init {
        refreshData()
    }

    fun refreshData() {
        _points.value = pointsRepository.getPoints()
        _transactions.value = pointsRepository.getTransactions()
        viewModelScope.launch(Dispatchers.IO) {
            val docs = pdfRepository.getSavedDocuments()
            _savedDocuments.value = docs
        }
    }

    fun resetDataForTesting() {
        pointsRepository.clearAllData()
        pdfRepository.clearAllSampleDocuments()
        refreshData()
        _userMessage.value = "Todo ha sido reiniciado en blanco para pruebas."
    }

    fun setCurrentTab(tab: String) {
        if (tab == "ESCANEAR") {
            startScan("BATCH")
        } else {
            _currentTab.value = tab
            _isCameraOpen.value = false
            _isDetailOpen.value = false
        }
    }

    fun setSelectedCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun startScan(mode: String = "BATCH"): Boolean {
        if (!pointsRepository.hasEnoughPoints()) {
            _userMessage.value = "⚠️ Puntos insuficientes. Ve un anuncio para ganar +25 puntos y habilitar el escáner."
            _currentTab.value = "RECOMPENSAS"
            return false
        }
        _scanMode.value = mode
        _capturedImages.value = emptyList()
        _pageRotations.value = emptyMap()
        _currentPageIndex.value = 0
        _isCameraOpen.value = true
        _isDetailOpen.value = false
        _documentNameInput.value = "DocuScan_${SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())}_Practica.pdf"
        return true
    }

    fun setScanMode(mode: String) {
        _scanMode.value = mode
    }

    fun toggleFlash() {
        _flashMode.value = when (_flashMode.value) {
            "AUTO" -> "ON"
            "ON" -> "OFF"
            else -> "AUTO"
        }
    }

    fun toggleGrid() {
        _gridEnabled.value = !_gridEnabled.value
    }

    fun dismissCanjeBanner() {
        _showCanjeBanner.value = false
    }

    fun closeCamera() {
        _isCameraOpen.value = false
        _currentTab.value = "DOCUMENTOS"
    }

    fun onImageCaptured(uri: Uri) {
        val currentList = _capturedImages.value.toMutableList()
        currentList.add(uri)
        _capturedImages.value = currentList

        if (_scanMode.value == "SINGLE") {
            _isCameraOpen.value = false
            _isDetailOpen.value = true
        }
    }

    fun addGalleryImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val currentList = _capturedImages.value.toMutableList()
        currentList.addAll(uris)
        _capturedImages.value = currentList

        if (_scanMode.value == "SINGLE") {
            _isCameraOpen.value = false
            _isDetailOpen.value = true
        }
    }

    fun removeCapturedImage(index: Int) {
        val currentList = _capturedImages.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _capturedImages.value = currentList
            val newIdx = _currentPageIndex.value.coerceAtMost((currentList.size - 1).coerceAtLeast(0))
            _currentPageIndex.value = newIdx
        }
    }

    fun openDetailScreen() {
        if (_capturedImages.value.isNotEmpty()) {
            _isCameraOpen.value = false
            _isDetailOpen.value = true
        } else {
            _userMessage.value = "Captura al menos una página para continuar."
        }
    }

    fun closeDetailScreen() {
        _isDetailOpen.value = false
        if (_capturedImages.value.isNotEmpty()) {
            _isCameraOpen.value = true
        } else {
            _currentTab.value = "DOCUMENTOS"
        }
    }

    fun setCurrentPageIndex(index: Int) {
        val size = _capturedImages.value.size
        if (size > 0 && index in 0 until size) {
            _currentPageIndex.value = index
        }
    }

    fun rotateCurrentPage() {
        val idx = _currentPageIndex.value
        val map = _pageRotations.value.toMutableMap()
        val currentRot = map[idx] ?: 0
        map[idx] = (currentRot + 90) % 360
        _pageRotations.value = map
    }

    fun setSelectedFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun setSelectedQuality(q: String) {
        _selectedQuality.value = q
    }

    fun setSaveToPublicDownloads(publicDir: Boolean) {
        _saveToPublicDownloads.value = publicDir
    }

    fun setDocumentNameInput(name: String) {
        _documentNameInput.value = name
    }

    fun generatePdfWithSettings() {
        if (_capturedImages.value.isEmpty()) {
            _userMessage.value = "No hay imágenes para generar el PDF."
            return
        }

        if (!pointsRepository.hasEnoughPoints()) {
            _userMessage.value = "⚠️ Puntos insuficientes. Ve a Recompensas y mira un anuncio para +25 puntos."
            return
        }

        _isGeneratingPdf.value = true

        val docName = _documentNameInput.value
        val rotations = _pageRotations.value
        val filter = _selectedFilter.value
        val quality = _selectedQuality.value
        val toPublic = _saveToPublicDownloads.value

        viewModelScope.launch(Dispatchers.IO) {
            val result = pdfRepository.createPdfWithAdvancedOptions(
                imageUris = _capturedImages.value,
                customName = docName,
                rotations = rotations,
                filterMode = filter,
                quality = quality,
                saveToPublicDownloads = toPublic
            )

            withContext(Dispatchers.Main) {
                _isGeneratingPdf.value = false
                result.fold(
                    onSuccess = { doc ->
                        // Consume 10 points
                        pointsRepository.consumePointsForScan(doc.name)
                        _points.value = pointsRepository.getPoints()
                        _transactions.value = pointsRepository.getTransactions()

                        _isDetailOpen.value = false
                        _isCameraOpen.value = false
                        _capturedImages.value = emptyList()
                        _currentTab.value = "DOCUMENTOS"

                        refreshData()
                        _userMessage.value = "✅ ¡PDF '${doc.name}' guardado con éxito! Se descontaron 10 pts."
                    },
                    onFailure = { error ->
                        _userMessage.value = "❌ Error al generar el PDF: ${error.localizedMessage}"
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
                    refreshData()
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
                _transactions.value = pointsRepository.getTransactions()
                _userMessage.value = "🎉 ¡Ganaste +$amount puntos por ver el anuncio Interstellar!"
            },
            onAdDismissed = {
                // Done
            },
            onError = { errorMessage ->
                _userMessage.value = errorMessage
            }
        )
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
