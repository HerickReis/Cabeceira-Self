package com.cabeceira.app.ui.search

import android.content.Context
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Usa o scanner pronto do Google Play services: abre a câmera numa tela própria
 * e devolve o código lido. Não precisa pedir permissão de câmera ao usuário.
 */
fun scanBookBarcode(
    context: Context,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A)
        .enableAutoZoom()
        .build()

    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            val value = barcode.rawValue
            if (value.isNullOrBlank()) onError("Não consegui ler o código.") else onResult(value)
        }
        .addOnFailureListener { e ->
            val msg = if (e is MlKitException && e.errorCode == MlKitException.UNAVAILABLE) {
                "Scanner ainda sendo baixado pelo Google Play services. Tente de novo em instantes."
            } else {
                "Falha ao abrir o scanner: ${e.localizedMessage ?: "erro desconhecido"}"
            }
            onError(msg)
        }
    // cancelamento pelo usuário: não faz nada
}
