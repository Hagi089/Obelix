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
  deleteField,
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
