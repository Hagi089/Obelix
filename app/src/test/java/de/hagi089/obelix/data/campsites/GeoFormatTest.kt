package de.hagi089.obelix.data.campsites

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GeoFormatTest {

    private lateinit var previous: Locale

    @Before
    fun useGermanLocale() {
        // Die Formate sind von der Gerätesprache unabhängig; mit deutscher Standardsprache würde ein Fehler sichtbar.
        previous = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
    }

    @After
    fun restoreLocale() = Locale.setDefault(previous)

    @Test
    fun display_isGerman_withHemispheres() {
        assertEquals("48,13715° N, 11,57612° O", GeoFormat.display(48.137154, 11.576124))
        assertEquals("33,86880° S, 151,20930° O", GeoFormat.display(-33.8688, 151.2093))
        assertEquals("40,71280° N, 74,00600° W", GeoFormat.display(40.7128, -74.006))
    }

    @Test
    fun plain_usesDecimalPoint_andSixDigits() {
        assertEquals("48.137154,11.576124", GeoFormat.plain(48.137154, 11.576124))
        assertEquals("-33.868800,151.209300", GeoFormat.plain(-33.8688, 151.2093))
        assertEquals("0.000000,0.000000", GeoFormat.plain(0.0, 0.0))
    }

    @Test
    fun navigationUri_startsGoogleNavigation() {
        assertEquals("google.navigation:q=48.137154,11.576124&mode=d", GeoFormat.navigationUri(48.137154, 11.576124))
    }

    @Test
    fun geoUri_withoutLabel() {
        assertEquals("geo:0,0?q=48.137154,11.576124", GeoFormat.geoUri(48.137154, 11.576124, null))
        assertEquals("geo:0,0?q=48.137154,11.576124", GeoFormat.geoUri(48.137154, 11.576124, "   "))
        assertEquals("geo:0,0?q=48.137154,11.576124", GeoFormat.geoUri(48.137154, 11.576124, "()"))
    }

    @Test
    fun geoUri_withLabel_encodesSpecialCharacters() {
        assertEquals(
            "geo:0,0?q=48.137154,11.576124(Seeblick%20am%20See)",
            GeoFormat.geoUri(48.137154, 11.576124, "Seeblick am See"),
        )
        // Klammern im Namen würden die Bezeichnung beenden: sie werden entfernt; Umlaute und & werden codiert
        assertEquals(
            "geo:0,0?q=48.137154,11.576124(K%C3%BCste%20%26%20Strand)",
            GeoFormat.geoUri(48.137154, 11.576124, "Küste & Strand"),
        )
        assertEquals(
            "geo:0,0?q=48.137154,11.576124(Platz%20%201)",
            GeoFormat.geoUri(48.137154, 11.576124, "Platz (1)"),
        )
    }

    @Test
    fun encodeDecode_roundTrip() {
        val position = GeoPosition(48.137154, 11.576124, 12.5f)
        val text = GeoFormat.encode(position)
        assertEquals("48.137154,11.576124,12.5", text)
        assertEquals(position, GeoFormat.decode(text))
        val noAccuracy = GeoPosition(-33.8688, 151.2093)
        assertEquals("-33.868800,151.209300", GeoFormat.encode(noAccuracy))
        assertEquals(noAccuracy, GeoFormat.decode(GeoFormat.encode(noAccuracy)))
    }

    @Test
    fun decode_rejectsInvalidText() {
        assertNull(GeoFormat.decode(null))
        assertNull(GeoFormat.decode(""))
        assertNull(GeoFormat.decode("abc"))
        assertNull(GeoFormat.decode("48.1"))
        assertNull(GeoFormat.decode("48.1,11.5,3,4"))
        assertNull(GeoFormat.decode("48,1,11,5")) // deutsche Kommas machen daraus vier Teile
        assertNull(GeoFormat.decode("91.0,11.5"))
        assertNull(GeoFormat.decode("48.1,181.0"))
        assertNull(GeoFormat.decode("NaN,11.5"))
        assertNull(GeoFormat.decode("48.1,11.5,-1.0"))
        assertNull(GeoFormat.decode("48.1,11.5,x"))
        assertNotNull(GeoFormat.decode("90.0,180.0"))
    }
}
