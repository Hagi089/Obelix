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

  it('R-03b entfernter Benutzer hat keinen Zugriff mehr', async () => {
    await assertSucceeds(deleteDoc(doc(as('admin'), 'users/member')));
    await assertFails(getDocs(collection(as('member'), 'users')));
  });

  it('R-03c noch nicht freigegebene Sammlungen sind für alle gesperrt', async () => {
    for (const uid of ['admin', 'member']) {
      const db = as(uid);
      await assertFails(setDoc(doc(db, 'transactions/t1'), { amountCents: 100 }));
      await assertFails(getDoc(doc(db, 'transactions/t1')));
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
