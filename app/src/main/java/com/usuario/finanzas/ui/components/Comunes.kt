package com.usuario.finanzas.ui.components

import com.usuario.finanzas.ui.i18n.tr

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.ui.util.colorDesdeHex
import com.usuario.finanzas.ui.util.iconoCategoria

/** Dropdown de categorías reutilizado por Detalle, Confirmación y Agregar manual. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorCategoria(
    categorias: List<Categoria>,
    seleccionada: Categoria?,
    onSeleccionar: (Categoria) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandido by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expandido,
        onExpandedChange = { expandido = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = seleccionada?.nombre ?: tr("Selecciona una categoría"),
            onValueChange = {},
            readOnly = true,
            label = { Text(tr("Categoría")) },
            leadingIcon = seleccionada?.let { cat ->
                {
                    Icon(
                        imageVector = iconoCategoria(cat.icono),
                        contentDescription = null,
                        tint = colorDesdeHex(cat.color)
                    )
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            categorias.forEach { categoria ->
                DropdownMenuItem(
                    text = { Text(categoria.nombre) },
                    leadingIcon = {
                        Icon(
                            imageVector = iconoCategoria(categoria.icono),
                            contentDescription = null,
                            tint = colorDesdeHex(categoria.color)
                        )
                    },
                    onClick = {
                        onSeleccionar(categoria)
                        expandido = false
                    }
                )
            }
        }
    }
}
