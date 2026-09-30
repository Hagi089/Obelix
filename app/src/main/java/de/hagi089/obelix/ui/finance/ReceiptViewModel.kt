package de.hagi089.obelix.ui.finance

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.FileStore
import de.hagi089.obelix.data.files.ReceiptCache
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReceiptState {
    data object Loading : ReceiptState
    data class Failed(val error: AppError) : ReceiptState

    /** Belegfoto, bereit zur Anzeige in der App. */
    data class Image(val bitmap: ImageBitmap) : ReceiptState

    /** PDF-Beleg, im Zwischenspeicher abgelegt; wird in einer externen PDF-App geöffnet. */
    data class Pdf(val file: File) : ReceiptState
}

/** Lädt einen Beleg aus der Dateiablage (nur beim Öffnen, nie im Voraus: Anforderung 37). */
class ReceiptViewModel(
    private val ref: FileRef,
    private val files: FileStore,
    private val cache: ReceiptCache,
) : ViewModel() {

    private val _state = MutableStateFlow<ReceiptState>(ReceiptState.Loading)
    val state: StateFlow<ReceiptState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ReceiptState.Loading
        viewModelScope.launch {
            _state.value = files.load(ref).fold(
                onSuccess = { bytes -> prepare(bytes) },
                onFailure = { ReceiptState.Failed((it as? AppException)?.error ?: AppError.UNKNOWN) },
            )
        }
    }

    private suspend fun prepare(bytes: ByteArray): ReceiptState = try {
        if (ref.isPdf) {
            ReceiptState.Pdf(cache.writePdf(ref.fileId, bytes))
        } else {
            val bitmap = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            if (bitmap != null) ReceiptState.Image(bitmap) else ReceiptState.Failed(AppError.FILE_CORRUPT)
        }
    } catch (e: IOException) {
        Log.w("Obelix", "Beleg konnte nicht vorbereitet werden", e)
        ReceiptState.Failed(AppError.UNKNOWN)
    } catch (e: OutOfMemoryError) {
        Log.w("Obelix", "Nicht genug Speicher für den Beleg", e)
        ReceiptState.Failed(AppError.UNKNOWN)
    }
}
