package de.hagi089.obelix.data.notifications

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A-56: Ablauf einer Hintergrundprüfung mit Fakes (Entscheidung 53; Korrektur Version 27). */
class ChangeCheckerTest {

    private val now = Stamp(5_000, 0)
    private val start = Stamp(1_000, 0)

    private class FakeSource(
        var records: Result<List<ChangeRecord>> = Result.success(emptyList()),
        var names: Result<Map<String, String>> = Result.success(emptyMap()),
    ) : ChangeSource {
        val queried = mutableListOf<Stamp>()
        var nameCalls = 0
        override suspend fun loadSince(since: Stamp): Result<List<ChangeRecord>> {
            queried += since
            return records
        }
        override suspend fun loadNames(): Result<Map<String, String>> {
            nameCalls++
            return names
        }
    }

    private class FakeStore(var enabled: Boolean = true) : ChangeStateStore {
        val marks = mutableMapOf<String, Stamp>()
        val checks = mutableListOf<CheckOutcome>()
        override fun isEnabled() = enabled
        override fun watermark(uid: String) = marks[uid]
        override fun setWatermark(uid: String, stamp: Stamp) { marks[uid] = stamp }
        override fun clearWatermark(uid: String) { marks.remove(uid) }
        override fun recordCheck(outcome: CheckOutcome) { checks += outcome }
    }

    private class FakePoster(var allowed: Boolean = true) : ChangePoster {
        val posted = mutableListOf<Pair<ChangeArea, List<PersonChange>>>()
        override fun canPost() = allowed
        override fun post(area: ChangeArea, changes: List<PersonChange>) { posted += area to changes }
    }

    private fun checker(source: FakeSource, store: FakeStore, poster: FakePoster) =
        ChangeChecker(source, store, poster) { now }

    @Test
    fun `ohne angemeldeten Benutzer passiert nichts`() = runBlocking {
        val source = FakeSource()
        val poster = FakePoster()
        assertEquals(CheckOutcome.NO_USER, checker(source, FakeStore(), poster).run(null))
        assertTrue(source.queried.isEmpty())
        assertTrue(poster.posted.isEmpty())
    }

    @Test
    fun `erster Lauf setzt nur den Startpunkt und meldet keine alte Historie`() = runBlocking {
        val source = FakeSource()
        val store = FakeStore()
        val poster = FakePoster()
        assertEquals(CheckOutcome.STARTED, checker(source, store, poster).run("ich"))
        assertEquals(now, store.marks["ich"])
        assertTrue(source.queried.isEmpty())
        assertTrue(poster.posted.isEmpty())
    }

    @Test
    fun `abgeschaltet liest nichts und setzt den Stand zurueck`() = runBlocking {
        val source = FakeSource()
        val store = FakeStore(enabled = false).apply { marks["ich"] = start }
        assertEquals(CheckOutcome.DISABLED, checker(source, store, FakePoster()).run("ich"))
        assertNull(store.marks["ich"])
        assertTrue(source.queried.isEmpty())
    }

    @Test
    fun `Fehler Version 26 - ohne Erlaubnis bleibt der Stand, nach dem Erlauben wird alles seitdem gemeldet`() = runBlocking {
        // Ablauf auf dem Gerät: Startpunkt beim Öffnen, erste Prüfung während der Erlaubnis-Dialog noch offen ist,
        // danach trägt ein anderer Benutzer in mehreren Bereichen etwas ein, dann wird die Erlaubnis erteilt.
        val source = FakeSource(
            records = Result.success(
                listOf(
                    ChangeRecord("calendarEntries", "1", Stamp(1_100, 0), "anna", null, null),
                    ChangeRecord("transactions", "2", Stamp(1_101, 0), "anna", null, null),
                    ChangeRecord("repairs", "3", Stamp(1_102, 0), "anna", null, null),
                    ChangeRecord("campsites", "4", Stamp(1_103, 0), "anna", null, null),
                    ChangeRecord("documents", "5", Stamp(1_104, 0), "anna", null, null),
                ),
            ),
        )
        val store = FakeStore()
        val poster = FakePoster(allowed = false)
        val checker = checker(source, store, poster)
        store.marks["ich"] = start // ensureStart beim Öffnen der App
        assertEquals(CheckOutcome.NOT_ALLOWED, checker.run("ich"))
        assertEquals(start, store.marks["ich"])
        assertTrue(source.queried.isEmpty())

        poster.allowed = true
        assertEquals(CheckOutcome.POSTED, checker.run("ich"))
        assertEquals(listOf(start), source.queried)
        assertEquals(ChangeArea.entries.toList(), poster.posted.map { it.first })
    }

    @Test
    fun `Startpunkt beim Oeffnen der App, ein vorhandener Stand bleibt`() = runBlocking {
        val store = FakeStore()
        val checker = checker(FakeSource(), store, FakePoster())
        checker.ensureStart("ich")
        assertEquals(now, store.marks["ich"])
        store.marks["ich"] = start
        checker.ensureStart("ich")
        assertEquals(start, store.marks["ich"])
        // Die erste Hintergrundprüfung setzt dann keinen neuen Startpunkt, sondern prüft ab dem Öffnen.
        assertEquals(CheckOutcome.NOTHING_NEW, checker.run("ich"))
    }

    @Test
    fun `jede Pruefung merkt sich ihr Ergebnis`() = runBlocking {
        val store = FakeStore()
        val checker = checker(FakeSource(records = Result.failure(IllegalStateException())), store, FakePoster())
        checker.run(null)
        checker.run("ich")
        checker.run("ich")
        assertEquals(listOf(CheckOutcome.NO_USER, CheckOutcome.STARTED, CheckOutcome.FAILED), store.checks)
    }

    @Test
    fun `Aenderungen anderer werden je Bereich mit Namen gemeldet und der Stand rueckt vor`() = runBlocking {
        val newest = Stamp(1_500, 7)
        val source = FakeSource(
            records = Result.success(
                listOf(
                    ChangeRecord("calendarEntries", "1", Stamp(1_200, 0), "anna", null, null),
                    ChangeRecord("repairs", "2", Stamp(10, 0), "ich", newest, "bert"),
                    ChangeRecord("transactions", "3", Stamp(1_300, 0), "ich", null, null),
                ),
            ),
            names = Result.success(mapOf("anna" to "Benutzer A", "bert" to "Benutzer B")),
        )
        val store = FakeStore().apply { marks["ich"] = start }
        val poster = FakePoster()
        assertEquals(CheckOutcome.POSTED, checker(source, store, poster).run("ich"))
        assertEquals(listOf(start), source.queried)
        assertEquals(
            listOf(
                ChangeArea.CALENDAR to listOf(PersonChange("anna", "Benutzer A", ChangeAction.CREATED)),
                ChangeArea.TASKS to listOf(PersonChange("bert", "Benutzer B", ChangeAction.UPDATED)),
            ),
            poster.posted,
        )
        assertEquals(newest, store.marks["ich"])
    }

    @Test
    fun `nur eigene Aenderungen melden nichts, laden keine Namen, Stand rueckt trotzdem vor`() = runBlocking {
        val source = FakeSource(records = Result.success(listOf(ChangeRecord("campsites", "1", Stamp(1_100, 0), "ich", null, null))))
        val store = FakeStore().apply { marks["ich"] = start }
        val poster = FakePoster()
        assertEquals(CheckOutcome.NOTHING_NEW, checker(source, store, poster).run("ich"))
        assertTrue(poster.posted.isEmpty())
        assertEquals(0, source.nameCalls)
        assertEquals(Stamp(1_100, 0), store.marks["ich"])
    }

    @Test
    fun `Lesefehler meldet nichts und laesst den Stand stehen, damit nichts verloren geht`() = runBlocking {
        val source = FakeSource(records = Result.failure(IllegalStateException("offline")))
        val store = FakeStore().apply { marks["ich"] = start }
        val poster = FakePoster()
        assertEquals(CheckOutcome.FAILED, checker(source, store, poster).run("ich"))
        assertTrue(poster.posted.isEmpty())
        assertEquals(start, store.marks["ich"])
    }

    @Test
    fun `fehlgeschlagene Namen melden trotzdem, ohne Namen`() = runBlocking {
        val source = FakeSource(
            records = Result.success(listOf(ChangeRecord("calendarEntries", "1", Stamp(1_200, 0), "anna", null, null))),
            names = Result.failure(IllegalStateException("offline")),
        )
        val store = FakeStore().apply { marks["ich"] = start }
        val poster = FakePoster()
        assertEquals(CheckOutcome.POSTED, checker(source, store, poster).run("ich"))
        assertEquals(listOf(PersonChange("anna", null, ChangeAction.CREATED)), poster.posted.single().second)
    }

    @Test
    fun `Stand gilt je Benutzer`() = runBlocking {
        val store = FakeStore().apply { marks["anderer"] = start }
        assertEquals(CheckOutcome.STARTED, checker(FakeSource(), store, FakePoster()).run("ich"))
        assertEquals(start, store.marks["anderer"])
        assertEquals(now, store.marks["ich"])
    }
}
