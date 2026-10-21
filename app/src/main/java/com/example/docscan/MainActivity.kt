package com.example.docscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.docscan.ui.screens.CameraScanScreen
import com.example.docscan.ui.screens.MainHomeScreen
import com.example.docscan.ui.screens.PdfPreviewDialog
import com.example.docscan.ui.theme.DocScanTheme
import com.example.docscan.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DocScanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DocScanApp()
                }
            }
        }
    }
}

@Composable
fun DocScanApp(
    viewModel: MainViewModel = viewModel()
) {
    val isCameraOpen by viewModel.isCameraOpen.collectAsState()
    val isPreviewOpen by viewModel.isPreviewOpen.collectAsState()
    val capturedImages by viewModel.capturedImages.collectAsState()
    val scanMode by viewModel.scanMode.collectAsState()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsState()
    val points by viewModel.points.collectAsState()

    if (isCameraOpen) {
        CameraScanScreen(
            scanMode = scanMode,
            capturedCount = capturedImages.size,
            onImageCaptured = { uri ->
                viewModel.onImageCaptured(uri)
            },
            onFinishScan = {
                viewModel.openPreview()
            },
            onClose = {
                viewModel.closeCamera()
            }
        )
    } else {
        MainHomeScreen(
            viewModel = viewModel,
            onStartScan = { mode ->
                viewModel.startScan(mode)
            }
        )
    }

    if (isPreviewOpen) {
        PdfPreviewDialog(
            capturedImages = capturedImages,
            isGenerating = isGeneratingPdf,
            currentPoints = points,
            onRemoveImage = { index ->
                viewModel.removeCapturedImage(index)
            },
            onGeneratePdf = { docName ->
                viewModel.generatePdf(docName)
            },
            onDismiss = {
                viewModel.closePreview()
            }
        )
    }
}
