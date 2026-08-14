package com.jaminsmoke.personalbar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.Destino
import com.jaminsmoke.personalbar.data.Producto
import com.jaminsmoke.personalbar.data.destinoDesdeCategoria

/** Categorías canónicas de la carta (valores que `destinoDesdeCategoria` mapea correctamente). */
private val CATEGORIAS = listOf(
    "Bebida" to R.string.carta_categoria_bebida,
    "Comida" to R.string.carta_categoria_comida,
)

/** Pantalla del editor de carta: lista + alta/edición + borrado + toggle `disponible`. */
@Composable
fun CartaScreen(viewModel: CartaViewModel = viewModel()) {
    val catalogo by viewModel.catalogo.collectAsState()
    val error by viewModel.error.collectAsState()

    var creando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Producto?>(null) }
    var borrando by remember { mutableStateOf<Producto?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.carta_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.carta_subtitulo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { creando = true }) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = stringResource(R.string.carta_nuevo_producto))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.carta_nuevo_producto))
            }
        }
        error?.let { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        if (catalogo.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.carta_vacia),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.carta_vacia_subtitulo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(catalogo, key = { it.id }) { producto ->
                    CartaFila(
                        producto = producto,
                        onEditar = { editando = producto },
                        onBorrar = { borrando = producto },
                        onToggleDisponible = {
                            viewModel.editar(
                                id = producto.id,
                                nombre = producto.nombre,
                                categoria = producto.categoria,
                                precio = producto.precio,
                                disponible = !producto.disponible,
                            )
                        },
                    )
                }
            }
        }
    }

    if (creando) {
        CartaDialogo(
            titulo = stringResource(R.string.carta_nuevo_producto),
            nombreInicial = "",
            categoriaInicial = CATEGORIAS.first().first,
            precioInicial = "",
            disponibleInicial = true,
            onConfirm = { nombre, categoria, precio, disponible ->
                if (viewModel.crear(nombre, categoria, precio)) creando = false
            },
            onDismiss = { creando = false },
        )
    }

    editando?.let { producto ->
        CartaDialogo(
            titulo = stringResource(R.string.carta_editar),
            nombreInicial = producto.nombre,
            categoriaInicial = producto.categoria,
            precioInicial = precioTexto(producto.precio, conSimbolo = false),
            disponibleInicial = producto.disponible,
            onConfirm = { nombre, categoria, precio, disponible ->
                if (viewModel.editar(producto.id, nombre, categoria, precio, disponible)) editando = null
            },
            onDismiss = { editando = null },
        )
    }

    borrando?.let { producto ->
        AlertDialog(
            onDismissRequest = { borrando = null },
            title = { Text(stringResource(R.string.carta_borrar_titulo)) },
            text = { Text(stringResource(R.string.carta_borrar_mensaje, producto.nombre)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.borrar(producto.id)
                        borrando = null
                    },
                ) {
                    Text(stringResource(R.string.mapa_menu_borrar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { borrando = null }) { Text(stringResource(R.string.mapa_cancelar)) }
            },
        )
    }
}

/** Fila de producto: nombre, categoría/destino, precio, toggle disponible, editar y borrar. */
@Composable
private fun CartaFila(
    producto: Producto,
    onEditar: () -> Unit,
    onBorrar: () -> Unit,
    onToggleDisponible: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = producto.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(categoriaLabelRes(producto.categoria)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = precioTexto(producto.precio, conSimbolo = true),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!producto.disponible) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.carta_no_disponible),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Switch(checked = producto.disponible, onCheckedChange = { onToggleDisponible() })
            IconButton(onClick = onEditar) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.carta_editar),
                )
            }
            IconButton(onClick = onBorrar) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.carta_borrar_titulo),
                )
            }
        }
    }
}

/** Diálogo de alta/edición: nombre, categoría (chips Bebida/Comida), precio, disponible. */
@Composable
private fun CartaDialogo(
    titulo: String,
    nombreInicial: String,
    categoriaInicial: String,
    precioInicial: String,
    disponibleInicial: Boolean,
    onConfirm: (nombre: String, categoria: String, precio: Double, disponible: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var nombre by remember { mutableStateOf(nombreInicial) }
    var categoria by remember { mutableStateOf(categoriaInicial) }
    var precio by remember { mutableStateOf(precioInicial) }
    var disponible by remember { mutableStateOf(disponibleInicial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text(stringResource(R.string.carta_campo_nombre)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.carta_campo_categoria),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIAS.forEach { (valor, labelRes) ->
                        CategoriaChip(
                            label = stringResource(labelRes),
                            seleccionada = categoria == valor,
                            onClick = { categoria = valor },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = precio,
                    onValueChange = { precio = it },
                    label = { Text(stringResource(R.string.carta_campo_precio)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.carta_campo_disponible),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = disponible, onCheckedChange = { disponible = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nombre, categoria, precio.toDoubleOrNull() ?: 0.0, disponible) },
                enabled = nombre.isNotBlank() && categoria.isNotBlank(),
            ) {
                Text(stringResource(R.string.carta_guardar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.mapa_cancelar)) }
        },
    )
}

/** Chip seleccionable de categoría (Bebida/Comida). */
@Composable
private fun CategoriaChip(
    label: String,
    seleccionada: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (seleccionada) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (seleccionada) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Recurso de texto del badge de categoría a partir del valor guardado. */
private fun categoriaLabelRes(categoria: String): Int = when (destinoDesdeCategoria(categoria)) {
    Destino.BARRA -> R.string.carta_categoria_bebida
    Destino.COCINA -> R.string.carta_categoria_comida
}

/** Precio legible: «—» si es 0; si no, «2,50 €» (con o sin símbolo según [conSimbolo]). */
private fun precioTexto(precio: Double, conSimbolo: Boolean): String {
    if (precio == 0.0) return "—"
    val importe = String.format(java.util.Locale.getDefault(), "%.2f", precio)
    return if (conSimbolo) "$importe €" else importe
}
