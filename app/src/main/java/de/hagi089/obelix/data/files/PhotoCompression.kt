package de.hagi089.obelix.data.files

/**
 * Stufen der Fotoverkleinerung für Stellplätze (Phase 9). Ein Foto muss in ein einziges Stück passen
 * ([FileLimits.MAX_PHOTO_BYTES]). Zuerst wird mit der üblichen Qualität versucht, dann mit sinkender Qualität, dann mit
 * kleinerer Bildgröße. Der Rechenteil steht hier ohne Android-Klassen, damit er sich testen lässt.
 */
object PhotoCompression {

    /** Eine Stufe: lange Seite in Pixeln und JPEG-Qualität in Prozent. */
    data class Step(val maxSide: Int, val quality: Int)

    /** Von der besten zur kleinsten Stufe. Die erste Stufe entspricht der Belegverkleinerung (1800 px, 80 %). */
    val steps: List<Step> = listOf(
        Step(FileLimits.IMAGE_MAX_SIDE_PX, FileLimits.JPEG_QUALITY),
        Step(FileLimits.IMAGE_MAX_SIDE_PX, 70),
        Step(FileLimits.IMAGE_MAX_SIDE_PX, 60),
        Step(FileLimits.IMAGE_MAX_SIDE_PX, 50),
        Step(1400, 70),
        Step(1000, 70),
    )

    /** true, wenn ein fertiges JPEG der Größe [sizeBytes] als Stellplatzfoto gespeichert werden darf. */
    fun fits(sizeBytes: Int): Boolean = sizeBytes in 1..FileLimits.MAX_PHOTO_BYTES
}
