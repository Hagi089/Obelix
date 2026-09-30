// Automatische Tests der Firestore-Sicherheitsregeln (firebase/firestore.rules).
// Läuft im Firebase-Emulator:  cd firebase && firebase emulators:exec --only firestore --project demo-obelix "npm --prefix rules-tests test"
// Testfall-IDs (R-xx) siehe docs/TESTFAELLE.md.

import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  runTransaction,
  serverTimestamp,
  setDoc,
  updateDoc,
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
      await assertFails(setDoc(doc(db, 'calendarEntries/t1'), { amountCents: 100 }));
      await assertFails(getDoc(doc(db, 'calendarEntries/t1')));
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
          tx.set(doc(db, `transactions/xl-${i}`), booking('member', { importRef: `xl-${i}`, settlement: 'SETTLED' }));
        }
      }));
    }
    const list = await assertSucceeds(getDocs(collection(db, 'transactions')));
    if (list.size !== 300) throw new Error(`erwartet 300 Buchungen, gefunden ${list.size}`);
    // Wiederholter Import darf bestehende Buchungen nicht überschreiben
    await assertFails(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'transactions/xl-0'), booking('member', { importRef: 'xl-0', amountCents: 1 }));
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
