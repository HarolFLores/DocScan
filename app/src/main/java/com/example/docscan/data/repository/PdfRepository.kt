package com.example.docscan.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.docscan.data.model.DocumentModel
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfRepository(private val context: Context) {

    private val internalDocumentsDir: File
        get() {
            val dir = File(context.filesDir, "DocScanPDFs")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    private val publicDownloadsDir: File
        get() {
            val pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val docScanDir = File(pubDir, "DocScan")
            if (!docScanDir.exists()) {
                docScanDir.mkdirs()
            }
            return if (docScanDir.exists() && docScanDir.canWrite()) docScanDir else internalDocumentsDir
        }

    init {
        // Limpiamos documentos previos para que inicie completamente en blanco según lo solicitado
        clearAllSampleDocuments()
    }

    fun clearAllSampleDocuments() {
        try {
            internalDocumentsDir.listFiles()?.forEach { file ->
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSavedDocuments(): List<DocumentModel> {
        val files = internalDocumentsDir.listFiles { file -> file.extension.equals("pdf", ignoreCase = true) }
            ?: return emptyList()

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        return files.map { file ->
            val dateStr = dateFormat.format(Date(file.lastModified()))
            val sizeKb = (file.length() / 1024).coerceAtLeast(1)
            val isContract = file.name.contains("Contrato", ignoreCase = true)
            val isBill = file.name.contains("Boleta", ignoreCase = true) || file.name.contains("Factura", ignoreCase = true)

            val category = when {
                isBill -> "Facturas"
                isContract -> "Notas"
                else -> "Recientes"
            }

            DocumentModel(
                id = file.name,
                name = file.name,
                filePath = file.absolutePath,
                dateFormatted = dateStr,
                sizeInKb = sizeKb,
                pageCount = estimatePageCount(file),
                category = category,
                isLocked = isContract,
                isVerified = true
            )
        }.sortedByDescending { File(it.filePath).lastModified() }
    }

    fun createPdfWithAdvancedOptions(
        imageUris: List<Uri>,
        customName: String,
        rotations: Map<Int, Int>, // page index -> rotation degrees
        filterMode: String, // "Original", "B&W Alto Contraste", "Escala de Grises"
        quality: String, // "Alta", "Media", "Baja"
        saveToPublicDownloads: Boolean
    ): Result<DocumentModel> {
        return try {
            if (imageUris.isEmpty()) {
                return Result.failure(IllegalArgumentException("No hay imágenes para convertir."))
            }

            var sanitizedName = if (customName.isBlank()) {
                "DocuScan_${SimpleDateFormat("yyyy_MM_dd_HHmm", Locale.getDefault()).format(Date())}"
            } else {
                customName.trim().removeSuffix(".pdf").replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
            }

            if (!sanitizedName.endsWith(".pdf", ignoreCase = true)) {
                sanitizedName += ".pdf"
            }

            val targetDir = if (saveToPublicDownloads) publicDownloadsDir else internalDocumentsDir
            val pdfFile = File(targetDir, sanitizedName)
            val pdfDocument = PdfDocument()

            // Standard A4 portrait: 595 x 842 points
            val pdfWidth = 595
            val pdfHeight = 842

            val maxDimension = when (quality) {
                "Baja" -> 1080
                "Media" -> 1600
                else -> 2048
            }

            imageUris.forEachIndexed { index, uri ->
                val rotation = rotations[index] ?: 0
                val baseBitmap = loadAndCorrectBitmap(uri, maxDimension, rotation) ?: return@forEachIndexed
                val filteredBitmap = applyFilter(baseBitmap, filterMode)

                val pageInfo = PdfDocument.PageInfo.Builder(pdfWidth, pdfHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val scale = Math.min(
                    pdfWidth.toFloat() / filteredBitmap.width,
                    pdfHeight.toFloat() / filteredBitmap.height
                )
                val scaledWidth = filteredBitmap.width * scale
                val scaledHeight = filteredBitmap.height * scale
                val left = (pdfWidth - scaledWidth) / 2f
                val top = (pdfHeight - scaledHeight) / 2f

                val matrix = Matrix()
                matrix.postScale(scale, scale)
                matrix.postTranslate(left, top)

                canvas.drawBitmap(filteredBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                pdfDocument.finishPage(page)

                if (filteredBitmap != baseBitmap) {
                    filteredBitmap.recycle()
                }
                baseBitmap.recycle()
            }

            val outputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()

            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val doc = DocumentModel(
                id = pdfFile.name,
                name = pdfFile.name,
                filePath = pdfFile.absolutePath,
                dateFormatted = dateFormat.format(Date(pdfFile.lastModified())),
                sizeInKb = (pdfFile.length() / 1024).coerceAtLeast(1),
                pageCount = imageUris.size,
                category = "Recientes",
                isLocked = false,
                isVerified = true
            )

            Result.success(doc)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun applyFilter(src: Bitmap, filterMode: String): Bitmap {
        return when (filterMode) {
            "B&W Alto Contraste" -> {
                val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val paint = Paint()
                val cm = ColorMatrix().apply {
                    setSaturation(0f)
                    val contrast = 1.6f
                    val scale = contrast
                    val translate = (-0.5f * contrast + 0.5f) * 255f
                    val array = floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                    postConcat(ColorMatrix(array))
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
                bmp
            }
            "Escala de Grises", "Escala de grises" -> {
                val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val paint = Paint()
                val cm = ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
                bmp
            }
            else -> src
        }
    }

    private fun loadAndCorrectBitmap(uri: Uri, maxDimension: Int, manualRotation: Int): Bitmap? {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            var inSampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }

            val is2: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            var bitmap = BitmapFactory.decodeStream(is2, null, decodeOptions)
            is2.close()

            if (bitmap == null) return null

            var rotationDegrees = 0
            try {
                val is3: InputStream? = context.contentResolver.openInputStream(uri)
                if (is3 != null) {
                    val exif = ExifInterface(is3)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    is3.close()
                    rotationDegrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (e: Exception) {
                // ignore
            }

            val totalRotation = (rotationDegrees + manualRotation) % 360
            if (totalRotation != 0) {
                val matrix = Matrix()
                matrix.postRotate(totalRotation.toFloat())
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                bitmap.recycle()
                bitmap = rotated
            }

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteDocument(document: DocumentModel): Boolean {
        val file = File(document.filePath)
        return if (file.exists()) {
            file.delete()
        } else false
    }

    fun shareDocument(document: DocumentModel) {
        val file = File(document.filePath)
        if (!file.exists()) return

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, document.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Compartir PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun openDocument(document: DocumentModel) {
        val file = File(document.filePath)
        if (!file.exists()) return

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Abrir PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun estimatePageCount(file: File): Int {
        return try {
            val parcelFileDescriptor = android.os.ParcelFileDescriptor.open(
                file,
                android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            val pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor)
            val count = pdfRenderer.pageCount
            pdfRenderer.close()
            parcelFileDescriptor.close()
            count
        } catch (e: Exception) {
            1
        }
    }
}
