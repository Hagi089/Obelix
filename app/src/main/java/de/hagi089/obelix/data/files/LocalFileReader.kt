package de.hagi089.obelix.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.StringRes
import de.hagi089.obelix.R
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Eine ausgewählte Datei konnte nicht übernommen werden; [messageRes] ist die Meldung für den Benutzer. */
class FileReadException(@param:StringRes val messageRes: Int, cause: Throwable? = null) : Exception("Datei nicht übernommen", cause)

/** Liest eine vom Benutzer ausgewählte Datei und bereitet sie zum Speichern vor (Anforderung 5 und 17). */
interface LocalFileReader {
    /** Liefert die fertig aufbereitete Datei (Bilder verkleinert als JPEG, PDF unverändert) oder eine [FileReadException]. */
    suspend fun read(uri: Uri): Result<NewFile>
}

class AndroidFileReader(context: Context) : LocalFileReader {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: Uri): Result<NewFile> = withContext(Dispatchers.IO) {
        try {
            Result.success(readBlocking(uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: FileReadException) {
            Result.failure(e)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Nicht genug Speicher für die Datei", e)
            Result.failure(FileReadException(R.string.error_file_unreadable, e))
        } catch (e: Exception) {
            Log.w(TAG, "Datei konnte nicht gelesen werden", e)
            Result.failure(FileReadException(R.string.error_file_unreadable, e))
        }
    }

    private fun readBlocking(uri: Uri): NewFile {
        val mime = resolver.getType(uri)
        val rawName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
        return when {
            mime == FileLimits.MIME_PDF -> readPdf(uri, rawName)
            mime != null && mime.startsWith("image/") -> readImage(uri, rawName)
            else -> throw FileReadException(R.string.error_file_type)
        }
    }

    private fun readPdf(uri: Uri, rawName: String?): NewFile {
        val bytes = readCapped(uri, FileLimits.MAX_FILE_BYTES, R.string.error_file_too_large)
        FileValidator.check(bytes.size.toLong(), FileLimits.MIME_PDF)?.let { throw FileReadException(it) }
        return NewFile(FileValidator.cleanName(rawName, "Beleg.pdf"), FileLimits.MIME_PDF, bytes)
    }

    private fun readImage(uri: Uri, rawName: String?): NewFile {
        val source = readCapped(uri, FileLimits.MAX_SOURCE_IMAGE_BYTES, R.string.error_image_source_too_large)
        val jpeg = compressToJpeg(source)
        FileValidator.check(jpeg.size.toLong(), FileLimits.MIME_JPEG)?.let { throw FileReadException(it) }
        return NewFile(FileValidator.jpegName(FileValidator.cleanName(rawName, "Beleg")), FileLimits.MIME_JPEG, jpeg)
    }

    /** Liest höchstens [limit] Bytes; eine größere Datei wird abgelehnt, ohne sie ganz in den Speicher zu laden. */
    private fun readCapped(uri: Uri, limit: Int, @StringRes tooLarge: Int): ByteArray {
        val input = resolver.openInputStream(uri) ?: throw FileReadException(R.string.error_file_unreadable)
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw FileReadException(tooLarge)
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    /**
     * Dekodiert verkleinert (nie das ganze Originalbild), dreht nach den Exif-Angaben, skaliert auf höchstens
     * [FileLimits.IMAGE_MAX_SIDE_PX] an der langen Seite und speichert als JPEG (Hintergrund weiß statt schwarz).
     */
    private fun compressToJpeg(source: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw FileReadException(R.string.error_file_unreadable)

        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageScaling.sampleSize(bounds.outWidth, bounds.outHeight, FileLimits.IMAGE_MAX_SIDE_PX)
        }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, options)
            ?: throw FileReadException(R.string.error_file_unreadable)

        var transformed: Bitmap? = null
        var flattened: Bitmap? = null
        try {
            val matrix = Matrix()
            val swapsSides = applyExifOrientation(matrix, exifOrientation(source))
            val width = if (swapsSides) decoded.height else decoded.width
            val height = if (swapsSides) decoded.width else decoded.height
            val (targetWidth, _) = ImageScaling.targetSize(width, height, FileLimits.IMAGE_MAX_SIDE_PX)
            val scale = targetWidth.toFloat() / width
            if (scale < 1f) matrix.postScale(scale, scale)

            transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            flattened = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
            Canvas(flattened).apply {
                drawColor(Color.WHITE)
                drawBitmap(transformed, 0f, 0f, null)
            }
            val out = ByteArrayOutputStream()
            if (!flattened.compress(Bitmap.CompressFormat.JPEG, FileLimits.JPEG_QUALITY, out)) {
                throw FileReadException(R.string.error_file_unreadable)
            }
            return out.toByteArray()
        } finally {
            if (transformed != null && transformed !== decoded) transformed.recycle()
            flattened?.recycle()
            decoded.recycle()
        }
    }

    private fun exifOrientation(source: ByteArray): Int = try {
        ExifInterface(ByteArrayInputStream(source)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    /** Trägt Drehung/Spiegelung in [matrix] ein. Liefert true, wenn dabei Breite und Höhe vertauscht werden. */
    private fun applyExifOrientation(matrix: Matrix, orientation: Int): Boolean = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> { matrix.postRotate(90f); true }
        ExifInterface.ORIENTATION_ROTATE_180 -> { matrix.postRotate(180f); false }
        ExifInterface.ORIENTATION_ROTATE_270 -> { matrix.postRotate(270f); true }
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> { matrix.postScale(-1f, 1f); false }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.postScale(1f, -1f); false }
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f); true }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f); true }
        else -> false
    }

    private companion object {
        const val TAG = "Obelix"
        const val BUFFER_SIZE = 64 * 1024
    }
}
