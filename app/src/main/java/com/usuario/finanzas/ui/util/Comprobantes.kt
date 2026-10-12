package com.usuario.finanzas.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Guarda las imágenes de comprobantes en almacenamiento INTERNO privado de la app
 * (context.filesDir/comprobantes/), nunca en la galería pública.
 */
object Comprobantes {

    private fun directorio(context: Context): File =
        File(context.filesDir, "comprobantes").apply { if (!exists()) mkdirs() }

    /** Guarda un Bitmap (foto tomada con la cámara) y devuelve la ruta absoluta. */
    fun guardarBitmap(context: Context, bitmap: Bitmap): String {
        val archivo = File(directorio(context), "${UUID.randomUUID()}.jpg")
        FileOutputStream(archivo).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return archivo.absolutePath
    }

    /** Copia una imagen elegida de la galería al almacenamiento privado; devuelve la ruta o null. */
    fun copiarDesdeUri(context: Context, uri: Uri): String? {
        return try {
            val archivo = File(directorio(context), "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { entrada ->
                FileOutputStream(archivo).use { salida -> entrada.copyTo(salida) }
            } ?: return null
            archivo.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Elimina el archivo del comprobante si existe. */
    fun eliminar(path: String?) {
        path?.let { File(it).takeIf(File::exists)?.delete() }
    }
}
