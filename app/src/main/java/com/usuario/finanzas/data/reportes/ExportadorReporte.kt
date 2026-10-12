package com.usuario.finanzas.data.reportes

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.domain.finanzas.CalculadoraFinanciera
import com.usuario.finanzas.domain.finanzas.Dinero
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

class ExportadorReporte @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun pdf(uri: Uri, transacciones: List<Transaccion>, moneda: String, desde: LocalDate, hasta: LocalDate) = withContext(Dispatchers.IO) {
        require(transacciones.isNotEmpty()) { "No hay movimientos para exportar." }
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
        var numero = 0
        var pagina: PdfDocument.Page? = null
        var y = 0f
        fun linea(texto: String, grande: Boolean = false) {
            paint.textSize = if (grande) 14f else 10f
            val max = 500f
            var resto = texto
            while (resto.isNotEmpty()) {
                if (pagina == null || y > 785) {
                    pagina?.let(doc::finishPage)
                    numero++
                    pagina = doc.startPage(PdfDocument.PageInfo.Builder(595,842,numero).create())
                    y = 40f
                    pagina!!.canvas.drawText("Mis Finanzas · $desde — $hasta · $numero", 40f, y, paint)
                    y += 26
                }
                val n = paint.breakText(resto, true, max, null).coerceAtLeast(1)
                pagina!!.canvas.drawText(resto.take(n),40f,y,paint)
                y += if(grande) 22 else 16
                resto = resto.drop(n)
            }
        }
        try {
            linea("Informe de movimientos registrados", true)
            linea("Entradas: ${Dinero.formatear(CalculadoraFinanciera.total(transacciones,"entrada"), moneda)}")
            linea("Salidas: ${Dinero.formatear(CalculadoraFinanciera.total(transacciones,"salida"), moneda)}")
            linea("Balance: ${Dinero.formatear(CalculadoraFinanciera.total(transacciones,"entrada")-CalculadoraFinanciera.total(transacciones,"salida"), moneda)}")
            linea("${transacciones.size} movimientos. Los traspasos propios se excluyen de los totales.")
            y += 10
            transacciones.sortedBy { it.fecha }.forEach { t ->
                linea("${CalculadoraFinanciera.fechaLocal(t.fecha)} · ${t.direccion} · ${t.tipo.replace('_',' ')} · ${Dinero.formatear(CalculadoraFinanciera.monto(t),moneda)}")
                linea(t.nombreComercio.take(200))
                if (t.descripcion.isNotBlank()) linea(t.descripcion.take(500))
                if (t.etiquetas.isNotBlank()) linea("Etiquetas: ${t.etiquetas}")
                y += 8
            }
            pagina?.let(doc::finishPage)
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use(doc::writeTo)
        } finally { doc.close() }
    }

    suspend fun csv(uri: Uri, transacciones: List<Transaccion>, moneda: String) = withContext(Dispatchers.IO) {
        require(transacciones.isNotEmpty())
        fun celda(s: String): String {
            // Excel también interpreta fórmulas tras espacios o caracteres de control.
            val inicio = s.dropWhile { it.isWhitespace() || it.code < 32 }.firstOrNull()
            val seguro = if (inicio in listOf('=', '+', '-', '@') || s.firstOrNull() in listOf('\t', '\r', '\n')) "'$s" else s
            return "\"${seguro.replace("\"", "\"\"")}\""
        }
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { out ->
            out.write("\uFEFFFecha,Concepto,Direccion,Tipo,Monto,Moneda,CategoriaId,Etiquetas\r\n")
            transacciones.sortedBy { it.fecha }.forEach { t ->
                out.write(listOf(CalculadoraFinanciera.fechaLocal(t.fecha).toString(), t.nombreComercio, t.direccion, t.tipo, java.math.BigDecimal.valueOf(CalculadoraFinanciera.monto(t),2).toPlainString(), moneda,t.categoriaId.toString(),t.etiquetas).joinToString(",", transform = ::celda) + "\r\n")
            }
        }
    }
}
