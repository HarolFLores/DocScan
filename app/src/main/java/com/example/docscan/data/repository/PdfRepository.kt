package com.example.docscan.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import android.media.ExifInterface
import com.example.docscan.data.model.DocumentModel
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfRepository(private val context: Context) {

    private val documentsDir: File
        get() {
            val dir = File(context.filesDir, "documents")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun getSavedDocuments(): List<DocumentModel> {
        val files = documentsDir.listFiles { file -> file.extension.equals("pdf", ignoreCase = true) }
            ?: return emptyList()

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        return files.map { file ->
            val dateStr = dateFormat.format(Date(file.lastModified()))
            val sizeKb = file.length() / 1024
            DocumentModel(
                id = file.name,
                name = file.nameWithoutExtension,
                filePath = file.absolutePath,
                dateFormatted = dateStr,
                sizeInKb = sizeKb,
                pageCount = estimatePageCount(file)
            )
        }.sortedByDescending { File(it.filePath).lastModified() }
    }

    fun createPdfFromUris(imageUris: List<Uri>, customName: String): Result<DocumentModel> {
        return try {
            if (imageUris.isEmpty()) {
                return Result.failure(IllegalArgumentException("No hay imágenes para convertir."))
            }

            val sanitizedName = if (customName.isBlank()) {
                "Doc_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
            } else {
                customName.trim().replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
            }

            val pdfFile = File(documentsDir, "$sanitizedName.pdf")
            val pdfDocument = PdfDocument()

            // Standard A4 portrait size at 72 DPI approx: 595 x 842 points
            val pdfWidth = 595
            val pdfHeight = 842

            imageUris.forEachIndexed { index, uri ->
                val bitmap = loadAndCorrectBitmap(uri) ?: return@forEachIndexed

                val pageInfo = PdfDocument.PageInfo.Builder(pdfWidth, pdfHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Scale bitmap to fit within PDF page margins
                val scale = Math.min(
                    pdfWidth.toFloat() / bitmap.width,
                    pdfHeight.toFloat() / bitmap.height
                )
                val scaledWidth = bitmap.width * scale
                val scaledHeight = bitmap.height * scale
                val left = (pdfWidth - scaledWidth) / 2f
                val top = (pdfHeight - scaledHeight) / 2f

                val matrix = Matrix()
                matrix.postScale(scale, scale)
                matrix.postTranslate(left, top)

                canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                pdfDocument.finishPage(page)
                bitmap.recycle()
            }

            val outputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()

            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val doc = DocumentModel(
                id = pdfFile.name,
                name = pdfFile.nameWithoutExtension,
                filePath = pdfFile.absolutePath,
                dateFormatted = dateFormat.format(Date(pdfFile.lastModified())),
                sizeInKb = pdfFile.length() / 1024,
                pageCount = imageUris.size
            )

            Result.success(doc)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
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

    private fun loadAndCorrectBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate sample size to prevent OOM
            var inSampleSize = 1
            val maxDimension = 1920
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

            // Read orientation from EXIF if possible
            val is3: InputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(is3)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            is3.close()

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            if (rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
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
