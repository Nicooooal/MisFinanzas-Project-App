package com.usuario.finanzas.ui.screens.planes

import com.usuario.finanzas.ui.i18n.tr

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.usuario.finanzas.domain.finanzas.CalculadoraFinanciera
import com.usuario.finanzas.ui.util.formatearFecha
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorOpcion(etiqueta: String, valor: String, opciones: List<Pair<String, String>>, onCambiar: (String) -> Unit, modifier: Modifier = Modifier) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(abierto, { abierto = it }, modifier) {
        OutlinedTextField(
            value = opciones.find { it.first == valor }?.second ?: valor,
            onValueChange = {}, readOnly = true, label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(abierto, { abierto = false }) {
            opciones.forEach { (id, nombre) -> DropdownMenuItem(text = { Text(nombre) }, onClick = { onCambiar(id); abierto = false }) }
        }
    }
}

@Composable
fun CampoMonto(valor: String, moneda: String, onCambiar: (String) -> Unit, etiqueta: String = tr("Monto")) {
    OutlinedTextField(valor, onCambiar, label = { Text("$etiqueta ($moneda)") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

@Composable
fun CampoFecha(fecha: Long, onCambiar: (Long) -> Unit, etiqueta: String, permitirFuturo: Boolean = false) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val local = CalculadoraFinanciera.fechaLocal(fecha)
        DatePickerDialog(context, { _, anio, mes, dia ->
            onCambiar(LocalDate.of(anio, mes + 1, dia).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }, local.year, local.monthValue - 1, local.dayOfMonth).apply {
            datePicker.minDate = 0
            if (!permitirFuturo) datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }, modifier = Modifier.fillMaxWidth()) { Text("$etiqueta: ${formatearFecha(fecha)}") }
}

fun inicioHoy(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
fun ErrorFormulario(error: String?) {
    error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun ProgresoPlan(actual: Long, objetivo: Long, descripcion: String) {
    val progreso = if (objetivo > 0) (actual.toDouble() / objetivo).coerceIn(0.0, 1.0).toFloat() else 0f
    Text(descripcion, style = MaterialTheme.typography.bodyMedium)
    LinearProgressIndicator(progress = { progreso }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
}
