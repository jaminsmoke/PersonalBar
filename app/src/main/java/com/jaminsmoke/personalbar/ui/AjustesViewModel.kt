package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Establecimiento
import kotlinx.coroutines.flow.StateFlow

/** ViewModel de Ajustes: solo el establecimiento (las salas se gestionan en el Mapa). */
class AjustesViewModel : ViewModel() {
    private val repository: BarRepository = PersonalBarApp.get().repository

    val establecimiento: StateFlow<Establecimiento> = repository.establecimiento
}
