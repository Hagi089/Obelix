// Automatische Tests der Firestore-Sicherheitsregeln (firebase/firestore.rules).
// Läuft im Firebase-Emulator:  cd firebase && firebase emulators:exec --only firestore --project demo-obelix "npm --prefix rules-tests test"
// Testfall-IDs (R-xx) siehe docs/TESTFAELLE.md.

import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  Bytes,
  Timestamp,
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  runTransaction,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';

// ---------- Testdaten (nur für den Emulator, keine echten Daten) ----------
const CODE = 'ABCDEFGH23456789'; // gültiger Zugangscode
const CODE_NEW = 'KMNPQRTV23456789'; // Code nach Erneuerung

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-obelix',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await (testEnv.clearFirestore ?? testEnv.clearFirestoreData).call(testEnv);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const now = Timestamp.now();
    await setDoc(doc(db, 'config/access'), { code: CODE, updatedBy: 'admin', updatedAt: now });
    await setDoc(doc(db, 'users/admin'), { displayName: 'Admin', role: 'ADMIN', accessCode: CODE, createdAt: now });
    await setDoc(doc(db, 'users/member'), { displayName: 'Mitglied', role: 'MEMBER', accessCode: CODE, createdAt: now });
  });
});

const as = (uid) => testEnv.authenticatedContext(uid).firestore();
const anon = () => testEnv.unauthenticatedContext().firestore();

/** Was die App bei der Registrierung schreibt (UserRepository.register). */
function registration(o = {}) {
  return { displayName: 'Neu', role: 'MEMBER', accessCode: CODE, createdAt: serverTimestamp(), ...o };
}

function newCode(uid, code) {
  return { code, updatedBy: uid, updatedAt: serverTimestamp() };
}

// =====================================================================
describe('R-01 Ohne Anmeldung kein Zugriff', () => {
  it('R-01a nichts lesbar', async () => {
    const db = anon();
    await assertFails(getDoc(doc(db, 'users/admin')));
    await assertFails(getDocs(collection(db, 'users')));
    await assertFails(getDoc(doc(db, 'config/access')));
  });

  it('R-01b nichts schreibbar, keine Registrierung', async () => {
    const db = anon();
    await assertFails(setDoc(doc(db, 'users/x'), registration()));
    await assertFails(updateDoc(doc(db, 'users/member'), { role: 'ADMIN' }));
    await assertFails(setDoc(doc(db, 'config/access'), newCode('x', CODE_NEW)));
  });
});

// =====================================================================
describe('R-02 Registrierung nur mit gültigem Zugangscode', () => {
  it('R-02a gültiger Code: Benutzerdokument entsteht, danach Zugriff', async () => {
    const db = as('newbie');
    await assertFails(getDocs(collection(db, 'users'))); // vorher: kein Zugriff
    await assertSucceeds(setDoc(doc(db, 'users/newbie'), registration()));
    await assertSucceeds(getDocs(collection(db, 'users')));
    await assertSucceeds(getDoc(doc(db, 'users/admin')));
  });

  it('R-02b derselbe Code gilt für mehrere Personen', async () => {
    await assertSucceeds(setDoc(doc(as('p1'), 'users/p1'), registration()));
    await assertSucceeds(setDoc(doc(as('p2'), 'users/p2'), registration()));
  });

  it('R-02c falscher Code', async () => {
    await assertFails(setDoc(doc(as('newbie'), 'users/newbie'), registration({ accessCode: 'ZZZZZZZZ22222222' })));
  });

  it('R-02d ohne Code, leerer Code, falsches Format', async () => {
    const { accessCode, ...withoutCode } = registration();
    await assertFails(setDoc(doc(as('n1'), 'users/n1'), withoutCode));
    await assertFails(setDoc(doc(as('n2'), 'users/n2'), registration({ accessCode: '' })));
    await assertFails(setDoc(doc(as('n3'), 'users/n3'), registration({ accessCode: 'abcdefgh23456789' })));
  });

  it('R-02e erneuerter Code: alter ungültig, neuer gültig', async () => {
    await assertSucceeds(setDoc(doc(as('admin'), 'config/access'), newCode('admin', CODE_NEW)));
    await assertFails(setDoc(doc(as('n1'), 'users/n1'), registration({ accessCode: CODE })));
    await assertSucceeds(setDoc(doc(as('n2'), 'users/n2'), registration({ accessCode: CODE_NEW })));
  });

  it('R-02f gesperrte Registrierung (code = null)', async () => {
    await assertSucceeds(setDoc(doc(as('admin'), 'config/access'), newCode('admin', null)));
    await assertFails(setDoc(doc(as('n1'), 'users/n1'), registration()));
  });

  it('R-02g ohne config/access ist keine Registrierung möglich', async () => {
    await testEnv.withSecurityRulesDisabled((ctx) => deleteDoc(doc(ctx.firestore(), 'config/access')));
    await assertFails(setDoc(doc(as('n1'), 'users/n1'), registration()));
  });

  it('R-02h Registrierung als ADMIN verboten (keine Selbst-Beförderung)', async () => {
    await assertFails(setDoc(doc(as('newbie'), 'users/newbie'), registration({ role: 'ADMIN' })));
  });

  it('R-02i nicht für andere registrieren', async () => {
    await assertFails(setDoc(doc(as('newbie'), 'users/other'), registration()));
  });

  it('R-02j Zusatzfelder, leerer/zu langer Name, falsche Zeit verboten', async () => {
    await assertFails(setDoc(doc(as('n1'), 'users/n1'), registration({ extra: 1 })));
    await assertFails(setDoc(doc(as('n2'), 'users/n2'), registration({ displayName: '' })));
    await assertFails(setDoc(doc(as('n3'), 'users/n3'), registration({ displayName: 'x'.repeat(51) })));
    await assertFails(setDoc(doc(as('n4'), 'users/n4'), registration({ createdAt: Timestamp.fromMillis(0) })));
  });

  it('R-02k bestehendes Benutzerdokument kann nicht neu angelegt (überschrieben) werden', async () => {
    await assertFails(setDoc(doc(as('member'), 'users/member'), registration({ displayName: 'Mitglied' })));
  });

  it('R-02l Registrierung per Transaktion (wie in der App)', async () => {
    const db = as('newbie');
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'users/newbie'), registration());
    }));
  });
});

// =====================================================================
describe('R-03 Angemeldet ohne Benutzerdokument: kein Zugriff', () => {
  it('R-03a Konto ohne gültigen Code sieht und ändert nichts', async () => {
    const db = as('stranger');
    await assertFails(getDoc(doc(db, 'users/admin')));
    await assertFails(getDocs(collection(db, 'users')));
    await assertFails(getDoc(doc(db, 'config/access')));
    await assertFails(updateDoc(doc(db, 'users/member'), { displayName: 'X' }));
    await assertFails(setDoc(doc(db, 'config/access'), newCode('stranger', CODE_NEW)));
  });

  it('R-03d eigenes (noch nicht vorhandenes) Benutzerdokument ist abfragbar, fremde nicht', async () => {
    const db = as('stranger');
    const snap = await assertSucceeds(getDoc(doc(db, 'users/stranger')));
    if (snap.exists()) throw new Error('darf nicht existieren');
    await assertFails(getDoc(doc(db, 'users/member')));
  });

  it('R-03b entfernter Benutzer hat keinen Zugriff mehr', async () => {
    await assertSucceeds(deleteDoc(doc(as('admin'), 'users/member')));
    await assertFails(getDocs(collection(as('member'), 'users')));
  });

  it('R-03c noch nicht freigegebene Sammlungen sind für alle gesperrt', async () => {
    for (const uid of ['admin', 'member']) {
      const db = as(uid);
      // Seit Phase 10 ist jede fachliche Sammlung freigegeben (documents zuletzt): Beispiele sind jetzt Sammlungen,
      // die es in der App nicht gibt. Wird künftig eine neue Sammlung freigegeben, hier keine ihrer Namen verwenden.
      await assertFails(setDoc(doc(db, 'unbekannt/t1'), { amountCents: 100 }));
      await assertFails(getDoc(doc(db, 'unbekannt/t1')));
      await assertFails(setDoc(doc(db, 'irgendwas/x'), { a: 1 }));
      await assertFails(setDoc(doc(db, 'config/other'), { a: 1 }));
    }
  });
});

// =====================================================================
describe('R-04 Rollen ADMIN / MEMBER', () => {
  it('R-04a MEMBER kann sich nicht selbst befördern', async () => {
    const db = as('member');
    await assertFails(updateDoc(doc(db, 'users/member'), { role: 'ADMIN' }));
    await assertFails(updateDoc(doc(db, 'users/member'), { displayName: 'X', role: 'ADMIN' }));
    await assertFails(setDoc(doc(db, 'users/member'), registration({ role: 'ADMIN' })));
  });

  it('R-04b MEMBER darf andere nicht ändern oder entfernen', async () => {
    const db = as('member');
    await assertFails(updateDoc(doc(db, 'users/admin'), { role: 'MEMBER' }));
    await assertFails(updateDoc(doc(db, 'users/admin'), { displayName: 'X' }));
    await assertFails(deleteDoc(doc(db, 'users/admin')));
  });

  it('R-04c MEMBER darf den Zugangscode weder lesen noch ändern', async () => {
    const db = as('member');
    await assertFails(getDoc(doc(db, 'config/access')));
    await assertFails(setDoc(doc(db, 'config/access'), newCode('member', CODE_NEW)));
  });

  it('R-04d jeder ändert seinen eigenen Namen, sonst nichts', async () => {
    const db = as('member');
    await assertSucceeds(updateDoc(doc(db, 'users/member'), { displayName: 'Neuer Name' }));
    await assertFails(updateDoc(doc(db, 'users/member'), { displayName: '' }));
    await assertFails(updateDoc(doc(db, 'users/member'), { accessCode: CODE_NEW }));
  });

  it('R-04i Zugangscode im eigenen Benutzerdokument: nur der Besitzer darf ihn entfernen, nie ändern oder setzen', async () => {
    const db = as('member');
    // andere dürfen ihn nicht entfernen (auch der ADMIN nicht: der ändert nur Rollen)
    await assertFails(updateDoc(doc(as('admin'), 'users/member'), { accessCode: deleteField() }));
    // ändern verboten
    await assertFails(updateDoc(doc(db, 'users/member'), { accessCode: CODE_NEW }));
    // entfernen erlaubt (so macht es die App), auch zusammen mit einer Namensänderung
    await assertSucceeds(updateDoc(doc(db, 'users/member'), { accessCode: deleteField() }));
    // danach nicht wieder setzbar; der Name bleibt änderbar
    await assertFails(updateDoc(doc(db, 'users/member'), { accessCode: CODE }));
    await assertSucceeds(updateDoc(doc(db, 'users/member'), { displayName: 'Mitglied 2' }));
    await assertSucceeds(updateDoc(doc(as('admin'), 'users/admin'), { displayName: 'Admin 2', accessCode: deleteField() }));
    // ohne Code bleibt der Zugriff bestehen
    await assertSucceeds(getDocs(collection(db, 'transactions')));
    // Selbst-Beförderung bleibt verboten, auch zusammen mit dem Entfernen des Codes
    await assertFails(updateDoc(doc(as('admin'), 'users/admin'), { role: 'MEMBER', accessCode: deleteField() }));
    await assertFails(updateDoc(doc(db, 'users/member'), { role: 'ADMIN' }));
  });

  it('R-04e ADMIN befördert und degradiert andere', async () => {
    const db = as('admin');
    await assertSucceeds(updateDoc(doc(db, 'users/member'), { role: 'ADMIN' }));
    await assertSucceeds(updateDoc(doc(db, 'users/member'), { role: 'MEMBER' }));
    await assertFails(updateDoc(doc(db, 'users/member'), { role: 'CHEF' }));
    await assertFails(updateDoc(doc(db, 'users/member'), { displayName: 'Fremd' }));
  });

  it('R-04f ADMIN kann sich nicht selbst degradieren oder entfernen', async () => {
    const db = as('admin');
    await assertFails(updateDoc(doc(db, 'users/admin'), { role: 'MEMBER' }));
    await assertFails(deleteDoc(doc(db, 'users/admin')));
    await assertSucceeds(updateDoc(doc(db, 'users/admin'), { displayName: 'Admin neu' }));
  });

  it('R-04g zwei ADMINs: der degradierte hat keine Admin-Rechte mehr', async () => {
    await assertSucceeds(updateDoc(doc(as('admin'), 'users/member'), { role: 'ADMIN' }));
    await assertSucceeds(updateDoc(doc(as('member'), 'users/admin'), { role: 'MEMBER' }));
    await assertFails(updateDoc(doc(as('admin'), 'users/member'), { role: 'MEMBER' }));
    await assertFails(getDoc(doc(as('admin'), 'config/access')));
  });

  it('R-04h ADMIN entfernt einen Benutzer', async () => {
    await assertSucceeds(deleteDoc(doc(as('admin'), 'users/member')));
  });
});

// =====================================================================
describe('R-05 Zugangscode verwalten (nur ADMIN)', () => {
  it('R-05a ADMIN liest und erneuert den Code', async () => {
    const db = as('admin');
    const snap = await assertSucceeds(getDoc(doc(db, 'config/access')));
    if (snap.data().code !== CODE) throw new Error('falscher Code');
    await assertSucceeds(setDoc(doc(db, 'config/access'), newCode('admin', CODE_NEW)));
  });

  it('R-05b falsches Format, Zusatzfelder, falscher Bearbeiter verboten', async () => {
    const db = as('admin');
    await assertFails(setDoc(doc(db, 'config/access'), newCode('admin', 'abc')));
    await assertFails(setDoc(doc(db, 'config/access'), newCode('admin', 'AAAA1111AAAA1111')));
    await assertFails(setDoc(doc(db, 'config/access'), { ...newCode('admin', CODE_NEW), extra: 1 }));
    await assertFails(setDoc(doc(db, 'config/access'), newCode('member', CODE_NEW)));
  });

  it('R-05c Code löschen verboten', async () => {
    await assertFails(deleteDoc(doc(as('admin'), 'config/access')));
  });

  it('R-05d Code erneuern per Transaktion (wie in der App)', async () => {
    const db = as('admin');
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'config/access'), newCode('admin', CODE_NEW));
    }));
  });
});

// =====================================================================
// Phase 4: Finanzen. Testdaten nur für den Emulator.

/** Was die App beim Anlegen einer Buchung schreibt (FinanceRepository.create). */
function booking(uid, o = {}) {
  return {
    type: 'EXPENSE',
    date: '2026-09-30',
    amountCents: 4700,
    categoryId: 'cat1',
    paidByUid: uid,
    settlement: 'OPEN',
    description: 'Diesel',
    comment: '',
    createdAt: serverTimestamp(),
    createdBy: uid,
    ...o,
  };
}

/** Entfernt Felder (undefined ist in Firestore-Dokumenten nicht erlaubt). */
function without(obj, ...keys) {
  const copy = { ...obj };
  for (const key of keys) delete copy[key];
  return copy;
}

async function seedBooking(id = 'b1', o = {}) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), `transactions/${id}`), {
      ...without(booking('member'), 'createdAt'),
      createdAt: Timestamp.now(),
      ...o,
    });
  });
}

function edit(uid, o = {}) {
  return { description: 'Geändert', updatedAt: serverTimestamp(), updatedBy: uid, ...o };
}

describe('R-06 Buchungen (transactions)', () => {
  it('R-06a MEMBER und ADMIN dürfen Ausgaben und Einnahmen anlegen und lesen', async () => {
    for (const uid of ['member', 'admin']) {
      const db = as(uid);
      await assertSucceeds(setDoc(doc(db, `transactions/e-${uid}`), booking(uid)));
      await assertSucceeds(setDoc(doc(db, `transactions/i-${uid}`), booking(uid, { type: 'INCOME', settlement: 'SETTLED' })));
      await assertSucceeds(getDoc(doc(db, `transactions/e-${uid}`)));
    }
    const list = await assertSucceeds(getDocs(collection(as('member'), 'transactions')));
    if (list.size !== 4) throw new Error(`erwartet 4 Buchungen, gefunden ${list.size}`);
  });

  it('R-06b ohne Anmeldung, ohne Freischaltung und als entfernter Benutzer kein Zugriff', async () => {
    await seedBooking();
    for (const db of [anon(), as('nobody')]) {
      await assertFails(getDoc(doc(db, 'transactions/b1')));
      await assertFails(getDocs(collection(db, 'transactions')));
      await assertFails(setDoc(doc(db, 'transactions/neu'), booking('nobody')));
      await assertFails(updateDoc(doc(db, 'transactions/b1'), edit('nobody')));
      await assertFails(deleteDoc(doc(db, 'transactions/b1')));
    }
  });

  it('R-06c Betrag: muss eine ganze Zahl in Cent größer als 0 und höchstens 1.000.000,00 sein', async () => {
    const db = as('member');
    for (const amountCents of [0, -100, 12.5, '47', null, 100000001]) {
      await assertFails(setDoc(doc(db, 'transactions/x'), booking('member', { amountCents })));
    }
    await assertSucceeds(setDoc(doc(db, 'transactions/ok1'), booking('member', { amountCents: 1 })));
    await assertSucceeds(setDoc(doc(db, 'transactions/ok2'), booking('member', { amountCents: 100000000 })));
  });

  it('R-06d Pflichtfelder, Formate und Werte werden geprüft', async () => {
    const db = as('member');
    const bad = [
      without(booking('member'), 'description'),
      without(booking('member'), 'comment'),
      without(booking('member'), 'categoryId'),
      without(booking('member'), 'date'),
      booking('member', { description: '' }),
      booking('member', { description: 'x'.repeat(201) }),
      booking('member', { comment: 'x'.repeat(501) }),
      booking('member', { date: '30.09.2026' }),
      booking('member', { date: '2026-9-30' }),
      booking('member', { type: 'TRANSFER' }),
      booking('member', { settlement: 'PAID' }),
      booking('member', { categoryId: '' }),
      booking('member', { extra: 1 }),
      booking('member', { importRef: 'x'.repeat(21) }),
    ];
    for (const data of bad) await assertFails(setDoc(doc(db, 'transactions/x'), data));
    await assertSucceeds(setDoc(doc(db, 'transactions/ok'), booking('member', { comment: 'x'.repeat(500), description: 'y'.repeat(200) })));
  });

  it('R-06e Ausgabe braucht einen Zahler; Einnahme nicht; Einnahmen sind immer SETTLED', async () => {
    const db = as('member');
    await assertFails(setDoc(doc(db, 'transactions/x'), without(booking('member'), 'paidByUid')));
    await assertFails(setDoc(doc(db, 'transactions/x'), booking('member', { paidByUid: '' })));
    await assertSucceeds(setDoc(doc(db, 'transactions/i1'), without(booking('member', { type: 'INCOME', settlement: 'SETTLED' }), 'paidByUid')));
    await assertSucceeds(setDoc(doc(db, 'transactions/i2'), booking('member', { paidByUid: 'admin', type: 'INCOME', settlement: 'SETTLED' })));
    await assertFails(setDoc(doc(db, 'transactions/x'), booking('member', { type: 'INCOME', settlement: 'OPEN' })));
    await assertFails(setDoc(doc(db, 'transactions/x'), booking('member', { type: 'INCOME', settlement: 'SPONSORED' })));
    await assertSucceeds(setDoc(doc(db, 'transactions/s1'), booking('member', { paidByUid: 'admin', settlement: 'SPONSORED' })));
    await assertSucceeds(setDoc(doc(db, 'transactions/s2'), booking('member', { paidByUid: 'admin', settlement: 'SETTLED' })));
  });

  it('R-06f Audit: createdBy und createdAt lassen sich nicht fälschen', async () => {
    const db = as('member');
    await assertFails(setDoc(doc(db, 'transactions/x'), booking('member', { createdBy: 'admin' })));
    await assertFails(setDoc(doc(db, 'transactions/x'), booking('member', { createdAt: Timestamp.fromDate(new Date('2020-01-01')) })));
    await assertFails(setDoc(doc(db, 'transactions/x'), booking('member', { updatedBy: 'member', updatedAt: serverTimestamp() })));
  });

  it('R-06g Ändern: jeder Benutzer darf, updatedBy/updatedAt Pflicht, Herkunft unveränderlich', async () => {
    await seedBooking('b1', { importRef: 'xl-15' });
    // Fremde Buchung ändern (jeder darf), vollständig geprüft wie in der App (Feldaktualisierung).
    await assertSucceeds(updateDoc(doc(as('admin'), 'transactions/b1'), edit('admin', { amountCents: 5000 })));
    await assertSucceeds(updateDoc(doc(as('member'), 'transactions/b1'), edit('member', {
      settlement: 'SETTLED', settledAt: serverTimestamp(), settledBy: 'member',
    })));
    // Pflicht: updatedBy = eigene uid, updatedAt = Serverzeit
    await assertFails(updateDoc(doc(as('member'), 'transactions/b1'), { description: 'ohne Audit' }));
    await assertFails(updateDoc(doc(as('member'), 'transactions/b1'), edit('admin')));
    await assertFails(updateDoc(doc(as('member'), 'transactions/b1'), edit('member', { updatedAt: Timestamp.now() })));
    // Unveränderlich: createdBy, createdAt, importRef
    await assertFails(updateDoc(doc(as('member'), 'transactions/b1'), edit('member', { createdBy: 'admin' })));
    await assertFails(updateDoc(doc(as('member'), 'transactions/b1'), edit('member', { createdAt: serverTimestamp() })));
    await assertFails(updateDoc(doc(as('member'), 'transactions/b1'), edit('member', { importRef: 'xl-99' })));
    // Ungültige Werte auch beim Ändern verboten
    await assertFails(updateDoc(doc(as('member'), 'transactions/b1'), edit('member', { amountCents: 0 })));
    await assertFails(updateDoc(doc(as('member'), 'transactions/b1'), edit('member', { settlement: 'PAID' })));
    await assertFails(updateDoc(doc(as('member'), 'transactions/b1'), edit('member', { type: 'INCOME', settlement: 'OPEN' })));
  });

  it('R-06h Löschen: jeder freigeschaltete Benutzer', async () => {
    await seedBooking('b1');
    await seedBooking('b2');
    await assertSucceeds(deleteDoc(doc(as('member'), 'transactions/b1')));
    await assertSucceeds(deleteDoc(doc(as('admin'), 'transactions/b2')));
  });

  it('R-06i Anlegen und Ändern als Transaktion (wie in der App)', async () => {
    const db = as('member');
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'transactions/t1'), booking('member'));
    }));
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.update(doc(db, 'transactions/t1'), edit('member', { settlement: 'SETTLED', settledAt: serverTimestamp(), settledBy: 'member' }));
    }));
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.delete(doc(db, 'transactions/t1'));
    }));
  });

  it('R-06j Import: 300 Buchungen mit festen IDs in Blöcken à 10 (Blockgröße der App)', async () => {
    const db = as('admin');
    // Firestore begrenzt die Regelabfragen (exists) je Transaktion auf 20; die App schreibt deshalb 10 je Block.
    for (let start = 0; start < 300; start += 10) {
      await assertSucceeds(runTransaction(db, async (tx) => {
        for (let i = start; i < start + 10; i++) {
          tx.set(doc(db, `transactions/xl-${i}`), booking('admin', { paidByUid: 'member', importRef: `xl-${i}`, settlement: 'SETTLED' }));
        }
      }));
    }
    const list = await assertSucceeds(getDocs(collection(db, 'transactions')));
    if (list.size !== 300) throw new Error(`erwartet 300 Buchungen, gefunden ${list.size}`);
    // Wiederholter Import darf bestehende Buchungen nicht überschreiben
    await assertFails(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'transactions/xl-0'), booking('admin', { paidByUid: 'member', importRef: 'xl-0', amountCents: 1 }));
    }));
  });
});

// =====================================================================
describe('R-07 Kategorien (categories)', () => {
  const category = (uid, o = {}) => ({ name: 'Inventar', active: true, createdAt: serverTimestamp(), createdBy: uid, ...o });
  const rename = (uid, o = {}) => ({ name: 'Neu', updatedAt: serverTimestamp(), updatedBy: uid, ...o });

  async function seedCategory(id = 'c1') {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `categories/${id}`), {
        name: 'Inventar', active: true, createdAt: Timestamp.now(), createdBy: 'admin',
      });
    });
  }

  it('R-07a jeder freigeschaltete Benutzer liest und legt Kategorien an', async () => {
    await assertSucceeds(setDoc(doc(as('member'), 'categories/c1'), category('member')));
    await assertSucceeds(setDoc(doc(as('admin'), 'categories/c2'), category('admin', { name: 'Elektro' })));
    const list = await assertSucceeds(getDocs(collection(as('member'), 'categories')));
    if (list.size !== 2) throw new Error(`erwartet 2 Kategorien, gefunden ${list.size}`);
    await assertFails(getDocs(collection(anon(), 'categories')));
    await assertFails(getDocs(collection(as('nobody'), 'categories')));
    await assertFails(setDoc(doc(as('nobody'), 'categories/c3'), category('nobody')));
  });

  it('R-07b Validierung beim Anlegen (Name, active, Audit, Zusatzfelder)', async () => {
    const db = as('member');
    await assertFails(setDoc(doc(db, 'categories/x'), category('member', { name: '' })));
    await assertFails(setDoc(doc(db, 'categories/x'), category('member', { name: 'x'.repeat(51) })));
    await assertFails(setDoc(doc(db, 'categories/x'), category('member', { active: false })));
    await assertFails(setDoc(doc(db, 'categories/x'), category('member', { createdBy: 'admin' })));
    await assertFails(setDoc(doc(db, 'categories/x'), category('member', { type: 'EXPENSE' })));
    await assertFails(setDoc(doc(db, 'categories/x'), category('member', { createdAt: Timestamp.now() })));
  });

  it('R-07c umbenennen und (de)aktivieren nur ADMIN', async () => {
    await seedCategory();
    await assertFails(updateDoc(doc(as('member'), 'categories/c1'), rename('member')));
    await assertFails(updateDoc(doc(as('member'), 'categories/c1'), rename('member', { active: false })));
    await assertSucceeds(updateDoc(doc(as('admin'), 'categories/c1'), rename('admin')));
    await assertSucceeds(updateDoc(doc(as('admin'), 'categories/c1'), rename('admin', { name: 'Neu', active: false })));
    await assertFails(updateDoc(doc(as('admin'), 'categories/c1'), rename('admin', { name: '' })));
    await assertFails(updateDoc(doc(as('admin'), 'categories/c1'), rename('admin', { createdBy: 'member' })));
    await assertFails(updateDoc(doc(as('admin'), 'categories/c1'), { name: 'ohne Audit' }));
  });

  it('R-07d Kategorien werden nie gelöscht', async () => {
    await seedCategory();
    await assertFails(deleteDoc(doc(as('admin'), 'categories/c1')));
    await assertFails(deleteDoc(doc(as('member'), 'categories/c1')));
  });

  it('R-07e Import legt mehrere Kategorien in einer Transaktion an', async () => {
    const db = as('admin');
    await assertSucceeds(runTransaction(db, async (tx) => {
      for (let i = 0; i < 12; i++) tx.set(doc(db, `categories/imp-${i}`), category('admin', { name: `K${i}` }));
    }));
  });
});

// =====================================================================
// Phase 5: geplante Ausgaben. Testdaten nur für den Emulator.

/** Was die App beim Anlegen einer geplanten Ausgabe schreibt (PlannedExpenseRepository.create). */
function planned(uid, o = {}) {
  return {
    title: 'Neue Batterie',
    estimatedAmountCents: 50000,
    plannedDate: '2026-09-30',
    status: 'PLANNED',
    comment: '',
    createdAt: serverTimestamp(),
    createdBy: uid,
    ...o,
  };
}

async function seedPlanned(id = 'p1', o = {}) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), `plannedExpenses/${id}`), {
      ...without(planned('member'), 'createdAt'),
      createdAt: Timestamp.now(),
      ...o,
    });
  });
}

/** Geplante Ausgabe, die bereits gekauft wurde, samt der zugehörigen Buchung (472,00 statt 500,00 EUR). */
async function seedPurchased(plannedId = 'p1', bookingId = 'b-p1') {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const now = Timestamp.now();
    await setDoc(doc(db, `plannedExpenses/${plannedId}`), {
      ...without(planned('member'), 'createdAt'),
      status: 'PURCHASED',
      purchasedTransactionId: bookingId,
      createdAt: now,
      updatedAt: now,
      updatedBy: 'member',
    });
    await setDoc(doc(db, `transactions/${bookingId}`), {
      ...without(booking('member'), 'createdAt'),
      createdAt: now,
      plannedExpenseId: plannedId,
      amountCents: 47200,
      description: 'Neue Batterie',
    });
  });
}

const planEdit = (uid, o = {}) => ({ title: 'Geändert', updatedAt: serverTimestamp(), updatedBy: uid, ...o });
const planUpdate = (uid, bookingId, o = {}) => ({
  status: 'PURCHASED', purchasedTransactionId: bookingId, updatedAt: serverTimestamp(), updatedBy: uid, ...o,
});
const purchaseBooking = (uid, plannedId, o = {}) =>
  booking(uid, { plannedExpenseId: plannedId, amountCents: 47200, description: 'Neue Batterie', ...o });

/** Der Kauf wie in der App: Buchung anlegen und Planung auf PURCHASED setzen, in einer Transaktion. */
function purchase(db, uid, plannedId, bookingId, bookingOverrides = {}, planOverrides = {}) {
  return runTransaction(db, async (tx) => {
    await tx.get(doc(db, `plannedExpenses/${plannedId}`));
    tx.set(doc(db, `transactions/${bookingId}`), purchaseBooking(uid, plannedId, bookingOverrides));
    tx.update(doc(db, `plannedExpenses/${plannedId}`), planUpdate(uid, bookingId, planOverrides));
  });
}

/** Buchung löschen und Planung wieder öffnen, in einer Transaktion (FinanceRepository.delete). */
function reopen(db, uid, plannedId, bookingId, planOverrides = {}) {
  return runTransaction(db, async (tx) => {
    await tx.get(doc(db, `plannedExpenses/${plannedId}`));
    tx.delete(doc(db, `transactions/${bookingId}`));
    tx.update(doc(db, `plannedExpenses/${plannedId}`), {
      status: 'PLANNED', purchasedTransactionId: deleteField(), updatedAt: serverTimestamp(), updatedBy: uid, ...planOverrides,
    });
  });
}

// =====================================================================
describe('R-08 Geplante Ausgaben (plannedExpenses)', () => {
  it('R-08a MEMBER und ADMIN dürfen Planungen anlegen und lesen; optionale Felder gültig', async () => {
    for (const uid of ['member', 'admin']) {
      await assertSucceeds(setDoc(doc(as(uid), `plannedExpenses/p-${uid}`), planned(uid)));
    }
    await assertSucceeds(setDoc(doc(as('member'), 'plannedExpenses/p3'),
      planned('member', { priority: 'HIGH', link: 'https://example.org/batterie?x=1', comment: 'Kommentar' })));
    const list = await assertSucceeds(getDocs(collection(as('member'), 'plannedExpenses')));
    if (list.size !== 3) throw new Error(`erwartet 3 Planungen, gefunden ${list.size}`);
  });

  it('R-08b ohne Anmeldung, ohne Freischaltung und als entfernter Benutzer kein Zugriff', async () => {
    await seedPlanned();
    for (const db of [anon(), as('nobody')]) {
      await assertFails(getDoc(doc(db, 'plannedExpenses/p1')));
      await assertFails(getDocs(collection(db, 'plannedExpenses')));
      await assertFails(setDoc(doc(db, 'plannedExpenses/neu'), planned('nobody')));
      await assertFails(updateDoc(doc(db, 'plannedExpenses/p1'), planEdit('nobody')));
      await assertFails(deleteDoc(doc(db, 'plannedExpenses/p1')));
    }
  });

  it('R-08c Validierung beim Anlegen: Pflichtfelder, Betrag, Datum, Status, Link, Audit', async () => {
    const db = as('member');
    const bad = (o) => assertFails(setDoc(doc(db, 'plannedExpenses/x'), planned('member', o)));
    await bad({ title: '' });
    await bad({ title: 'x'.repeat(201) });
    await bad({ estimatedAmountCents: 0 });
    await bad({ estimatedAmountCents: -5 });
    await bad({ estimatedAmountCents: 12.5 });
    await bad({ estimatedAmountCents: 100000001 });
    await bad({ plannedDate: '30.09.2026' });
    await bad({ status: 'DONE' });
    await bad({ status: 'PURCHASED', purchasedTransactionId: 'b1' });
    await bad({ purchasedTransactionId: 'b1' });
    await bad({ priority: 'URGENT' });
    await bad({ link: 'ftp://example.org/datei' });
    await bad({ link: 'javascript:alert(1)' });
    await bad({ link: 'https://exa mple.org' });
    await bad({ link: 'https://example.org/' + 'a'.repeat(500) });
    await bad({ comment: 'x'.repeat(501) });
    await bad({ comment: 5 });
    await bad({ createdBy: 'admin' });
    await bad({ createdAt: Timestamp.now() });
    await bad({ updatedAt: serverTimestamp(), updatedBy: 'member' });
    await bad({ extra: 1 });
    await assertFails(setDoc(doc(db, 'plannedExpenses/x'), without(planned('member'), 'comment')));
    await assertFails(setDoc(doc(db, 'plannedExpenses/x'), without(planned('member'), 'title')));
    // Grenzwerte sind gültig
    await assertSucceeds(setDoc(doc(db, 'plannedExpenses/g1'), planned('member', { estimatedAmountCents: 1 })));
    await assertSucceeds(setDoc(doc(db, 'plannedExpenses/g2'), planned('member', { estimatedAmountCents: 100000000, title: 'x'.repeat(200) })));
  });

  it('R-08d Bearbeiten, solange geplant: jeder Benutzer, Audit Pflicht, Herkunft unveränderlich', async () => {
    await seedPlanned();
    await assertSucceeds(updateDoc(doc(as('admin'), 'plannedExpenses/p1'), planEdit('admin', { estimatedAmountCents: 45000, priority: 'LOW' })));
    await assertSucceeds(updateDoc(doc(as('member'), 'plannedExpenses/p1'), planEdit('member', { link: 'http://example.org' })));
    // Optionale Felder wieder entfernen (so schreibt die App, wenn Priorität und Link geleert werden)
    await assertSucceeds(updateDoc(doc(as('member'), 'plannedExpenses/p1'), planEdit('member', { priority: deleteField(), link: deleteField() })));
    await assertFails(updateDoc(doc(as('member'), 'plannedExpenses/p1'), { title: 'ohne Audit' }));
    await assertFails(updateDoc(doc(as('member'), 'plannedExpenses/p1'), planEdit('admin')));
    await assertFails(updateDoc(doc(as('member'), 'plannedExpenses/p1'), planEdit('member', { updatedAt: Timestamp.now() })));
    await assertFails(updateDoc(doc(as('member'), 'plannedExpenses/p1'), planEdit('member', { createdBy: 'admin' })));
    await assertFails(updateDoc(doc(as('member'), 'plannedExpenses/p1'), planEdit('member', { createdAt: Timestamp.now() })));
    await assertFails(updateDoc(doc(as('member'), 'plannedExpenses/p1'), planEdit('member', { estimatedAmountCents: 0 })));
    await assertFails(updateDoc(doc(as('member'), 'plannedExpenses/p1'), planEdit('member', { title: '' })));
  });

  it('R-08e Kauf in einer Transaktion: Buchung mit tatsächlichem Betrag, Planung wird PURCHASED', async () => {
    await seedPlanned();
    const db = as('member');
    await assertSucceeds(purchase(db, 'member', 'p1', 'b-p1'));
    const b = (await assertSucceeds(getDoc(doc(db, 'transactions/b-p1')))).data();
    const p = (await assertSucceeds(getDoc(doc(db, 'plannedExpenses/p1')))).data();
    if (b.amountCents !== 47200) throw new Error(`Buchung: erwartet 47200 Cent, gefunden ${b.amountCents}`);
    if (p.estimatedAmountCents !== 50000) throw new Error('Schätzung darf sich nicht ändern');
    if (p.status !== 'PURCHASED' || p.purchasedTransactionId !== 'b-p1') throw new Error('Planung nicht als gekauft markiert');
    if (b.plannedExpenseId !== 'p1') throw new Error('Buchung verweist nicht auf die Planung');
  });

  it('R-08f PURCHASED ohne passende Buchung ist verboten', async () => {
    await seedPlanned();
    await seedPlanned('p2');
    const db = as('member');
    // nur die Planung, keine Buchung
    await assertFails(updateDoc(doc(db, 'plannedExpenses/p1'), planUpdate('member', 'gibt-es-nicht')));
    // Buchung existiert schon, verweist aber nicht auf diese Planung
    await seedBooking('b-alt');
    await assertFails(updateDoc(doc(db, 'plannedExpenses/p1'), planUpdate('member', 'b-alt')));
    // Buchung verweist auf eine andere Planung
    await assertFails(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'transactions/b-x'), purchaseBooking('member', 'p2'));
      tx.update(doc(db, 'plannedExpenses/p1'), planUpdate('member', 'b-x'));
    }));
    // Einnahme statt Ausgabe
    await assertFails(purchase(db, 'member', 'p1', 'b-i', { type: 'INCOME', settlement: 'SETTLED' }));
    // Kauf und gleichzeitig andere Felder ändern
    await assertFails(purchase(db, 'member', 'p1', 'b-t', {}, { title: 'Anderer Titel' }));
    await assertFails(purchase(db, 'member', 'p1', 'b-e', {}, { estimatedAmountCents: 1 }));
    // Kauf ohne Audit
    await assertFails(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'transactions/b-a'), purchaseBooking('member', 'p1'));
      tx.update(doc(db, 'plannedExpenses/p1'), { status: 'PURCHASED', purchasedTransactionId: 'b-a' });
    }));
    const p = (await assertSucceeds(getDoc(doc(db, 'plannedExpenses/p1')))).data();
    if (p.status !== 'PLANNED') throw new Error('Planung darf nicht gekauft sein');
  });

  it('R-08g Buchung mit plannedExpenseId gibt es nur zusammen mit dem Kauf', async () => {
    await seedPlanned();
    const db = as('member');
    await assertFails(setDoc(doc(db, 'transactions/b1'), purchaseBooking('member', 'p1')));
    await assertFails(setDoc(doc(db, 'transactions/b2'), purchaseBooking('member', 'gibt-es-nicht')));
    await assertFails(setDoc(doc(db, 'transactions/b3'), purchaseBooking('member', 'p1', { plannedExpenseId: '' })));
    // Planung darf sich nicht auf eine andere Buchungs-ID beziehen als die angelegte
    await assertFails(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'transactions/b4'), purchaseBooking('member', 'p1'));
      tx.update(doc(db, 'plannedExpenses/p1'), planUpdate('member', 'andere-id'));
    }));
    // Normale Buchungen bleiben unberührt
    await assertSucceeds(setDoc(doc(db, 'transactions/b5'), booking('member')));
  });

  it('R-08h Doppelter Kauf und Änderung einer gekauften Planung sind verboten', async () => {
    await seedPurchased();
    await seedBooking('b-neu');
    const db = as('admin');
    await assertFails(purchase(db, 'admin', 'p1', 'b-zweit'));
    await assertFails(updateDoc(doc(db, 'plannedExpenses/p1'), planEdit('admin')));
    await assertFails(updateDoc(doc(db, 'plannedExpenses/p1'), planEdit('admin', { estimatedAmountCents: 1 })));
    await assertFails(updateDoc(doc(db, 'plannedExpenses/p1'), planUpdate('admin', 'b-neu')));
  });

  it('R-08i Wieder öffnen nur, wenn die Buchung im selben Schritt gelöscht wird', async () => {
    await seedPurchased();
    const db = as('member');
    // Buchung bleibt bestehen: verboten
    await assertFails(updateDoc(doc(db, 'plannedExpenses/p1'), {
      status: 'PLANNED', purchasedTransactionId: deleteField(), updatedAt: serverTimestamp(), updatedBy: 'member',
    }));
    // Buchung löschen und Planung öffnen in einer Transaktion: erlaubt
    await assertSucceeds(reopen(db, 'member', 'p1', 'b-p1'));
    const p = (await assertSucceeds(getDoc(doc(db, 'plannedExpenses/p1')))).data();
    if (p.status !== 'PLANNED' || 'purchasedTransactionId' in p) throw new Error('Planung nicht wieder geöffnet');
    const b = await assertSucceeds(getDoc(doc(db, 'transactions/b-p1')));
    if (b.exists()) throw new Error('Buchung müsste gelöscht sein');
    // danach kann erneut gekauft werden
    await assertSucceeds(purchase(db, 'member', 'p1', 'b-zweit'));
  });

  it('R-08j Wieder öffnen ändert nichts anderes; Buchung ohne Planung wird trotzdem gelöscht', async () => {
    await seedPurchased();
    const db = as('member');
    await assertFails(reopen(db, 'member', 'p1', 'b-p1', { title: 'Anderer Titel' }));
    await assertFails(reopen(db, 'member', 'p1', 'b-p1', { updatedBy: 'admin' }));
    // Buchung ohne Öffnen der Planung löschen (z. B. ältere App): erlaubt; Planung lässt sich danach öffnen
    await assertSucceeds(deleteDoc(doc(db, 'transactions/b-p1')));
    await assertSucceeds(updateDoc(doc(db, 'plannedExpenses/p1'), {
      status: 'PLANNED', purchasedTransactionId: deleteField(), updatedAt: serverTimestamp(), updatedBy: 'member',
    }));
  });

  it('R-08k plannedExpenseId einer Buchung ist unveränderlich; sonst normal bearbeitbar', async () => {
    await seedPurchased();
    await seedPlanned('p2');
    await seedBooking('b-normal');
    const db = as('member');
    await assertSucceeds(updateDoc(doc(db, 'transactions/b-p1'), edit('member', { amountCents: 47500, settlement: 'SPONSORED' })));
    await assertFails(updateDoc(doc(db, 'transactions/b-p1'), edit('member', { plannedExpenseId: 'p2' })));
    await assertFails(updateDoc(doc(db, 'transactions/b-p1'), edit('member', { plannedExpenseId: deleteField() })));
    await assertFails(updateDoc(doc(db, 'transactions/b-p1'), edit('member', { type: 'INCOME', settlement: 'SETTLED' })));
    await assertFails(updateDoc(doc(db, 'transactions/b-normal'), edit('member', { plannedExpenseId: 'p2' })));
    await assertSucceeds(updateDoc(doc(db, 'transactions/b-normal'), edit('member')));
  });

  it('R-08l Löschen einer Planung: jeder freigeschaltete Benutzer, gekaufte Buchung bleibt', async () => {
    await seedPlanned('p1');
    await seedPurchased('p2', 'b-p2');
    await assertSucceeds(deleteDoc(doc(as('member'), 'plannedExpenses/p1')));
    await assertSucceeds(deleteDoc(doc(as('admin'), 'plannedExpenses/p2')));
    const b = await assertSucceeds(getDoc(doc(as('member'), 'transactions/b-p2')));
    if (!b.exists()) throw new Error('Buchung muss bestehen bleiben');
  });

  it('R-08m Regression: 300 Importbuchungen in Blöcken à 10 funktionieren weiterhin', async () => {
    const db = as('admin');
    for (let start = 0; start < 300; start += 10) {
      await assertSucceeds(runTransaction(db, async (tx) => {
        for (let i = start; i < start + 10; i++) {
          tx.set(doc(db, `transactions/xl-${i}`), booking('admin', { paidByUid: 'member', importRef: `xl-${i}`, settlement: 'SETTLED' }));
        }
      }));
    }
  });
});

// =====================================================================
// Phase 6: Dateiablage und Belege. Testdaten nur für den Emulator (Nullbytes, keine echten Dateien).

const CHUNK = 921600; // 900 KiB je Stück (FileLimits.CHUNK_SIZE_BYTES)
const MAX_FILE = 8 * 1024 * 1024; // 8 MiB (FileLimits.MAX_FILE_BYTES)

const chunkCountFor = (size) => Math.ceil(size / CHUNK);
const chunkBytes = (n) => ({ data: Bytes.fromUint8Array(new Uint8Array(n)) });

/** Metadaten einer Datei, wie FirestoreFileStore sie schreibt. */
function fileMeta(uid, size = 1000, o = {}) {
  return {
    name: 'beleg.jpg', contentType: 'image/jpeg', sizeBytes: size, chunkCount: chunkCountFor(size),
    createdAt: serverTimestamp(), createdBy: uid, ...o,
  };
}

/** Verweis der Buchung auf die Datei (Feld receipt). */
const receiptRef = (fileId, size = 1000, o = {}) => ({ fileId, name: 'beleg.jpg', contentType: 'image/jpeg', sizeBytes: size, ...o });

/** Schreibt in die Transaktion: Datei mit allen Stücken (die Stücke zuerst, dann die Metadaten wie in der App). */
function stageFile(tx, db, uid, fileId, size = 1000, metaOverrides = {}) {
  const count = chunkCountFor(size);
  for (let i = 0; i < count; i++) {
    const n = i < count - 1 ? CHUNK : size - CHUNK * (count - 1);
    tx.set(doc(db, `files/${fileId}/chunks/${i}`), chunkBytes(n));
  }
  tx.set(doc(db, `files/${fileId}`), fileMeta(uid, size, metaOverrides));
}

/** Neue Ausgabe mit Beleg in einer Transaktion (FinanceRepository.create mit Beleg). */
function createWithReceipt(db, uid, bookingId, fileId, size = 1000, bookingOverrides = {}, metaOverrides = {}) {
  return runTransaction(db, async (tx) => {
    stageFile(tx, db, uid, fileId, size, metaOverrides);
    tx.set(doc(db, `transactions/${bookingId}`), booking(uid, { receipt: receiptRef(fileId, size), ...bookingOverrides }));
  });
}

/** Datei samt Stücken ohne Regelprüfung anlegen (für Tests, die eine bestehende Datei brauchen). */
async function seedFile(fileId = 'f1', size = 1000, uid = 'member') {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const count = chunkCountFor(size);
    for (let i = 0; i < count; i++) {
      await setDoc(doc(db, `files/${fileId}/chunks/${i}`), chunkBytes(i < count - 1 ? CHUNK : size - CHUNK * (count - 1)));
    }
    await setDoc(doc(db, `files/${fileId}`), { ...without(fileMeta(uid, size), 'createdAt'), createdAt: Timestamp.now() });
  });
}

/** Bestehende Ausgabe mit Beleg (Datei und Buchung, ohne Regelprüfung). */
async function seedReceiptBooking(bookingId = 'b1', fileId = 'f1', size = 1000) {
  await seedFile(fileId, size);
  await seedBooking(bookingId, { receipt: receiptRef(fileId, size) });
}

/** Datei samt Stücken löschen (FileStore.stageDelete). */
function stageDeleteFile(tx, db, fileId, size = 1000) {
  for (let i = 0; i < chunkCountFor(size); i++) tx.delete(doc(db, `files/${fileId}/chunks/${i}`));
  tx.delete(doc(db, `files/${fileId}`));
}

describe('R-09 Dateiablage und Belege (files, chunks, transactions.receipt)', () => {
  it('R-09a Ausgabe mit Beleg (1 Stück) in einer Transaktion; Datei und Stücke lesbar', async () => {
    await assertSucceeds(createWithReceipt(as('member'), 'member', 't1', 'f1'));
    const db = as('admin');
    const meta = await assertSucceeds(getDoc(doc(db, 'files/f1')));
    if (!meta.exists() || meta.data().chunkCount !== 1) throw new Error('Metadaten fehlen');
    const chunks = await assertSucceeds(getDocs(collection(db, 'files/f1/chunks')));
    if (chunks.size !== 1) throw new Error(`erwartet 1 Stück, gefunden ${chunks.size}`);
    const b = await assertSucceeds(getDoc(doc(db, 'transactions/t1')));
    if (b.data().receipt.fileId !== 'f1') throw new Error('Verweis fehlt');
  });

  it('R-09b größte Datei: 8 MiB = 10 Stücke samt Buchung in einer Transaktion (Grenze der Regelabfragen)', async () => {
    await assertSucceeds(createWithReceipt(as('member'), 'member', 't1', 'f-max', MAX_FILE));
    const chunks = await assertSucceeds(getDocs(collection(as('member'), 'files/f-max/chunks')));
    if (chunks.size !== 10) throw new Error(`erwartet 10 Stücke, gefunden ${chunks.size}`);
  });

  it('R-09c Datei mit genau einem vollen Stück und mit einem Byte mehr (2 Stücke)', async () => {
    await assertSucceeds(createWithReceipt(as('member'), 'member', 't1', 'f-full', CHUNK));
    await assertSucceeds(createWithReceipt(as('member'), 'member', 't2', 'f-plus', CHUNK + 1));
  });

  it('R-09d zu groß (mehr als 8 MiB), zu viele Stücke, Größe 0: verboten', async () => {
    const db = as('member');
    // Alle Stücke sind vorhanden (klein), nur die Metadaten sind ungültig: so schlägt genau die Größenprüfung an.
    const small = (tx, id, count) => { for (let i = 0; i < count; i++) tx.set(doc(db, `files/${id}/chunks/${i}`), chunkBytes(10)); };
    await assertFails(runTransaction(db, async (tx) => {
      small(tx, 'f1', 10);
      tx.set(doc(db, 'files/f1'), fileMeta('member', 1000, { sizeBytes: MAX_FILE + 1, chunkCount: 10 }));
    }));
    await assertSucceeds(runTransaction(db, async (tx) => { // Gegenprobe: genau 8 MiB mit 10 Stücken ist gültig
      small(tx, 'f1-ok', 10);
      tx.set(doc(db, 'files/f1-ok'), fileMeta('member', 1000, { sizeBytes: MAX_FILE, chunkCount: 10 }));
    }));
    await assertFails(runTransaction(db, async (tx) => {
      small(tx, 'f2', 10);
      tx.set(doc(db, 'files/f2'), fileMeta('member', 1000, { sizeBytes: MAX_FILE + 1, chunkCount: 11 }));
    }));
    await assertFails(runTransaction(db, async (tx) => {
      small(tx, 'f3', 1);
      tx.set(doc(db, 'files/f3'), fileMeta('member', 1000, { sizeBytes: 0, chunkCount: 1 }));
    }));
  });

  it('R-09e nur JPEG und PDF; Name Pflicht; keine Zusatzfelder; falscher Ersteller/Zeitstempel', async () => {
    const db = as('member');
    const tryMeta = (id, o) => runTransaction(db, async (tx) => {
      tx.set(doc(db, `files/${id}/chunks/0`), chunkBytes(10));
      tx.set(doc(db, `files/${id}`), fileMeta('member', 10, o));
    });
    await assertSucceeds(tryMeta('ok-pdf', { contentType: 'application/pdf', name: 'rechnung.pdf' }));
    await assertFails(tryMeta('x1', { contentType: 'image/png' }));
    await assertFails(tryMeta('x2', { contentType: 'text/html' }));
    await assertFails(tryMeta('x3', { name: '' }));
    await assertFails(tryMeta('x4', { name: 'x'.repeat(201) }));
    await assertFails(tryMeta('x5', { extra: 1 }));
    await assertFails(tryMeta('x6', { createdBy: 'admin' }));
    await assertFails(tryMeta('x7', { createdAt: Timestamp.fromMillis(0) }));
  });

  it('R-09f Größe passt nicht zur Stückzahl: verboten', async () => {
    const db = as('member');
    // 1000 Byte können nicht 2 Stücke sein; 2 Stücke (CHUNK+1 Byte) nicht als 1 Stück angegeben werden
    await assertFails(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'files/f1/chunks/0'), chunkBytes(500));
      tx.set(doc(db, 'files/f1/chunks/1'), chunkBytes(500));
      tx.set(doc(db, 'files/f1'), fileMeta('member', 1000, { chunkCount: 2 }));
    }));
    await assertFails(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'files/f2/chunks/0'), chunkBytes(CHUNK));
      tx.set(doc(db, 'files/f2'), fileMeta('member', CHUNK + 1, { chunkCount: 1 }));
    }));
  });

  it('R-09g fehlendes letztes Stück und zusätzliches Stück: verboten', async () => {
    const db = as('member');
    await assertFails(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'files/f1/chunks/0'), chunkBytes(CHUNK));
      tx.set(doc(db, 'files/f1'), fileMeta('member', CHUNK + 1)); // 2 Stücke angegeben, nur eines geschrieben
    }));
    await assertFails(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'files/f2/chunks/0'), chunkBytes(1000));
      tx.set(doc(db, 'files/f2/chunks/1'), chunkBytes(10)); // zusätzliches Stück
      tx.set(doc(db, 'files/f2'), fileMeta('member', 1000));
    }));
    await assertFails(setDoc(doc(db, 'files/f3'), fileMeta('member', 1000))); // Metadaten ohne Stück
  });

  it('R-09h Stücke: zu groß, leer, falsche Nummer, Zusatzfeld, falscher Typ; Ändern verboten', async () => {
    const db = as('member');
    await assertFails(setDoc(doc(db, 'files/f1/chunks/0'), chunkBytes(CHUNK + 1)));
    await assertFails(setDoc(doc(db, 'files/f1/chunks/0'), chunkBytes(0)));
    await assertFails(setDoc(doc(db, 'files/f1/chunks/10'), chunkBytes(10)));
    await assertFails(setDoc(doc(db, 'files/f1/chunks/abc'), chunkBytes(10)));
    await assertFails(setDoc(doc(db, 'files/f1/chunks/0'), { ...chunkBytes(10), extra: 1 }));
    await assertFails(setDoc(doc(db, 'files/f1/chunks/0'), { data: 'kein Bytes-Wert' }));
    await assertSucceeds(setDoc(doc(db, 'files/f1/chunks/0'), chunkBytes(CHUNK)));
    await assertFails(updateDoc(doc(db, 'files/f1/chunks/0'), chunkBytes(10)));
  });

  it('R-09i ohne Anmeldung, ohne Freischaltung und als entfernter Benutzer kein Zugriff', async () => {
    await seedFile('f1');
    for (const db of [anon(), as('nobody')]) {
      await assertFails(getDoc(doc(db, 'files/f1')));
      await assertFails(getDocs(collection(db, 'files/f1/chunks')));
      await assertFails(setDoc(doc(db, 'files/f9/chunks/0'), chunkBytes(10)));
      await assertFails(deleteDoc(doc(db, 'files/f1')));
      await assertFails(runTransaction(db, async (tx) => { stageFile(tx, db, 'nobody', 'f9'); }));
    }
  });

  it('R-09j Metadaten sind unveränderlich; Dateien lassen sich nicht auflisten', async () => {
    await seedFile('f1');
    const db = as('member');
    await assertFails(updateDoc(doc(db, 'files/f1'), { name: 'anders.jpg' }));
    await assertFails(setDoc(doc(db, 'files/f1'), fileMeta('member')));
    await assertFails(getDocs(collection(db, 'files')));
  });

  it('R-09k Beleg-Verweis auf nicht vorhandene Datei, mit falschen Angaben oder auf Einnahme: verboten', async () => {
    const db = as('member');
    await assertFails(setDoc(doc(db, 'transactions/t1'), booking('member', { receipt: receiptRef('gibt-es-nicht') })));
    await assertFails(createWithReceipt(db, 'member', 't2', 'f2', 1000, { receipt: receiptRef('f2', 1000, { name: 'andere.jpg' }) }));
    await assertFails(createWithReceipt(db, 'member', 't3', 'f3', 1000, { receipt: receiptRef('f3', 999) }));
    await assertFails(createWithReceipt(db, 'member', 't4', 'f4', 1000, { receipt: receiptRef('f4', 1000, { contentType: 'application/pdf' }) }));
    await assertFails(createWithReceipt(db, 'member', 't5', 'f5', 1000, { type: 'INCOME', settlement: 'SETTLED' }));
    await assertFails(createWithReceipt(db, 'member', 't6', 'f6', 1000, { receipt: { ...receiptRef('f6'), extra: 1 } }));
    await assertFails(createWithReceipt(db, 'member', 't7', 'f7', 1000, { receipt: without(receiptRef('f7'), 'name') }));
  });

  it('R-09l bereits vorhandene Datei kann nicht an eine zweite Buchung gehängt werden', async () => {
    await seedReceiptBooking('b1', 'f1');
    await seedBooking('b2');
    const db = as('member');
    await assertFails(setDoc(doc(db, 'transactions/t-new'), booking('member', { receipt: receiptRef('f1') })));
    await assertFails(updateDoc(doc(db, 'transactions/b2'), edit('member', { receipt: receiptRef('f1') })));
  });

  it('R-09n Buchung mit Beleg bearbeiten (Beleg unverändert): erlaubt, auch als Erstattet-Markierung', async () => {
    await seedReceiptBooking('b1', 'f1');
    const db = as('admin');
    await assertSucceeds(updateDoc(doc(db, 'transactions/b1'), edit('admin', { amountCents: 5100 })));
    await assertSucceeds(updateDoc(doc(db, 'transactions/b1'), edit('admin', { settlement: 'SETTLED', settledAt: serverTimestamp(), settledBy: 'admin' })));
    await assertFails(updateDoc(doc(db, 'transactions/b1'), edit('admin', { receipt: receiptRef('f1', 1000, { name: 'umbenannt.jpg' }) })));
  });

  it('R-09o Beleg nachträglich an bestehende Ausgabe hängen (Datei im selben Schritt)', async () => {
    await seedBooking('b1');
    const db = as('member');
    await assertSucceeds(runTransaction(db, async (tx) => {
      stageFile(tx, db, 'member', 'f1', 1000);
      tx.update(doc(db, 'transactions/b1'), edit('member', { receipt: receiptRef('f1') }));
    }));
    const b = await assertSucceeds(getDoc(doc(db, 'transactions/b1')));
    if (b.data().receipt.fileId !== 'f1') throw new Error('Verweis fehlt');
  });

  it('R-09p Beleg entfernen: Verweis löschen und Datei im selben Schritt löschen', async () => {
    await seedReceiptBooking('b1', 'f1', CHUNK + 1);
    const db = as('member');
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.update(doc(db, 'transactions/b1'), edit('member', { receipt: deleteField() }));
      stageDeleteFile(tx, db, 'f1', CHUNK + 1);
    }));
    const f = await assertSucceeds(getDoc(doc(db, 'files/f1')));
    if (f.exists()) throw new Error('Datei muss gelöscht sein');
    const chunks = await assertSucceeds(getDocs(collection(db, 'files/f1/chunks')));
    if (chunks.size !== 0) throw new Error('Stücke müssen gelöscht sein');
  });

  it('R-09q Beleg ersetzen in zwei Schritten: neue Datei mit neuem Verweis, danach alte Datei löschen', async () => {
    await seedReceiptBooking('b1', 'f-alt');
    const db = as('member');
    await assertSucceeds(runTransaction(db, async (tx) => {
      stageFile(tx, db, 'member', 'f-neu', 2000);
      tx.update(doc(db, 'transactions/b1'), edit('member', { receipt: receiptRef('f-neu', 2000) }));
    }));
    await assertSucceeds(runTransaction(db, async (tx) => { stageDeleteFile(tx, db, 'f-alt'); }));
    const b = await assertSucceeds(getDoc(doc(db, 'transactions/b1')));
    if (b.data().receipt.fileId !== 'f-neu') throw new Error('neuer Verweis fehlt');
  });

  it('R-09r Buchung mit Beleg löschen: Buchung und Datei in einer Transaktion; größte Datei (11 Schreibvorgänge)', async () => {
    await seedReceiptBooking('b1', 'f1', MAX_FILE);
    const db = as('admin');
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.delete(doc(db, 'transactions/b1'));
      stageDeleteFile(tx, db, 'f1', MAX_FILE);
    }));
    const f = await assertSucceeds(getDoc(doc(db, 'files/f1')));
    if (f.exists()) throw new Error('Datei muss gelöscht sein');
  });

  it('R-09s Regression: Ausgabe ohne Beleg, Kauf einer Planung und Import funktionieren unverändert', async () => {
    const db = as('member');
    await assertSucceeds(setDoc(doc(db, 'transactions/t1'), booking('member')));
    await seedPlanned('p1');
    await assertSucceeds(purchase(db, 'member', 'p1', 'b-p1'));
    const adminDb = as('admin');
    await assertSucceeds(runTransaction(adminDb, async (tx) => {
      for (let i = 0; i < 10; i++) {
        tx.set(doc(adminDb, `transactions/xl-${i}`), booking('admin', { paidByUid: 'member', importRef: `xl-${i}`, settlement: 'SETTLED' }));
      }
    }));
  });
});

// =====================================================================
// Phase 7: Kalender. Testdaten nur für den Emulator.

/** Was die App beim Anlegen eines Kalendereintrags schreibt (CalendarRepository.create). */
function entry(uid, o = {}) {
  return {
    startDate: '2026-10-10',
    endDate: '2026-10-18',
    personUid: uid,
    personName: 'Mitglied',
    comment: '',
    createdAt: serverTimestamp(),
    createdBy: uid,
    ...o,
  };
}

async function seedEntry(id = 'c1', o = {}) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), `calendarEntries/${id}`), {
      ...without(entry('member'), 'createdAt'),
      createdAt: Timestamp.now(),
      ...o,
    });
  });
}

/** Was die App beim Ändern schreibt (CalendarRepository.update): Audit gesetzt, Ziel optional wieder entfernt. */
const entryEdit = (uid, o = {}) => ({ endDate: '2026-10-20', updatedAt: serverTimestamp(), updatedBy: uid, ...o });

describe('R-10 Kalender (calendarEntries)', () => {
  it('R-10a MEMBER und ADMIN dürfen Einträge anlegen und lesen; Ziel und Kommentar optional gültig', async () => {
    for (const uid of ['member', 'admin']) {
      await assertSucceeds(setDoc(doc(as(uid), `calendarEntries/c-${uid}`), entry(uid)));
    }
    await assertSucceeds(setDoc(doc(as('member'), 'calendarEntries/c3'),
      entry('member', { destination: 'Italien', comment: 'Herbsturlaub' })));
    const list = await assertSucceeds(getDocs(collection(as('member'), 'calendarEntries')));
    if (list.size !== 3) throw new Error(`erwartet 3 Einträge, gefunden ${list.size}`);
    await assertSucceeds(getDoc(doc(as('admin'), 'calendarEntries/c3')));
  });

  it('R-10b ohne Anmeldung, ohne Freischaltung und als entfernter Benutzer kein Zugriff', async () => {
    await seedEntry();
    for (const db of [anon(), as('nobody')]) {
      await assertFails(getDoc(doc(db, 'calendarEntries/c1')));
      await assertFails(getDocs(collection(db, 'calendarEntries')));
      await assertFails(setDoc(doc(db, 'calendarEntries/neu'), entry('nobody')));
      await assertFails(updateDoc(doc(db, 'calendarEntries/c1'), entryEdit('nobody')));
      await assertFails(deleteDoc(doc(db, 'calendarEntries/c1')));
    }
  });

  it('R-10c Validierung beim Anlegen: Pflichtfelder, Datumsbereich, Längen, Audit', async () => {
    const db = as('member');
    const bad = (o) => assertFails(setDoc(doc(db, 'calendarEntries/x'), entry('member', o)));
    await bad({ startDate: '10.10.2026' });
    await bad({ endDate: '2026-10' });
    await bad({ startDate: '' });
    await bad({ startDate: 20261010 });
    await bad({ endDate: '2026-10-09' }); // Ende vor Start
    await bad({ personUid: '' });
    await bad({ personUid: 5 });
    await bad({ personName: '' });
    await bad({ personName: 'x'.repeat(51) });
    await bad({ destination: '' });
    await bad({ destination: 'x'.repeat(101) });
    await bad({ destination: 5 });
    await bad({ comment: 'x'.repeat(501) });
    await bad({ comment: 5 });
    await bad({ createdBy: 'admin' });
    await bad({ createdAt: Timestamp.now() });
    await bad({ updatedAt: serverTimestamp(), updatedBy: 'member' });
    await bad({ extra: 1 });
    for (const key of ['startDate', 'endDate', 'personUid', 'personName', 'comment']) {
      await assertFails(setDoc(doc(db, 'calendarEntries/x'), without(entry('member'), key)));
    }
    // Grenzwerte sind gültig: ein Tag (Start = Ende), längste Texte
    await assertSucceeds(setDoc(doc(db, 'calendarEntries/g1'), entry('member', { startDate: '2026-10-10', endDate: '2026-10-10' })));
    await assertSucceeds(setDoc(doc(db, 'calendarEntries/g2'), entry('member', {
      personName: 'x'.repeat(50), destination: 'x'.repeat(100), comment: 'x'.repeat(500),
    })));
    // Jahreswechsel: Textvergleich entspricht der Reihenfolge der Tage
    await assertSucceeds(setDoc(doc(db, 'calendarEntries/g3'), entry('member', { startDate: '2026-12-30', endDate: '2027-01-03' })));
    await assertFails(setDoc(doc(db, 'calendarEntries/g4'), entry('member', { startDate: '2027-01-03', endDate: '2026-12-30' })));
  });

  it('R-10d Überschneidungen sind erlaubt (Entscheidung 5): gleicher, angrenzender und umschließender Zeitraum', async () => {
    await seedEntry('c1');
    const db = as('admin');
    await assertSucceeds(setDoc(doc(db, 'calendarEntries/c2'), entry('admin', { personName: 'Admin' })));
    await assertSucceeds(setDoc(doc(db, 'calendarEntries/c3'), entry('admin', { startDate: '2026-10-18', endDate: '2026-10-20' })));
    await assertSucceeds(setDoc(doc(db, 'calendarEntries/c4'), entry('admin', { startDate: '2026-10-01', endDate: '2026-10-31' })));
  });

  it('R-10e Bearbeiten: jeder Benutzer, Audit Pflicht, Herkunft unveränderlich, Validierung gilt weiter', async () => {
    await seedEntry();
    await assertSucceeds(updateDoc(doc(as('admin'), 'calendarEntries/c1'), entryEdit('admin', { destination: 'Kroatien' })));
    await assertSucceeds(updateDoc(doc(as('member'), 'calendarEntries/c1'), entryEdit('member', { personUid: 'admin', personName: 'Admin' })));
    // Ziel wieder entfernen (so schreibt die App, wenn das Feld geleert wird)
    await assertSucceeds(updateDoc(doc(as('member'), 'calendarEntries/c1'), entryEdit('member', { destination: deleteField() })));
    await assertFails(updateDoc(doc(as('member'), 'calendarEntries/c1'), { endDate: '2026-10-21' }));
    await assertFails(updateDoc(doc(as('member'), 'calendarEntries/c1'), entryEdit('admin')));
    await assertFails(updateDoc(doc(as('member'), 'calendarEntries/c1'), entryEdit('member', { updatedAt: Timestamp.now() })));
    await assertFails(updateDoc(doc(as('member'), 'calendarEntries/c1'), entryEdit('member', { createdBy: 'admin' })));
    await assertFails(updateDoc(doc(as('member'), 'calendarEntries/c1'), entryEdit('member', { createdAt: Timestamp.now() })));
    await assertFails(updateDoc(doc(as('member'), 'calendarEntries/c1'), entryEdit('member', { endDate: '2026-10-09' })));
    await assertFails(updateDoc(doc(as('member'), 'calendarEntries/c1'), entryEdit('member', { personName: '' })));
  });

  it('R-10f Löschen: jeder freigeschaltete Benutzer, auch fremde Einträge', async () => {
    await seedEntry('c1');
    await seedEntry('c2');
    await assertSucceeds(deleteDoc(doc(as('member'), 'calendarEntries/c1')));
    await assertSucceeds(deleteDoc(doc(as('admin'), 'calendarEntries/c2')));
    const list = await assertSucceeds(getDocs(collection(as('admin'), 'calendarEntries')));
    if (list.size !== 0) throw new Error('Einträge müssen gelöscht sein');
  });

  it('R-10g Die Abfrage der Überschneidungsprüfung (startDate <= Ende, nach Start sortiert) ist erlaubt', async () => {
    await seedEntry('c1');
    await seedEntry('c2', { startDate: '2026-11-01', endDate: '2026-11-05' });
    const result = await assertSucceeds(getDocs(query(
      collection(as('member'), 'calendarEntries'), where('startDate', '<=', '2026-10-31'), orderBy('startDate'),
    )));
    if (result.size !== 1) throw new Error(`erwartet 1 Eintrag, gefunden ${result.size}`);
  });

  it('R-10h Regression: Finanzen, geplante Ausgaben und Dateien sind unverändert erreichbar', async () => {
    const db = as('member');
    await assertSucceeds(setDoc(doc(db, 'transactions/t1'), booking('member')));
    await seedPlanned('p1');
    await assertSucceeds(purchase(db, 'member', 'p1', 'b-p1'));
    await assertSucceeds(setDoc(doc(db, 'calendarEntries/c1'), entry('member')));
    await assertFails(setDoc(doc(db, 'nichtFreigegeben/x'), { a: 1 }));
  });
});

// =====================================================================
// Phase 8: Auffälligkeiten. Testdaten nur für den Emulator.

/** Was die App beim Anlegen einer Auffälligkeit schreibt (RepairRepository.create). */
function repair(uid, o = {}) {
  return {
    title: 'Wasserhahn tropft',
    description: 'Der Hahn in der Küche tropft dauerhaft.',
    date: '2026-10-10',
    status: 'OPEN',
    comment: '',
    createdAt: serverTimestamp(),
    createdBy: uid,
    ...o,
  };
}

async function seedRepair(id = 'r1', o = {}) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), `repairs/${id}`), {
      ...without(repair('member'), 'createdAt'),
      createdAt: Timestamp.now(),
      ...o,
    });
  });
}

/** Was die App beim Ändern schreibt (RepairRepository.update): Status und Audit gesetzt. */
const repairEdit = (uid, o = {}) => ({ title: 'Geändert', status: 'OPEN', updatedAt: serverTimestamp(), updatedBy: uid, ...o });

describe('R-11 Auffälligkeiten (repairs)', () => {
  it('R-11a MEMBER und ADMIN dürfen anlegen und lesen; Priorität und Kommentar optional gültig', async () => {
    for (const uid of ['member', 'admin']) {
      await assertSucceeds(setDoc(doc(as(uid), `repairs/r-${uid}`), repair(uid)));
    }
    for (const priority of ['LOW', 'MEDIUM', 'HIGH']) {
      await assertSucceeds(setDoc(doc(as('member'), `repairs/p-${priority}`), repair('member', { priority, comment: 'Bitte bald' })));
    }
    const list = await assertSucceeds(getDocs(collection(as('member'), 'repairs')));
    if (list.size !== 5) throw new Error(`erwartet 5 Einträge, gefunden ${list.size}`);
    await assertSucceeds(getDoc(doc(as('admin'), 'repairs/p-HIGH')));
  });

  it('R-11b ohne Anmeldung, ohne Freischaltung und als entfernter Benutzer kein Zugriff', async () => {
    await seedRepair();
    for (const db of [anon(), as('nobody')]) {
      await assertFails(getDoc(doc(db, 'repairs/r1')));
      await assertFails(getDocs(collection(db, 'repairs')));
      await assertFails(setDoc(doc(db, 'repairs/neu'), repair('nobody')));
      await assertFails(updateDoc(doc(db, 'repairs/r1'), repairEdit('nobody')));
      await assertFails(deleteDoc(doc(db, 'repairs/r1')));
    }
  });

  it('R-11c Validierung beim Anlegen: Pflichtfelder, Format, Längen, Status, Audit', async () => {
    const db = as('member');
    const bad = (o) => assertFails(setDoc(doc(db, 'repairs/x'), repair('member', o)));
    await bad({ title: '' });
    await bad({ title: 'x'.repeat(201) });
    await bad({ title: 5 });
    await bad({ description: '' });
    await bad({ description: 'x'.repeat(2001) });
    await bad({ date: '10.10.2026' });
    await bad({ date: 20261010 });
    await bad({ status: 'DONE' }); // neue Auffälligkeiten sind immer offen
    await bad({ status: 'CLOSED' });
    await bad({ status: 'open' });
    await bad({ priority: 'URGENT' });
    await bad({ priority: 3 });
    await bad({ comment: 'x'.repeat(501) });
    await bad({ comment: 5 });
    await bad({ createdBy: 'admin' });
    await bad({ createdAt: Timestamp.now() });
    await bad({ updatedAt: serverTimestamp(), updatedBy: 'member' });
    await bad({ extra: 1 });
    for (const key of ['title', 'description', 'date', 'status', 'comment']) {
      await assertFails(setDoc(doc(db, 'repairs/x'), without(repair('member'), key)));
    }
    // Grenzwerte sind gültig: längste Texte
    await assertSucceeds(setDoc(doc(db, 'repairs/g1'), repair('member', {
      title: 'x'.repeat(200), description: 'x'.repeat(2000), comment: 'x'.repeat(500),
    })));
  });

  it('R-11d Erledigen und Wiederöffnen sind Statuswechsel mit Audit, jeder Benutzer darf beides', async () => {
    await seedRepair();
    await assertSucceeds(updateDoc(doc(as('admin'), 'repairs/r1'), repairEdit('admin', { status: 'DONE' })));
    const done = await assertSucceeds(getDoc(doc(as('member'), 'repairs/r1')));
    if (done.data().status !== 'DONE') throw new Error('Status muss ERLEDIGT sein');
    await assertSucceeds(updateDoc(doc(as('member'), 'repairs/r1'), repairEdit('member', { status: 'OPEN' })));
    const open = await assertSucceeds(getDoc(doc(as('admin'), 'repairs/r1')));
    if (open.data().status !== 'OPEN') throw new Error('Status muss OFFEN sein');
    await assertFails(updateDoc(doc(as('member'), 'repairs/r1'), repairEdit('member', { status: 'CLOSED' })));
    await assertFails(updateDoc(doc(as('member'), 'repairs/r1'), { status: 'DONE' })); // ohne Audit
  });

  it('R-11e Bearbeiten: Audit Pflicht, Herkunft unveränderlich, Priorität entfernbar, Validierung gilt weiter', async () => {
    await seedRepair('r1', { priority: 'HIGH' });
    await assertSucceeds(updateDoc(doc(as('admin'), 'repairs/r1'), repairEdit('admin', { description: 'Neu beschrieben' })));
    await assertSucceeds(updateDoc(doc(as('member'), 'repairs/r1'), repairEdit('member', { priority: deleteField() })));
    await assertFails(updateDoc(doc(as('member'), 'repairs/r1'), { title: 'Ohne Audit' }));
    await assertFails(updateDoc(doc(as('member'), 'repairs/r1'), repairEdit('admin')));
    await assertFails(updateDoc(doc(as('member'), 'repairs/r1'), repairEdit('member', { updatedAt: Timestamp.now() })));
    await assertFails(updateDoc(doc(as('member'), 'repairs/r1'), repairEdit('member', { createdBy: 'admin' })));
    await assertFails(updateDoc(doc(as('member'), 'repairs/r1'), repairEdit('member', { createdAt: Timestamp.now() })));
    await assertFails(updateDoc(doc(as('member'), 'repairs/r1'), repairEdit('member', { title: '' })));
    await assertFails(updateDoc(doc(as('member'), 'repairs/r1'), repairEdit('member', { date: 'gestern' })));
    await assertFails(updateDoc(doc(as('member'), 'repairs/r1'), repairEdit('member', { extra: 1 })));
  });

  it('R-11f Löschen: jeder freigeschaltete Benutzer, auch fremde und erledigte Einträge', async () => {
    await seedRepair('r1');
    await seedRepair('r2', { status: 'DONE' });
    await assertSucceeds(deleteDoc(doc(as('member'), 'repairs/r1')));
    await assertSucceeds(deleteDoc(doc(as('admin'), 'repairs/r2')));
    const list = await assertSucceeds(getDocs(collection(as('admin'), 'repairs')));
    if (list.size !== 0) throw new Error('Einträge müssen gelöscht sein');
  });

  it('R-11g Liste nach Status abfragbar (Filter Offen/Erledigt)', async () => {
    await seedRepair('r1');
    await seedRepair('r2', { status: 'DONE' });
    const open = await assertSucceeds(getDocs(query(collection(as('member'), 'repairs'), where('status', '==', 'OPEN'))));
    if (open.size !== 1) throw new Error(`erwartet 1 offenen Eintrag, gefunden ${open.size}`);
  });

  it('R-11h Regression: Finanzen, Kalender und geplante Ausgaben unverändert; nicht freigegebene Sammlung weiter gesperrt', async () => {
    const db = as('member');
    await assertSucceeds(setDoc(doc(db, 'transactions/t1'), booking('member')));
    await assertSucceeds(setDoc(doc(db, 'calendarEntries/c1'), entry('member')));
    await seedPlanned('p1');
    await assertSucceeds(purchase(db, 'member', 'p1', 'b-p1'));
    await assertSucceeds(setDoc(doc(db, 'repairs/r1'), repair('member')));
    await assertFails(setDoc(doc(db, 'unbekannt/x'), { a: 1 }));
  });
});

// =====================================================================
// Phase 9: Stellplätze. Testdaten nur für den Emulator.

/** Verweis eines Stellplatzes auf ein Foto (Feld photos), wie CampsiteRepository ihn schreibt. */
const photoRef = (fileId, size = 1000, o = {}) => ({ fileId, name: 'beleg.jpg', contentType: 'image/jpeg', sizeBytes: size, ...o }); // Name wie fileMeta()

/** Was die App beim Anlegen eines Stellplatzes schreibt (CampsiteRepository.create). */
function campsite(uid, o = {}) {
  return {
    latitude: 48.137154,
    longitude: 11.576124,
    date: '2026-10-01',
    comment: 'Ruhig, am See',
    photos: [],
    createdAt: serverTimestamp(),
    createdBy: uid,
    ...o,
  };
}

/** Stellplatz anlegen, dabei neue Fotos (eine Datei mit einem Stück je Foto) in derselben Transaktion. */
function createCampsite(db, uid, id, photoIds = [], size = CHUNK, o = {}) {
  return runTransaction(db, async (tx) => {
    for (const fileId of photoIds) stageFile(tx, db, uid, fileId, size);
    tx.set(doc(db, `campsites/${id}`), campsite(uid, { photos: photoIds.map((f) => photoRef(f, size)), ...o }));
  });
}

/** Bestehender Stellplatz mit Fotos (Dateien und Stellplatz, ohne Regelprüfung). */
async function seedCampsite(id = 's1', photoIds = [], size = 1000, o = {}) {
  for (const fileId of photoIds) await seedFile(fileId, size);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), `campsites/${id}`), {
      ...without(campsite('member'), 'createdAt'),
      createdAt: Timestamp.now(),
      photos: photoIds.map((f) => photoRef(f, size)),
      ...o,
    });
  });
}

/** Was die App beim Ändern schreibt (CampsiteRepository.update): Audit gesetzt, Fotoliste wie gewünscht. */
const campsiteEdit = (uid, o = {}) => ({ comment: 'Geändert', updatedAt: serverTimestamp(), updatedBy: uid, ...o });

describe('R-12 Stellplätze (campsites)', () => {
  it('R-12a MEMBER und ADMIN dürfen anlegen und lesen; optionale Felder und Grenzwerte gültig', async () => {
    for (const uid of ['member', 'admin']) {
      await assertSucceeds(setDoc(doc(as(uid), `campsites/s-${uid}`), campsite(uid)));
    }
    await assertSucceeds(setDoc(doc(as('member'), 'campsites/opt'), campsite('member', {
      name: 'Seeblick', address: 'Seestraße 1, 12345 Ort', note: 'Zufahrt schmal', rating: 5,
    })));
    // Grenzwerte: Pole, Datumsgrenze, ganze Zahlen, längste Texte, Bewertung 1
    await assertSucceeds(setDoc(doc(as('member'), 'campsites/g1'), campsite('member', {
      latitude: 90, longitude: 180, rating: 1, name: 'x'.repeat(100), address: 'x'.repeat(200), note: 'x'.repeat(500), comment: 'x'.repeat(500),
    })));
    await assertSucceeds(setDoc(doc(as('member'), 'campsites/g2'), campsite('member', { latitude: -90, longitude: -180, comment: 'x' })));
    await assertSucceeds(setDoc(doc(as('member'), 'campsites/g3'), campsite('member', { latitude: 0, longitude: 0 })));
    const list = await assertSucceeds(getDocs(collection(as('member'), 'campsites')));
    if (list.size !== 6) throw new Error(`erwartet 6 Stellplätze, gefunden ${list.size}`);
    await assertSucceeds(getDoc(doc(as('admin'), 'campsites/opt')));
  });

  it('R-12b ohne Anmeldung, ohne Freischaltung und als entfernter Benutzer kein Zugriff', async () => {
    await seedCampsite();
    for (const db of [anon(), as('nobody')]) {
      await assertFails(getDoc(doc(db, 'campsites/s1')));
      await assertFails(getDocs(collection(db, 'campsites')));
      await assertFails(setDoc(doc(db, 'campsites/neu'), campsite('nobody')));
      await assertFails(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('nobody')));
      await assertFails(deleteDoc(doc(db, 'campsites/s1')));
    }
  });

  it('R-12c Validierung beim Anlegen: Position, Pflichtfelder, Formate, Längen, Bewertung, Audit', async () => {
    const db = as('member');
    const bad = (o) => assertFails(setDoc(doc(db, 'campsites/x'), campsite('member', o)));
    await bad({ latitude: 90.0001 });
    await bad({ latitude: -90.0001 });
    await bad({ longitude: 180.0001 });
    await bad({ longitude: -180.5 });
    await bad({ latitude: '48.1' });
    await bad({ longitude: null });
    await bad({ comment: '' });
    await bad({ comment: 'x'.repeat(501) });
    await bad({ comment: 5 });
    await bad({ date: '01.10.2026' });
    await bad({ date: 20261001 });
    await bad({ name: '' });
    await bad({ name: 'x'.repeat(101) });
    await bad({ address: 'x'.repeat(201) });
    await bad({ note: 'x'.repeat(501) });
    await bad({ note: '' });
    await bad({ rating: 0 });
    await bad({ rating: 6 });
    await bad({ rating: 2.5 });
    await bad({ rating: '3' });
    await bad({ photos: 'keine' });
    await bad({ photos: null });
    await bad({ createdBy: 'admin' });
    await bad({ createdAt: Timestamp.now() });
    await bad({ updatedAt: serverTimestamp(), updatedBy: 'member' });
    await bad({ extra: 1 });
    for (const key of ['latitude', 'longitude', 'date', 'comment', 'photos']) {
      await assertFails(setDoc(doc(db, 'campsites/x'), without(campsite('member'), key)));
    }
  });

  it('R-12d ein, zwei und drei Fotos mit voller Stückgröße (900 KiB) in einer Transaktion (Grenze der Regelabfragen)', async () => {
    await assertSucceeds(createCampsite(as('member'), 'member', 's1', ['f1']));
    await assertSucceeds(createCampsite(as('member'), 'member', 's2', ['f2', 'f3']));
    await assertSucceeds(createCampsite(as('member'), 'member', 's3', ['f4', 'f5', 'f6'])); // 19 Regelabfragen, Grenze 20
    const db = as('admin');
    const s3 = await assertSucceeds(getDoc(doc(db, 'campsites/s3')));
    if (s3.data().photos.length !== 3) throw new Error('drei Fotoverweise erwartet');
    for (const f of ['f4', 'f5', 'f6']) {
      const meta = await assertSucceeds(getDoc(doc(db, `files/${f}`)));
      if (!meta.exists() || meta.data().chunkCount !== 1) throw new Error(`Datei ${f} fehlt oder hat nicht genau ein Stück`);
      await assertSucceeds(getDoc(doc(db, `files/${f}/chunks/0`)));
    }
  });

  it('R-12e vier Fotos sind verboten (auch wenn das vierte im selben Schritt entsteht)', async () => {
    await seedCampsite('s1', ['a', 'b', 'c']);
    const db = as('member');
    await assertFails(runTransaction(db, async (tx) => {
      stageFile(tx, db, 'member', 'd');
      tx.update(doc(db, 'campsites/s1'), campsiteEdit('member', { photos: ['a', 'b', 'c', 'd'].map((f) => photoRef(f)) }));
    }));
    // Gegenprobe: drei Fotos bleiben gültig (Kommentar ändern, Fotoliste unverändert)
    await assertSucceeds(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('member', { photos: ['a', 'b', 'c'].map((f) => photoRef(f)) })));
  });

  it('R-12f Fotos: nur JPEG, höchstens ein Stück (900 KiB), gültiger Verweis, keine Dublette', async () => {
    const db = as('member');
    // ein Byte mehr als ein Stück: zwei Stücke, als Foto verboten
    await assertFails(runTransaction(db, async (tx) => {
      stageFile(tx, db, 'member', 'big', CHUNK + 1);
      tx.set(doc(db, 'campsites/x1'), campsite('member', { photos: [photoRef('big', CHUNK + 1)] }));
    }));
    // PDF als Foto
    await assertFails(runTransaction(db, async (tx) => {
      stageFile(tx, db, 'member', 'pdf', 1000, { contentType: 'application/pdf', name: 'a.pdf' });
      tx.set(doc(db, 'campsites/x2'), campsite('member', { photos: [photoRef('pdf', 1000, { contentType: 'application/pdf', name: 'a.pdf' })] }));
    }));
    // Verweis mit Zusatzfeld, ohne Namen, mit leerer Kennung
    for (const [i, o] of [{ extra: 1 }, { name: '' }, { fileId: '' }, { sizeBytes: 0 }, { sizeBytes: '1000' }].entries()) {
      await assertFails(runTransaction(db, async (tx) => {
        stageFile(tx, db, 'member', `r${i}`);
        tx.set(doc(db, `campsites/y${i}`), campsite('member', { photos: [photoRef(`r${i}`, 1000, o)] }));
      }));
    }
    // dieselbe Datei zweimal
    await assertFails(runTransaction(db, async (tx) => {
      stageFile(tx, db, 'member', 'dup');
      tx.set(doc(db, 'campsites/x3'), campsite('member', { photos: [photoRef('dup'), photoRef('dup')] }));
    }));
    // Größe im Verweis passt nicht zur Datei
    await assertFails(runTransaction(db, async (tx) => {
      stageFile(tx, db, 'member', 'sz', 1000);
      tx.set(doc(db, 'campsites/x4'), campsite('member', { photos: [photoRef('sz', 2000)] }));
    }));
    // Gegenprobe: genau ein volles Stück ist gültig
    await assertSucceeds(createCampsite(db, 'member', 'ok', ['fok'], CHUNK));
  });

  it('R-12g Fotos nur mit ihrer Datei im selben Schritt; bestehende Dateien lassen sich nicht einhängen', async () => {
    await seedCampsite('s1', ['f1']);
    const db = as('member');
    // Verweis ohne Datei
    await assertFails(setDoc(doc(db, 'campsites/x1'), campsite('member', { photos: [photoRef('fehlt')] })));
    // Verweis auf eine schon vorhandene Datei eines anderen Stellplatzes (Einmalverwendung)
    await assertFails(setDoc(doc(db, 'campsites/x2'), campsite('member', { photos: [photoRef('f1')] })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('member', { photos: [photoRef('f1'), photoRef('f1')] })));
    await seedCampsite('s2', []);
    await assertFails(updateDoc(doc(db, 'campsites/s2'), campsiteEdit('member', { photos: [photoRef('f1')] })));
    // Gegenprobe: ein Stellplatz ganz ohne Foto bleibt gültig
    await assertSucceeds(setDoc(doc(db, 'campsites/ohne'), campsite('member')));
  });

  it('R-12h Bearbeiten: Audit Pflicht, Datum/Herkunft unveränderlich, Validierung gilt weiter (Position: siehe R-12m)', async () => {
    await seedCampsite('s1', [], 1000, { name: 'Alt', rating: 3 });
    const db = as('member');
    await assertSucceeds(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('member', { name: 'Neu', rating: 4 })));
    await assertSucceeds(updateDoc(doc(as('admin'), 'campsites/s1'), campsiteEdit('admin', { rating: deleteField(), name: deleteField() })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), { comment: 'Ohne Audit' }));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('admin')));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('member', { updatedAt: Timestamp.now() })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('member', { date: '2026-11-01' })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('member', { createdBy: 'admin' })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('member', { createdAt: Timestamp.now() })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('member', { comment: '' })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('member', { rating: 9 })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('member', { extra: 1 })));
  });

  it('R-12m Position korrigieren (Marker verschieben): jeder Benutzer, nur mit Audit und gültigem Bereich; Datum und Herkunft bleiben', async () => {
    await seedCampsite('s1', ['a', 'b', 'c'], 1000, { name: 'Alt', rating: 3 });
    const edit = (uid, o = {}) => ({ updatedAt: serverTimestamp(), updatedBy: uid, ...o });
    // genau die Schreibform der App: nur Breite, Länge und Audit (übrige Felder, auch drei Fotos, bleiben unverändert)
    await assertSucceeds(updateDoc(doc(as('member'), 'campsites/s1'), edit('member', { latitude: 48.5, longitude: 11.25 })));
    await assertSucceeds(updateDoc(doc(as('admin'), 'campsites/s1'), edit('admin', { latitude: -33.9, longitude: -70.6 })));
    // Grenzwerte sind gültig
    await assertSucceeds(updateDoc(doc(as('member'), 'campsites/s1'), edit('member', { latitude: 90, longitude: 180 })));
    await assertSucceeds(updateDoc(doc(as('member'), 'campsites/s1'), edit('member', { latitude: -90, longitude: -180 })));
    // ungültig: außerhalb des Bereichs, keine Zahl, ohne Audit, falscher Bearbeiter, Datum/Herkunft ändern
    const db = as('member');
    await assertFails(updateDoc(doc(db, 'campsites/s1'), edit('member', { latitude: 90.0001 })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), edit('member', { longitude: 180.0001 })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), edit('member', { latitude: -91 })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), edit('member', { longitude: '11.5' })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), { latitude: 48.6, longitude: 11.3 }));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), edit('admin', { latitude: 48.6 })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), edit('member', { latitude: 48.6, date: '2026-11-01' })));
    await assertFails(updateDoc(doc(db, 'campsites/s1'), edit('member', { latitude: 48.6, createdBy: 'admin' })));
    // ohne Anmeldung/Freischaltung nicht
    await assertFails(updateDoc(doc(as('nobody'), 'campsites/s1'), edit('nobody', { latitude: 48.6 })));
    // Position als Transaktion (wie in der App, mit drei Fotos im Stellplatz): Regelbudget reicht
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.update(doc(db, 'campsites/s1'), edit('member', { latitude: 47.1, longitude: 8.2 }));
    }));
  });

  it('R-12i Fotos ändern: hinzufügen, entfernen (mit Datei), ersetzen; Verweise ohne Änderung kosten keine Prüfung', async () => {
    await seedCampsite('s1', ['a', 'b']);
    const db = as('member');
    // hinzufügen: drittes Foto
    await assertSucceeds(runTransaction(db, async (tx) => {
      stageFile(tx, db, 'member', 'c');
      tx.update(doc(db, 'campsites/s1'), campsiteEdit('member', { photos: ['a', 'b', 'c'].map((f) => photoRef(f)) }));
    }));
    // entfernen: nur Verweis (Datei bleibt zunächst bestehen, danach eigener Aufräumschritt)
    await assertSucceeds(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('member', { photos: [photoRef('a'), photoRef('c')] })));
    await assertSucceeds(runTransaction(db, async (tx) => { stageDeleteFile(tx, db, 'b'); }));
    // ersetzen in einem Schritt: a durch d, c bleibt
    await assertSucceeds(runTransaction(db, async (tx) => {
      stageFile(tx, db, 'member', 'd');
      tx.update(doc(db, 'campsites/s1'), campsiteEdit('member', { photos: [photoRef('d'), photoRef('c')] }));
    }));
    const s1 = await assertSucceeds(getDoc(doc(as('admin'), 'campsites/s1')));
    const ids = s1.data().photos.map((p) => p.fileId).join(',');
    if (ids !== 'd,c') throw new Error(`erwartet d,c, gefunden ${ids}`);
    // alle Fotos entfernen
    await assertSucceeds(updateDoc(doc(db, 'campsites/s1'), campsiteEdit('member', { photos: [] })));
  });

  it('R-12j Löschen: jeder Benutzer; Stellplatz mit drei Fotos samt Dateien in einer Transaktion', async () => {
    await seedCampsite('s1', ['a', 'b', 'c'], CHUNK);
    await seedCampsite('s2', []);
    const db = as('member');
    await assertSucceeds(runTransaction(db, async (tx) => {
      for (const f of ['a', 'b', 'c']) stageDeleteFile(tx, db, f, CHUNK);
      tx.delete(doc(db, 'campsites/s1'));
    }));
    await assertSucceeds(deleteDoc(doc(as('admin'), 'campsites/s2')));
    const list = await assertSucceeds(getDocs(collection(as('admin'), 'campsites')));
    if (list.size !== 0) throw new Error('Stellplätze müssen gelöscht sein');
    const files = await assertSucceeds(getDoc(doc(as('admin'), 'files/a')));
    if (files.exists()) throw new Error('Datei muss gelöscht sein');
  });

  it('R-12k Liste abfragbar und nach Datum sortierbar (App lädt alle Stellplätze einmal)', async () => {
    await seedCampsite('s1', [], 1000, { date: '2026-09-01' });
    await seedCampsite('s2', [], 1000, { date: '2026-10-01' });
    const list = await assertSucceeds(getDocs(query(collection(as('member'), 'campsites'), orderBy('date', 'desc'))));
    if (list.size !== 2 || list.docs[0].id !== 's2') throw new Error('Sortierung nach Datum erwartet');
  });

  it('R-12l Regression: Finanzen, Kalender, Auffälligkeiten, geplante Ausgaben und Belege unverändert; nicht freigegebene Sammlung gesperrt', async () => {
    const db = as('member');
    await assertSucceeds(createWithReceipt(db, 'member', 't1', 'fr'));
    await assertSucceeds(setDoc(doc(db, 'calendarEntries/c1'), entry('member')));
    await assertSucceeds(setDoc(doc(db, 'repairs/r1'), repair('member')));
    await seedPlanned('p1');
    await assertSucceeds(purchase(db, 'member', 'p1', 'b-p1'));
    await assertSucceeds(setDoc(doc(db, 'campsites/s1'), campsite('member')));
    await assertFails(setDoc(doc(db, 'unbekannt/x'), { a: 1 }));
    // Ein Beleg darf nicht auf ein Foto-Stück eines Stellplatzes zeigen (Einmalverwendung der Datei)
    await seedCampsite('s9', ['foto9']);
    await assertFails(setDoc(doc(db, 'transactions/t9'), booking('member', { receipt: receiptRef('foto9') })));
  });
});

// =====================================================================
// Phase 10: Dokumente. Testdaten nur für den Emulator (Nullbytes, keine echten Dokumente).

/** Angaben einer PDF-Datei (Metadaten der Datei und Verweis müssen übereinstimmen). */
const pdfMeta = { contentType: 'application/pdf', name: 'handbuch.pdf' };

/** Was die App beim Anlegen eines Dokuments schreibt (DocumentRepository.create). */
function docEntry(uid, fileId = 'f1', size = 1000, o = {}) {
  return {
    name: 'Fahrzeugschein',
    category: 'VEHICLE',
    file: receiptRef(fileId, size),
    date: '2026-10-01',
    createdAt: serverTimestamp(),
    createdBy: uid,
    ...o,
  };
}

/** Dokument anlegen, dabei die Datei (alle Stücke) in derselben Transaktion. */
function createDocument(db, uid, id, fileId = 'f1', size = 1000, o = {}, metaOverrides = {}) {
  return runTransaction(db, async (tx) => {
    stageFile(tx, db, uid, fileId, size, metaOverrides);
    tx.set(doc(db, `documents/${id}`), docEntry(uid, fileId, size, o));
  });
}

/** Wie createDocument, aber mit frei gewählten Dokumentdaten (Datei `f-<id>` entsteht im selben Schritt). */
function tryCreateDoc(db, id, data, size = 1000) {
  return runTransaction(db, async (tx) => {
    stageFile(tx, db, 'member', `f-${id}`, size);
    tx.set(doc(db, `documents/${id}`), data);
  });
}

/** Bestehendes Dokument mit Datei (Datei und Dokument, ohne Regelprüfung). */
async function seedDocument(id = 'd1', fileId = 'f1', size = 1000, o = {}) {
  await seedFile(fileId, size);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), `documents/${id}`), {
      ...without(docEntry('member', fileId, size), 'createdAt'),
      createdAt: Timestamp.now(),
      ...o,
    });
  });
}

/** Was die App beim Ändern schreibt (DocumentRepository.update): Name/Kategorie, Audit gesetzt. */
const docEdit = (uid, o = {}) => ({ name: 'Geändert', updatedAt: serverTimestamp(), updatedBy: uid, ...o });

describe('R-13 Dokumente (documents)', () => {
  it('R-13a MEMBER und ADMIN legen an und lesen; Bild und PDF, alle sechs Kategorien, kürzester und längster Name', async () => {
    for (const uid of ['member', 'admin']) {
      await assertSucceeds(createDocument(as(uid), uid, `d-${uid}`, `f-${uid}`));
    }
    const categories = ['VEHICLE', 'INSURANCE', 'INVOICE', 'WARRANTY', 'MANUAL', 'OTHER'];
    for (const [i, category] of categories.entries()) {
      await assertSucceeds(createDocument(as('member'), 'member', `c${i}`, `fc${i}`, 1000, { category }));
    }
    await assertSucceeds(createDocument(as('member'), 'member', 'pdf', 'fpdf', 1000, { file: receiptRef('fpdf', 1000, pdfMeta) }, pdfMeta));
    await assertSucceeds(createDocument(as('member'), 'member', 'lang', 'flang', 1000, { name: 'x'.repeat(100) }));
    await assertSucceeds(createDocument(as('member'), 'member', 'kurz', 'fkurz', 1000, { name: 'x' }));
    const list = await assertSucceeds(getDocs(collection(as('member'), 'documents')));
    if (list.size !== 11) throw new Error(`erwartet 11 Dokumente, gefunden ${list.size}`);
    const pdf = await assertSucceeds(getDoc(doc(as('admin'), 'documents/pdf')));
    if (pdf.data().file.contentType !== 'application/pdf') throw new Error('PDF-Verweis fehlt');
    await assertSucceeds(getDoc(doc(as('admin'), 'files/fpdf')));
    await assertSucceeds(getDoc(doc(as('admin'), 'files/fpdf/chunks/0')));
  });

  it('R-13b größtes Dokument: 8 MiB = 10 Stücke samt Dokument in einer Transaktion (Grenze der Regelabfragen)', async () => {
    await assertSucceeds(createDocument(as('member'), 'member', 'd1', 'f-max', MAX_FILE));
    const chunks = await assertSucceeds(getDocs(collection(as('member'), 'files/f-max/chunks')));
    if (chunks.size !== 10) throw new Error(`erwartet 10 Stücke, gefunden ${chunks.size}`);
    // dasselbe als PDF
    await assertSucceeds(createDocument(as('member'), 'member', 'd2', 'f-max-pdf', MAX_FILE, { file: receiptRef('f-max-pdf', MAX_FILE, pdfMeta) }, pdfMeta));
    // Gegenprobe: ein Byte mehr ist verboten (die Datei ist zu groß, nichts bleibt zurück)
    await assertFails(createDocument(as('member'), 'member', 'd3', 'f-zu-gross', MAX_FILE + 1));
    const d3 = await assertSucceeds(getDoc(doc(as('member'), 'documents/d3')));
    if (d3.exists()) throw new Error('das zu große Dokument darf nicht entstanden sein');
  });

  it('R-13c ohne Anmeldung, ohne Freischaltung und als entfernter Benutzer kein Zugriff (auch nicht auf die Dateien)', async () => {
    await seedDocument();
    for (const db of [anon(), as('nobody')]) {
      await assertFails(getDoc(doc(db, 'documents/d1')));
      await assertFails(getDocs(collection(db, 'documents')));
      await assertFails(createDocument(db, 'nobody', 'neu', 'fneu'));
      await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('nobody')));
      await assertFails(deleteDoc(doc(db, 'documents/d1')));
      await assertFails(getDoc(doc(db, 'files/f1')));
      await assertFails(getDoc(doc(db, 'files/f1/chunks/0')));
      await assertFails(getDocs(collection(db, 'files/f1/chunks')));
    }
    // Gegenprobe: ein registrierter Benutzer liest dieselben Daten
    await assertSucceeds(getDoc(doc(as('member'), 'documents/d1')));
    await assertSucceeds(getDoc(doc(as('member'), 'files/f1/chunks/0')));
    // Entfernter Benutzer: Sobald sein Benutzerdokument gelöscht ist, hat er keinen Zugriff mehr
    await assertSucceeds(deleteDoc(doc(as('admin'), 'users/member')));
    await assertFails(getDoc(doc(as('member'), 'documents/d1')));
    await assertFails(getDoc(doc(as('member'), 'files/f1/chunks/0')));
    await assertFails(deleteDoc(doc(as('member'), 'documents/d1')));
  });

  it('R-13d Validierung beim Anlegen: Name, Kategorie, Datum, Verweis, Pflichtfelder, Audit, Zusatzfelder', async () => {
    const db = as('member');
    let n = 0;
    // Jeder Fall legt eine gültige Datei im selben Schritt an und verletzt genau eine Regel des Dokuments.
    const bad = (o) => {
      const id = `v${n++}`;
      const fileId = `f-${id}`;
      return assertFails(tryCreateDoc(db, id, docEntry('member', fileId, 1000, typeof o === 'function' ? o(fileId) : o)));
    };
    await bad({ name: '' });
    await bad({ name: 'x'.repeat(101) });
    await bad({ name: 5 });
    await bad({ name: null });
    await bad({ category: 'Fahrzeug' });
    await bad({ category: 'vehicle' });
    await bad({ category: '' });
    await bad({ category: null });
    await bad({ category: 7 });
    await bad({ date: '01.10.2026' });
    await bad({ date: 20261001 });
    await bad({ date: '' });
    await bad({ file: 'f1' });
    await bad({ file: null });
    await bad({ file: {} });
    await bad((f) => ({ file: receiptRef(f, 1000, { extra: 1 }) }));
    await bad((f) => ({ file: receiptRef(f, 1000, { name: '' }) }));
    await bad((f) => ({ file: receiptRef(f, 1000, { fileId: '' }) }));
    await bad((f) => ({ file: receiptRef(f, 0) }));
    await bad((f) => ({ file: receiptRef(f, 1000, { sizeBytes: '1000' }) }));
    await bad((f) => ({ file: receiptRef(f, 1000, { contentType: 'image/png' }) }));
    await bad((f) => ({ file: receiptRef(f, 1000, { contentType: 'text/plain' }) }));
    await bad({ createdBy: 'admin' });
    await bad({ createdAt: Timestamp.now() });
    await bad({ updatedAt: serverTimestamp(), updatedBy: 'member' });
    await bad({ extra: 1 });
    for (const key of ['name', 'category', 'file', 'date', 'createdBy', 'createdAt']) {
      const id = `p-${key}`;
      await assertFails(tryCreateDoc(db, id, without(docEntry('member', `f-${id}`), key)));
    }
    // Gegenprobe: dieselbe Form ohne Fehler ist gültig
    await assertSucceeds(tryCreateDoc(db, 'ok', docEntry('member', 'f-ok')));
  });

  it('R-13e Datei nur im selben Schritt und nur einmal: keine fehlende, keine fremde, keine nicht passende Datei', async () => {
    await seedDocument('d1', 'f1');
    await seedReceiptBooking('b1', 'fb');
    await seedCampsite('s1', ['fs']);
    const db = as('member');
    // Verweis ohne Datei
    await assertFails(setDoc(doc(db, 'documents/x1'), docEntry('member', 'fehlt')));
    // Verweis auf die Datei eines anderen Dokuments, eines Belegs oder eines Stellplatzfotos (Einmalverwendung)
    for (const [i, fileId] of ['f1', 'fb', 'fs'].entries()) {
      await assertFails(setDoc(doc(db, `documents/y${i}`), docEntry('member', fileId)));
    }
    // Angaben im Verweis passen nicht zur Datei: Größe, Name, Typ
    for (const [i, o] of [{ sizeBytes: 2000 }, { name: 'anders.jpg' }, { contentType: 'application/pdf' }].entries()) {
      await assertFails(runTransaction(db, async (tx) => {
        stageFile(tx, db, 'member', `m${i}`, 1000);
        tx.set(doc(db, `documents/m${i}`), docEntry('member', `m${i}`, 1000, { file: receiptRef(`m${i}`, 1000, o) }));
      }));
    }
    // Datei im selben Schritt, aber von einem anderen Benutzer angelegt
    await assertFails(runTransaction(db, async (tx) => {
      stageFile(tx, db, 'admin', 'fremd', 1000);
      tx.set(doc(db, 'documents/z1'), docEntry('member', 'fremd'));
    }));
    // Datei mit nicht erlaubtem Typ (PNG): weder als Datei noch als Dokument zulässig
    await assertFails(runTransaction(db, async (tx) => {
      stageFile(tx, db, 'member', 'png', 1000, { contentType: 'image/png', name: 'a.png' });
      tx.set(doc(db, 'documents/z2'), docEntry('member', 'png', 1000, { file: receiptRef('png', 1000, { contentType: 'image/png', name: 'a.png' }) }));
    }));
    // Gegenprobe: ein Dokument mit neuer Datei im selben Schritt ist gültig
    await assertSucceeds(createDocument(db, 'member', 'ok', 'fok'));
  });

  it('R-13f Bearbeiten: nur Name und Kategorie; Audit Pflicht; Datei, Datum und Herkunft unveränderlich', async () => {
    await seedDocument('d1', 'f1');
    await seedFile('f2');
    const db = as('member');
    await assertSucceeds(updateDoc(doc(db, 'documents/d1'), docEdit('member', { name: 'Neu', category: 'INSURANCE' })));
    await assertSucceeds(updateDoc(doc(as('admin'), 'documents/d1'), docEdit('admin', { category: 'OTHER' })));
    // unveränderter Dateiverweis im Update ist erlaubt
    await assertSucceeds(updateDoc(doc(db, 'documents/d1'), docEdit('member', { file: receiptRef('f1') })));
    const d = await assertSucceeds(getDoc(doc(as('admin'), 'documents/d1')));
    if (d.data().category !== 'OTHER' || d.data().updatedBy !== 'member' || d.data().file.fileId !== 'f1') {
      throw new Error('Name, Kategorie, Audit oder Datei nicht wie erwartet');
    }
    await assertFails(updateDoc(doc(db, 'documents/d1'), { name: 'Ohne Audit' }));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('admin')));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { updatedAt: Timestamp.now() })));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { file: receiptRef('f1', 2000) })));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { file: receiptRef('f2') })));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { file: deleteField() })));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { date: '2026-11-01' })));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { createdBy: 'admin' })));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { createdAt: Timestamp.now() })));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { name: '' })));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { name: 'x'.repeat(101) })));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { name: deleteField() })));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { category: 'Fahrzeug' })));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { category: deleteField() })));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { extra: 1 })));
  });

  it('R-13g die Datei lässt sich nachträglich nicht austauschen (auch nicht mit neuer Datei im selben Schritt)', async () => {
    await seedDocument('d1', 'f1');
    await seedDocument('d2', 'f2');
    const db = as('member');
    await assertFails(runTransaction(db, async (tx) => {
      stageFile(tx, db, 'member', 'neu', 1000);
      tx.update(doc(db, 'documents/d1'), docEdit('member', { file: receiptRef('neu') }));
    }));
    await assertFails(updateDoc(doc(db, 'documents/d1'), docEdit('member', { file: receiptRef('f2') })));
    const d1 = await assertSucceeds(getDoc(doc(db, 'documents/d1')));
    if (d1.data().file.fileId !== 'f1') throw new Error('die Datei des Dokuments muss unverändert sein');
    const neu = await assertSucceeds(getDoc(doc(db, 'files/neu')));
    if (neu.exists()) throw new Error('die neue Datei darf nicht entstanden sein');
  });

  it('R-13h Löschen: jeder Benutzer; Dokument samt Datei (mehrere Stücke) in einer Transaktion', async () => {
    await seedDocument('d1', 'f1', CHUNK * 3);
    await seedDocument('d2', 'f2');
    const db = as('member');
    await assertSucceeds(runTransaction(db, async (tx) => {
      stageDeleteFile(tx, db, 'f1', CHUNK * 3);
      tx.delete(doc(db, 'documents/d1'));
    }));
    // ein fremdes Dokument darf auch der ADMIN löschen (Entscheidung 4: jeder darf löschen)
    await assertSucceeds(deleteDoc(doc(as('admin'), 'documents/d2')));
    const list = await assertSucceeds(getDocs(collection(as('admin'), 'documents')));
    if (list.size !== 0) throw new Error('Dokumente müssen gelöscht sein');
    const file = await assertSucceeds(getDoc(doc(as('admin'), 'files/f1')));
    if (file.exists()) throw new Error('Datei muss gelöscht sein');
    const chunk = await assertSucceeds(getDoc(doc(as('admin'), 'files/f1/chunks/2')));
    if (chunk.exists()) throw new Error('Stücke müssen gelöscht sein');
  });

  it('R-13i Liste abfragbar: nach Datum sortiert und nach Kategorie gefiltert (App lädt alle Dokumente einmal)', async () => {
    await seedDocument('d1', 'f1', 1000, { date: '2026-09-01', category: 'INVOICE' });
    await seedDocument('d2', 'f2', 1000, { date: '2026-10-01', category: 'VEHICLE' });
    const sorted = await assertSucceeds(getDocs(query(collection(as('member'), 'documents'), orderBy('date', 'desc'))));
    if (sorted.size !== 2 || sorted.docs[0].id !== 'd2') throw new Error('Sortierung nach Datum erwartet');
    const invoices = await assertSucceeds(getDocs(query(collection(as('member'), 'documents'), where('category', '==', 'INVOICE'))));
    if (invoices.size !== 1 || invoices.docs[0].id !== 'd1') throw new Error('Filter nach Kategorie erwartet');
  });

  it('R-13j Regression: Belege, Stellplatzfotos, Finanzen, Kalender, Auffälligkeiten unverändert; Dokumentdatei nirgends wiederverwendbar', async () => {
    const db = as('member');
    await assertSucceeds(createWithReceipt(db, 'member', 't1', 'fr'));
    await assertSucceeds(setDoc(doc(db, 'calendarEntries/c1'), entry('member')));
    await assertSucceeds(setDoc(doc(db, 'repairs/r1'), repair('member')));
    await seedPlanned('p1');
    await assertSucceeds(purchase(db, 'member', 'p1', 'b-p1'));
    await assertSucceeds(createCampsite(db, 'member', 's1', ['fp1']));
    await assertSucceeds(createDocument(db, 'member', 'd1', 'fd'));
    // Die Datei eines Dokuments lässt sich weder als Beleg noch als Stellplatzfoto verwenden (Einmalverwendung)
    await assertFails(setDoc(doc(db, 'transactions/t9'), booking('member', { receipt: receiptRef('fd') })));
    await seedCampsite('s2', []);
    await assertFails(updateDoc(doc(db, 'campsites/s2'), campsiteEdit('member', { photos: [photoRef('fd')] })));
    await assertFails(setDoc(doc(db, 'campsites/s3'), campsite('member', { photos: [photoRef('fd')] })));
    // Eine nicht freigegebene Sammlung bleibt gesperrt
    await assertFails(setDoc(doc(db, 'unbekannt/x'), { a: 1 }));
  });
});
