# OBELIX – App verteilen und aktualisieren

Ziel: Neue Versionen lassen sich **als Update** installieren, ohne die App vorher zu deinstallieren. Dafür muss jede APK mit **demselben Schlüssel** signiert sein und eine **höhere Versionsnummer** haben (`versionCode` in `app/build.gradle.kts`, wird bei jedem Deployment erhöht).

## 1. Einmalig: festen Schlüssel in GitHub hinterlegen

Der Schlüssel (`obelix-signing.p12`) und sein Passwort wurden einmalig erzeugt und dem Projektinhaber übergeben. **Sie liegen nie im Repository** (`.gitignore`: `*.p12`, `obelix-signing*`).

1. GitHub → Repository `Hagi089/Obelix` → **Settings → Secrets and variables → Actions → New repository secret**.
2. Secret `OBELIX_KEYSTORE_BASE64`: den Inhalt der Datei `keystore.base64` (eine lange Zeile) einfügen.
3. Secret `OBELIX_KEYSTORE_PASSWORD`: das Passwort einfügen.
4. **Actions → Build → letzten Lauf auf `main` → „Re-run all jobs“** (oder ein neuer Push). Danach liegt im Lauf das Artefakt **`obelix-apk`** (fest signiert, zum Verteilen); die Anmerkung „Signatur der APK“ zeigt den SHA-256 des Zertifikats.

Erwartete Fingerabdrücke des Zertifikats (öffentlich, kein Geheimnis):
- SHA-256: `CF:91:1C:E7:6B:03:E5:4F:62:60:9B:5D:22:2A:C2:E3:BF:4D:AA:26:7D:75:EE:83:76:66:05:11:01:7A:3B:4E`
- SHA-1: `31:69:ED:ED:32:1F:27:B7:A0:56:8B:75:A5:23:6B:7A:16:53:D4:34`

**Schlüssel und Passwort sicher aufbewahren** (z. B. Passwort-Manager und eine zweite Kopie offline). Geht der Schlüssel verloren, kann keine weitere Version mehr als Update installiert werden; dann müssten alle die App einmal deinstallieren (die Daten liegen in Firebase und gehen dabei nicht verloren).

Fehlen die Secrets, baut die CI nur eine Debug-APK mit zufälligem Schlüssel (Artefakt `obelix-debug-apk`, Warnung „Kein fester Signaturschlüssel“). Sie ist nur zum Testen gedacht.

## 2. Einmalig: Umstieg auf die fest signierte App

Alle bisher installierten APKs hatten einen zufälligen Schlüssel. **Ein letztes Mal** muss jeder Benutzer die alte App deinstallieren und die APK aus `obelix-apk` installieren. Die Daten bleiben erhalten (sie liegen in Firebase); danach einmal neu anmelden.

## 3. Jede neue Version

1. Änderung umsetzen, `versionCode` +1 und `versionName` (zweistellig) erhöhen, auf `main` pushen.
2. Im neuen Lauf das Artefakt `obelix-apk` herunterladen (ZIP mit `app-release.apk`) und an die Familie weitergeben.
3. Auf dem Smartphone die APK öffnen → **„Aktualisieren“**. Kein Deinstallieren nötig.

Wenn Android „App nicht installiert“ meldet: Entweder ist die Versionsnummer nicht höher als die installierte oder die APK stammt aus einem Lauf ohne festen Schlüssel (Artefakt `obelix-debug-apk`). In der Lauf-Anmerkung „Signatur der APK“ muss der SHA-256 oben stehen.

## 4. Empfohlen: API-Schlüssel auf die App beschränken

Mit festem Schlüssel lässt sich der Firebase-API-Schlüssel auf diese App beschränken (Google Cloud Console → Projekt `obelix-daf7c` → APIs & Dienste → Anmeldedaten → „Android key (auto created by Firebase)“ → Anwendungseinschränkungen: **Android-Apps**, Paketname `de.hagi089.obelix`, SHA-1 von oben). **Erst danach** nur noch fest signierte APKs verwenden: Debug-APKs mit zufälligem Schlüssel können sich dann nicht mehr anmelden. Nach der Änderung einmal An- und Abmelden testen. Das ist eine zusätzliche Hürde gegen Missbrauch des öffentlichen Schlüssels; den Schutz der Daten leisten weiterhin die Firestore-Regeln.
