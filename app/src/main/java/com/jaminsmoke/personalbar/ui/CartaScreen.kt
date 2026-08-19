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
import androidx.compose.material3.Checkbox
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
import com.jaminsmoke.personalbar.data.GrupoModificador
import com.jaminsmoke.personalbar.data.OpcionModificador
import com.jaminsmoke.personalbar.data.Producto
import com.jaminsmoke.personalbar.data.destinoDesdeCategoria

/** Categorías canónicas de la carta (valores que `destinoDesdeCategoria` mapea correctamente). */
private val CATEGORIAS = listOf(
    "Bebida" to R.string.carta_categoria_bebida,
    "Comida" to R.string.carta_categoria_comida,
)

/** Pantalla del editor de carta: lista + alta/edición + borrado + toggle `disponible` + grupos. */
@Composable
fun CartaScreen(viewModel: CartaViewModel = viewModel()) {
    val catalogo by viewModel.catalogo.collectAsState()
    val grupos by viewModel.gruposModificador.collectAsState()
    val error by viewModel.error.collectAsState()

    var creando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Producto?>(null) }
    var borrando by remember { mutableStateOf<Producto?>(null) }
    var mostrandoGrupos by remember { mutableStateOf(false) }

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
            TextButton(onClick = { mostrandoGrupos = true }) {
                Text(stringResource(R.string.carta_grupos_titulo))
            }
            Spacer(Modifier.width(4.dp))
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
                                subfamilia = producto.subfamilia,
                                permiteNota = producto.permiteNota,
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
            subfamiliaInicial = "",
            permiteNotaInicial = false,
            disponibleInicial = true,
            grupos = grupos,
            asignados = emptySet(),
            onToggleGrupo = {},
            onConfirm = { nombre, categoria, precio, subfamilia, permiteNota, disponible ->
                if (viewModel.crear(nombre, categoria, precio, subfamilia, permiteNota)) creando = false
            },
            onDismiss = { creando = false },
        )
    }

    editando?.let { producto ->
        val asignados = viewModel.productoGrupo.collectAsState().value
            .filter { it.productoId == producto.id }
            .map { it.grupoId }
            .toSet()
        CartaDialogo(
            titulo = stringResource(R.string.carta_editar),
            nombreInicial = producto.nombre,
            categoriaInicial = producto.categoria,
            precioInicial = precioTexto(producto.precio, conSimbolo = false),
            subfamiliaInicial = producto.subfamilia.orEmpty(),
            permiteNotaInicial = producto.permiteNota,
            disponibleInicial = producto.disponible,
            grupos = grupos,
            asignados = asignados,
            onToggleGrupo = { grupoId ->
                if (grupoId in asignados) viewModel.desasignarGrupo(producto.id, grupoId)
                else viewModel.asignarGrupo(producto.id, grupoId)
            },
            onConfirm = { nombre, categoria, precio, subfamilia, permiteNota, disponible ->
                if (viewModel.editar(producto.id, nombre, categoria, precio, disponible, subfamilia, permiteNota)) {
                    editando = null
                }
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

    if (mostrandoGrupos) {
        ModificadoresDialog(
            viewModel = viewModel,
            onDismiss = { mostrandoGrupos = false },
        )
    }
}

/** Fila de producto: nombre, subfamilia, categoría/destino, precio, toggle disponible, editar y borrar. */
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
                    producto.subfamilia?.takeIf { it.isNotBlank() }?.let { sub ->
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

/** Diálogo de alta/edición: nombre, categoría, precio, subfamilia, permiteNota, disponible y grupos. */
@Composable
private fun CartaDialogo(
    titulo: String,
    nombreInicial: String,
    categoriaInicial: String,
    precioInicial: String,
    subfamiliaInicial: String,
    permiteNotaInicial: Boolean,
    disponibleInicial: Boolean,
    grupos: List<GrupoModificador>,
    asignados: Set<String>,
    onToggleGrupo: (String) -> Unit,
    onConfirm: (nombre: String, categoria: String, precio: Double, subfamilia: String?, permiteNota: Boolean, disponible: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var nombre by remember { mutableStateOf(nombreInicial) }
    var categoria by remember { mutableStateOf(categoriaInicial) }
    var precio by remember { mutableStateOf(precioInicial) }
    var subfamilia by remember { mutableStateOf(subfamiliaInicial) }
    var permiteNota by remember { mutableStateOf(permiteNotaInicial) }
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
                OutlinedTextField(
                    value = subfamilia,
                    onValueChange = { subfamilia = it },
                    label = { Text(stringResource(R.string.carta_campo_subfamilia)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.carta_campo_permite_nota),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = permiteNota, onCheckedChange = { permiteNota = it })
                }
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
                if (grupos.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.carta_asignar_grupos),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Column {
                        grupos.forEach { grupo ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = grupo.id in asignados,
                                    onCheckedChange = { onToggleGrupo(grupo.id) },
                                )
                                Text(
                                    text = grupo.nombre,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        nombre,
                        categoria,
                        precio.toDoubleOrNull() ?: 0.0,
                        subfamilia.trim().takeIf { it.isNotEmpty() },
                        permiteNota,
                        disponible,
                    )
                },
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

/** Diálogo de gestión de grupos de modificadores: CRUD de grupos y sus opciones. */
@Composable
private fun ModificadoresDialog(
    viewModel: CartaViewModel,
    onDismiss: () -> Unit,
) {
    val grupos by viewModel.gruposModificador.collectAsState()
    val opciones by viewModel.opcionesModificador.collectAsState()

    var editandoGrupo by remember { mutableStateOf<GrupoModificador?>(null) }
    var borrandoGrupo by remember { mutableStateOf<GrupoModificador?>(null) }
    var creandoGrupo by remember { mutableStateOf(false) }
    var editandoOpcion by remember { mutableStateOf<OpcionModificador?>(null) }
    var borrandoOpcion by remember { mutableStateOf<OpcionModificador?>(null) }
    var creandoOpcionEnGrupo by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.carta_grupos_titulo), modifier = Modifier.weight(1f))
                IconButton(onClick = { creandoGrupo = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.carta_grupo_nuevo))
                }
            }
        },
        text = {
            if (grupos.isEmpty()) {
                Text(
                    text = stringResource(R.string.carta_grupos_vacio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    grupos.forEach { grupo ->
                        val opcionesGrupo = opciones.filter { it.grupoId == grupo.id }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = grupo.nombre,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = descripcionGrupo(grupo),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { editandoGrupo = grupo }) {
                                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.carta_grupo_editar))
                                    }
                                    IconButton(onClick = { borrandoGrupo = grupo }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.carta_grupo_borrar))
                                    }
                                }
                                opcionesGrupo.forEach { opcion ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = opcion.nombre,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = deltaTexto(opcion.deltaPrecio),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        IconButton(onClick = { editandoOpcion = opcion }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.carta_opcion_editar))
                                        }
                                        IconButton(onClick = { borrandoOpcion = opcion }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.carta_opcion_borrar))
                                        }
                                    }
                                }
                                TextButton(onClick = { creandoOpcionEnGrupo = grupo.id }) {
                                    Text(stringResource(R.string.carta_opcion_nueva))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.carta_cerrar)) }
        },
    )

    if (creandoGrupo) {
        GrupoDialogo(
            titulo = stringResource(R.string.carta_grupo_nuevo),
            nombreInicial = "",
            multipleInicial = false,
            obligatorioInicial = false,
            onConfirm = { nombre, multiple, obligatorio ->
                if (viewModel.crearGrupo(nombre, multiple, obligatorio)) creandoGrupo = false
            },
            onDismiss = { creandoGrupo = false },
        )
    }

    editandoGrupo?.let { grupo ->
        GrupoDialogo(
            titulo = stringResource(R.string.carta_grupo_editar),
            nombreInicial = grupo.nombre,
            multipleInicial = grupo.multiple,
            obligatorioInicial = grupo.obligatorio,
            onConfirm = { nombre, multiple, obligatorio ->
                if (viewModel.editarGrupo(grupo.id, nombre, multiple, obligatorio)) editandoGrupo = null
            },
            onDismiss = { editandoGrupo = null },
        )
    }

    borrandoGrupo?.let { grupo ->
        AlertDialog(
            onDismissRequest = { borrandoGrupo = null },
            title = { Text(stringResource(R.string.carta_grupo_borrar)) },
            text = { Text(stringResource(R.string.carta_grupo_borrar_mensaje, grupo.nombre)) },
            confirmButton = {
                TextButton(onClick = { viewModel.borrarGrupo(grupo.id); borrandoGrupo = null }) {
                    Text(stringResource(R.string.mapa_menu_borrar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { borrandoGrupo = null }) { Text(stringResource(R.string.mapa_cancelar)) }
            },
        )
    }

    creandoOpcionEnGrupo?.let { grupoId ->
        OpcionDialogo(
            titulo = stringResource(R.string.carta_opcion_nueva),
            nombreInicial = "",
            deltaInicial = "",
            aliasInicial = "",
            onConfirm = { nombre, delta, alias ->
                if (viewModel.crearOpcion(grupoId, nombre, delta, alias)) creandoOpcionEnGrupo = null
            },
            onDismiss = { creandoOpcionEnGrupo = null },
        )
    }

    editandoOpcion?.let { opcion ->
        OpcionDialogo(
            titulo = stringResource(R.string.carta_opcion_editar),
            nombreInicial = opcion.nombre,
            deltaInicial = deltaPlano(opcion.deltaPrecio),
            aliasInicial = opcion.alias,
            onConfirm = { nombre, delta, alias ->
                if (viewModel.editarOpcion(opcion.id, nombre, delta, alias)) editandoOpcion = null
            },
            onDismiss = { editandoOpcion = null },
        )
    }

    borrandoOpcion?.let { opcion ->
        AlertDialog(
            onDismissRequest = { borrandoOpcion = null },
            title = { Text(stringResource(R.string.carta_opcion_borrar)) },
            text = { Text(stringResource(R.string.carta_opcion_borrar_mensaje, opcion.nombre)) },
            confirmButton = {
                TextButton(onClick = { viewModel.borrarOpcion(opcion.id); borrandoOpcion = null }) {
                    Text(stringResource(R.string.mapa_menu_borrar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { borrandoOpcion = null }) { Text(stringResource(R.string.mapa_cancelar)) }
            },
        )
    }
}

/** Diálogo de alta/edición de un grupo de modificadores. */
@Composable
private fun GrupoDialogo(
    titulo: String,
    nombreInicial: String,
    multipleInicial: Boolean,
    obligatorioInicial: Boolean,
    onConfirm: (nombre: String, multiple: Boolean, obligatorio: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var nombre by remember { mutableStateOf(nombreInicial) }
    var multiple by remember { mutableStateOf(multipleInicial) }
    var obligatorio by remember { mutableStateOf(obligatorioInicial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text(stringResource(R.string.carta_grupo_campo_nombre)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.carta_grupo_multiple),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = multiple, onCheckedChange = { multiple = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.carta_grupo_obligatorio),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = obligatorio, onCheckedChange = { obligatorio = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nombre, multiple, obligatorio) },
                enabled = nombre.isNotBlank(),
            ) {
                Text(stringResource(R.string.carta_guardar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.mapa_cancelar)) }
        },
    )
}

/** Diálogo de alta/edición de una opción de modificador. */
@Composable
private fun OpcionDialogo(
    titulo: String,
    nombreInicial: String,
    deltaInicial: String,
    aliasInicial: String,
    onConfirm: (nombre: String, delta: Double, alias: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var nombre by remember { mutableStateOf(nombreInicial) }
    var delta by remember { mutableStateOf(deltaInicial) }
    var alias by remember { mutableStateOf(aliasInicial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text(stringResource(R.string.carta_opcion_campo_nombre)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = delta,
                    onValueChange = { delta = it },
                    label = { Text(stringResource(R.string.carta_opcion_campo_delta)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(stringResource(R.string.carta_opcion_campo_alias)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nombre, delta.toDoubleOrNull() ?: 0.0, alias) },
                enabled = nombre.isNotBlank(),
            ) {
                Text(stringResource(R.string.carta_guardar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.mapa_cancelar)) }
        },
    )
}

/** Descripción compacta de las flags de un grupo (multiple/obligatorio). */
@Composable
private fun descripcionGrupo(grupo: GrupoModificador): String {
    val partes = mutableListOf<String>()
    if (grupo.multiple) partes.add(stringResource(R.string.carta_grupo_multiple))
    if (grupo.obligatorio) partes.add(stringResource(R.string.carta_grupo_obligatorio))
    return partes.joinToString(" · ")
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

/** Delta de una opción legible: «+0,50 €» (solo si ≠ 0), «—» si es 0. */
private fun deltaTexto(delta: Double): String {
    if (delta == 0.0) return "—"
    val importe = String.format(java.util.Locale.getDefault(), "%.2f", delta)
    return if (delta > 0) "+$importe €" else "$importe €"
}

/** Delta plano para editar en un campo de texto: «» si es 0, «0,50» si no (sin símbolo). */
private fun deltaPlano(delta: Double): String =
    if (delta == 0.0) "" else String.format(java.util.Locale.getDefault(), "%.2f", delta)
