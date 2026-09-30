package de.hagi089.obelix.data.files

/** Rechenteil der Bildverkleinerung (ohne Android-Klassen, damit er sich testen lässt). */
object ImageScaling {

    /** Zielgröße: lange Seite höchstens [maxSide], Seitenverhältnis bleibt, nie vergrößern, jede Seite mindestens 1. */
    fun targetSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxSide > 0) { "Ungültige Bildgröße" }
        val longSide = maxOf(width, height)
        if (longSide <= maxSide) return width to height
        val factor = maxSide.toDouble() / longSide
        return Math.round(width * factor).toInt().coerceAtLeast(1) to Math.round(height * factor).toInt().coerceAtLeast(1)
    }

    /**
     * `inSampleSize` für das Dekodieren: die größte Zweierpotenz, bei der die lange Seite noch mindestens
     * [maxSide] beträgt. So wird nie ein riesiges Bild ganz in den Speicher geladen, und die endgültige
     * Verkleinerung geschieht noch mit voller Qualität.
     */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        require(width > 0 && height > 0 && maxSide > 0) { "Ungültige Bildgröße" }
        var sample = 1
        val longSide = maxOf(width, height)
        while (longSide / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}
