package ir.tajeritools.healthai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object OcrEngine {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractFromBitmap(bitmap: Bitmap): String =
        recognize(InputImage.fromBitmap(bitmap, 0))

    suspend fun extractFromUri(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        return if (mime == "application/pdf") extractPdf(context, uri)
        else {
            val bmp = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "فایل قابل خواندن نیست" }
                    BitmapFactory.decodeStream(input)
                }
            }
            extractFromBitmap(requireNotNull(bmp) { "تصویر قابل خواندن نیست" })
        }
    }

    private suspend fun extractPdf(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("PDF قابل باز شدن نیست")
        val renderer = PdfRenderer(pfd)
        try {
            val out = StringBuilder()
            val maxPages = minOf(renderer.pageCount, 12)
            for (i in 0 until maxPages) {
                renderer.openPage(i).use { page ->
                    val scale = 2
                    val bitmap = Bitmap.createBitmap(
                        page.width * scale,
                        page.height * scale,
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    out.appendLine("=== صفحه ${i + 1} ===")
                    out.appendLine(extractFromBitmap(bitmap))
                    bitmap.recycle()
                }
            }
            out.toString()
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    private suspend fun recognize(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result.text) }
                .addOnFailureListener { err -> if (cont.isActive) cont.resumeWithException(err) }
        }
}
