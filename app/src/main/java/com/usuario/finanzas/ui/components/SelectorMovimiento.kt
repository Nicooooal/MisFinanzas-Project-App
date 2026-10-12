package com.usuario.finanzas.ui.components

import com.usuario.finanzas.ui.i18n.tr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.usuario.finanzas.domain.model.ClaseMovimiento

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorMovimiento(clase: ClaseMovimiento, onSeleccionar: (ClaseMovimiento) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Column {
        ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
            OutlinedTextField(
                value = tr(clase.etiqueta), onValueChange = {}, readOnly = true,
                label = { Text(tr("Tipo de movimiento")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                ClaseMovimiento.entries.forEach { opcion ->
                    DropdownMenuItem(text = { Text(tr(opcion.etiqueta)) }, onClick = {
                        onSeleccionar(opcion)
                        abierto = false
                    })
                }
            }
        }
        if (clase.esPropia) Text(tr("Este traspaso se excluye de entradas, salidas y balance del mes."))
    }
}
