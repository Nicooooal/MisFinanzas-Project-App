package com.usuario.finanzas.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.usuario.finanzas.domain.captura.LineaRecibo
import com.usuario.finanzas.domain.captura.TextoGeometricoRecibo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Modelo incluido en la aplicación; no envía fotografías a ningún servidor. */
object LectorRecibo {
    suspend fun leer(context: Context, uri: Uri): String {
        val bitmap = withContext(Dispatchers.IO) { cargarImagen(context, uri) }
        coroutineContext.ensureActive()
        val reconocedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            suspend fun reconocer(rotacion: Int): Text = suspendCancellableCoroutine { c ->
                reconocedor.process(InputImage.fromBitmap(bitmap, rotacion))
                    .addOnSuccessListener { if (c.isActive) c.resume(it) }
                    .addOnFailureListener { if (c.isActive) c.resumeWithException(it) }
            }
            var resultado = reconocer(0)
            // Un único intento adicional para imágenes giradas sin metadatos de orientación.
            if (resultado.text.isBlank()) resultado = reconocer(90)
            val lineas = resultado.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                l.boundingBox?.let { b -> LineaRecibo(l.text, b.left, b.top, b.right, b.bottom) }
            }
            return TextoGeometricoRecibo.ordenar(lineas).ifBlank { resultado.text }.take(50_000)
        } finally {
            reconocedor.close()
            // No reciclar el bitmap aquí: una tarea nativa puede seguir terminando tras cancelar.
        }
    }

    private fun cargarImagen(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        require(limites.outWidth > 0 && limites.outHeight > 0) { "El archivo no contiene una imagen compatible." }
        var muestra = 1
        while (limites.outWidth / muestra > 4096 || limites.outHeight / muestra > 4096 ||
            (limites.outWidth / muestra).toLong() * (limites.outHeight / muestra) > 8_000_000L) muestra *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
            ?: error("No se pudo abrir la imagen. Selecciónala nuevamente.")
        val orientacion = runCatching { resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }.getOrNull()
        val matriz = Matrix().apply {
            when (orientacion) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        if (matriz.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true).also { if (it !== bitmap) bitmap.recycle() }
    }
}
