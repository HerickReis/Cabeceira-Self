package com.cabeceira.app.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.cabeceira.app.AppContainer
import com.cabeceira.app.CabeceiraApp

fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as CabeceiraApp).container
