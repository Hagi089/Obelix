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
  query,
  runTransaction,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';

// ---------- Testdaten (nur für den Emulator, keine echten Daten) ----------
const PARTIES = [
  { id: 'A', name: 'Partei 1' },
  { id: 'B', name: 'Partei 2' },
];
const CODE_A = 'ABCDEFGH23456789'; // gemeinsamer Code Haushalt A
const CODE_B = 'JKMNPQRS23456789'; // gemeinsamer Code Haushalt B
const CODE_NEW = 'KMNPQRTV23456789'; // neuer Code nach Erneuerung
const START = 'TVWXYZAB23456789'; // gültiger Start-Code
const START_OLD = 'ZYXWVTSR23456789'; // abgelaufener Start-Code
const START_USED = 'ABABABAB22222222'; // bereits benutzter Start-Code

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
  await seed();
});

async function seed() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const now = Timestamp.now();
    for (const [hid, code, admin, member] of [
      ['hhA', CODE_A, 'adminA', 'memberA'],
      ['hhB', CODE_B, 'adminB', 'memberB'],
    ]) {
      await setDoc(doc(db, `households/${hid}`), {
        name: `Haushalt ${hid}`, parties: PARTIES, inviteCode: code, createdBy: admin, createdAt: now,
      });
      await setDoc(doc(db, `households/${hid}/members/${admin}`), {
        role: 'ADMIN', partyId: 'A', displayName: admin, inviteCode: START_USED, createdAt: now,
      });
      await setDoc(doc(db, `households/${hid}/members/${member}`), {
        role: 'MEMBER', partyId: 'B', displayName: member, inviteCode: code, createdAt: now,
      });
      await setDoc(doc(db, `users/${admin}`), { householdId: hid, displayName: admin, createdAt: now });
      await setDoc(doc(db, `users/${member}`), { householdId: hid, displayName: member, createdAt: now });
      await setDoc(doc(db, `invites/${code}`), {
        type: 'JOIN', householdId: hid, parties: PARTIES, createdBy: admin, createdAt: now,
      });
    }
    await setDoc(doc(db, `invites/${START}`), { type: 'CREATE_HOUSEHOLD', usedBy: null, createdAt: now });
    await setDoc(doc(db, `invites/${START_OLD}`), {
      type: 'CREATE_HOUSEHOLD', usedBy: null, createdAt: now,
      expiresAt: Timestamp.fromMillis(Date.now() - 24 * 3600 * 1000),
    });
    await setDoc(doc(db, `invites/${START_USED}`), {
      type: 'CREATE_HOUSEHOLD', usedBy: 'someone', usedAt: now, householdId: 'hhOld', createdAt: now,
    });
  });
}

const as = (uid) => testEnv.authenticatedContext(uid).firestore();
const anon = () => testEnv.unauthenticatedContext().firestore();

// ---------- Hilfen: die Batches, die die App später schreibt ----------
function joinBatch(db, uid, o = {}) {
  const { hid = 'hhA', code = CODE_A, role = 'MEMBER', partyId = 'A', usersHid = hid, member = {}, user = {} } = o;
  const b = writeBatch(db);
  b.set(doc(db, `households/${hid}/members/${uid}`), {
    role, partyId, displayName: 'Neu', inviteCode: code, createdAt: serverTimestamp(), ...member,
  });
  b.set(doc(db, `users/${uid}`), { householdId: usersHid, displayName: 'Neu', createdAt: serverTimestamp(), ...user });
  return b;
}

function createHouseholdBatch(db, uid, o = {}) {
  const { hid = 'hhNew', code = START, role = 'ADMIN', household = {}, redeem = true, redeemHid = hid } = o;
  const b = writeBatch(db);
  b.set(doc(db, `households/${hid}`), {
    name: 'Neuer Haushalt', parties: PARTIES, inviteCode: null, createdBy: uid, createdAt: serverTimestamp(), ...household,
  });
  b.set(doc(db, `households/${hid}/members/${uid}`), {
    role, partyId: 'A', displayName: 'Gründer', inviteCode: code, createdAt: serverTimestamp(),
  });
  b.set(doc(db, `users/${uid}`), { householdId: hid, displayName: 'Gründer', createdAt: serverTimestamp() });
  if (redeem) {
    b.update(doc(db, `invites/${code}`), { usedBy: uid, usedAt: serverTimestamp(), householdId: redeemHid });
  }
  return b;
}

function rotateCodeBatch(db, uid, o = {}) {
  const { hid = 'hhA', newCode = CODE_NEW, oldCode = CODE_A, parties = PARTIES } = o;
  const b = writeBatch(db);
  b.set(doc(db, `invites/${newCode}`), {
    type: 'JOIN', householdId: hid, parties, createdBy: uid, createdAt: serverTimestamp(),
  });
  b.update(doc(db, `households/${hid}`), { inviteCode: newCode });
  if (oldCode) b.delete(doc(db, `invites/${oldCode}`));
  return b;
}

// =====================================================================
describe('R-01 Ohne Anmeldung kein Zugriff', () => {
  it('R-01a nichts lesbar', async () => {
    const db = anon();
    await assertFails(getDoc(doc(db, 'households/hhA')));
    await assertFails(getDoc(doc(db, 'households/hhA/members/adminA')));
    await assertFails(getDocs(collection(db, 'households/hhA/members')));
    await assertFails(getDoc(doc(db, 'users/adminA')));
    await assertFails(getDoc(doc(db, `invites/${CODE_A}`)));
    await assertFails(getDoc(doc(db, `invites/${START}`)));
  });

  it('R-01b nichts schreibbar', async () => {
    const db = anon();
    await assertFails(setDoc(doc(db, 'households/hhX'), { name: 'X' }));
    await assertFails(setDoc(doc(db, 'users/x'), { householdId: 'hhA', displayName: 'x', createdAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'households/hhA'), { name: 'Geändert' }));
    await assertFails(deleteDoc(doc(db, 'households/hhA/members/memberA')));
    await assertFails(joinBatch(db, 'stranger').commit());
  });
});

// =====================================================================
describe('R-02 Beitritt mit dem gemeinsamen Code', () => {
  it('R-02a gültiger Code: Beitritt gelingt, danach Zugriff auf den eigenen Haushalt', async () => {
    const db = as('newbie');
    await assertFails(getDoc(doc(db, 'households/hhA'))); // vorher: kein Zugriff
    await assertSucceeds(joinBatch(db, 'newbie').commit());
    await assertSucceeds(getDoc(doc(db, 'households/hhA')));
    await assertSucceeds(getDocs(collection(db, 'households/hhA/members')));
    await assertSucceeds(getDoc(doc(db, 'users/newbie')));
  });

  it('R-02b der gemeinsame Code gilt für mehrere Personen', async () => {
    await assertSucceeds(joinBatch(as('p1'), 'p1').commit());
    await assertSucceeds(joinBatch(as('p2'), 'p2', { partyId: 'B' }).commit());
  });

  it('R-02c Rolle ADMIN per Beitrittscode verboten (keine Selbst-Beförderung)', async () => {
    await assertFails(joinBatch(as('newbie'), 'newbie', { role: 'ADMIN' }).commit());
  });

  it('R-02d Code existiert nicht', async () => {
    await assertFails(joinBatch(as('newbie'), 'newbie', { code: 'ZZZZZZZZ22222222' }).commit());
  });

  it('R-02e Code eines anderen Haushalts für diesen Haushalt verboten', async () => {
    await assertFails(joinBatch(as('newbie'), 'newbie', { hid: 'hhA', code: CODE_B }).commit());
  });

  it('R-02f erneuerter (widerrufener) Code ist ungültig', async () => {
    await assertSucceeds(rotateCodeBatch(as('adminA'), 'adminA', { oldCode: null }).commit());
    // alter Code, sein invites-Dokument existiert noch, gilt trotzdem nicht mehr
    await assertFails(joinBatch(as('newbie'), 'newbie', { code: CODE_A }).commit());
    await assertSucceeds(joinBatch(as('newbie'), 'newbie', { code: CODE_NEW }).commit());
  });

  it('R-02g ungültige Partei', async () => {
    await assertFails(joinBatch(as('newbie'), 'newbie', { partyId: 'C' }).commit());
  });

  it('R-02h ohne users-Dokument im selben Batch verboten', async () => {
    const db = as('newbie');
    await assertFails(setDoc(doc(db, 'households/hhA/members/newbie'), {
      role: 'MEMBER', partyId: 'A', displayName: 'Neu', inviteCode: CODE_A, createdAt: serverTimestamp(),
    }));
  });

  it('R-02i users-Dokument darf nicht auf einen anderen Haushalt zeigen', async () => {
    await assertFails(joinBatch(as('newbie'), 'newbie', { usersHid: 'hhB' }).commit());
  });

  it('R-02j fremde Mitglieds-ID verboten (nicht für andere beitreten)', async () => {
    const db = as('newbie');
    const b = writeBatch(db);
    b.set(doc(db, 'households/hhA/members/other'), {
      role: 'MEMBER', partyId: 'A', displayName: 'X', inviteCode: CODE_A, createdAt: serverTimestamp(),
    });
    b.set(doc(db, 'users/other'), { householdId: 'hhA', displayName: 'X', createdAt: serverTimestamp() });
    await assertFails(b.commit());
  });

  it('R-02k zusätzliche Felder und falsche Zeitangabe verboten', async () => {
    await assertFails(joinBatch(as('n1'), 'n1', { member: { extra: 'x' } }).commit());
    await assertFails(joinBatch(as('n2'), 'n2', { member: { createdAt: Timestamp.fromMillis(0) } }).commit());
    await assertFails(joinBatch(as('n3'), 'n3', { member: { displayName: '' } }).commit());
  });

  it('R-02l wer schon in einem Haushalt ist, kann nicht einem zweiten beitreten', async () => {
    await assertFails(joinBatch(as('memberB'), 'memberB', { hid: 'hhA', code: CODE_A }).commit());
  });

  it('R-02m ohne aktiven Code (inviteCode = null) ist kein Beitritt möglich', async () => {
    await testEnv.withSecurityRulesDisabled((ctx) => updateDoc(doc(ctx.firestore(), 'households/hhA'), { inviteCode: null }));
    await assertFails(joinBatch(as('newbie'), 'newbie', { code: CODE_A }).commit());
  });
});

// =====================================================================
describe('R-03 Haushalt anlegen mit Start-Code', () => {
  it('R-03a gültiger Start-Code: Haushalt entsteht, Gründer ist ADMIN', async () => {
    const db = as('founder');
    await assertSucceeds(createHouseholdBatch(db, 'founder').commit());
    const member = await assertSucceeds(getDoc(doc(db, 'households/hhNew/members/founder')));
    assert(member.data().role === 'ADMIN');
    await assertSucceeds(getDoc(doc(db, 'households/hhNew')));
  });

  it('R-03b Start-Code ist einmalig', async () => {
    await assertSucceeds(createHouseholdBatch(as('f1'), 'f1', { hid: 'h1' }).commit());
    await assertFails(createHouseholdBatch(as('f2'), 'f2', { hid: 'h2' }).commit());
  });

  it('R-03c bereits benutzter Start-Code verboten', async () => {
    await assertFails(createHouseholdBatch(as('founder'), 'founder', { code: START_USED }).commit());
  });

  it('R-03d abgelaufener Start-Code verboten', async () => {
    await assertFails(createHouseholdBatch(as('founder'), 'founder', { code: START_OLD }).commit());
  });

  it('R-03e ohne Einlösen des Start-Codes verboten', async () => {
    await assertFails(createHouseholdBatch(as('founder'), 'founder', { redeem: false }).commit());
  });

  it('R-03f gemeinsamer Haushalts-Code taugt nicht zum Gründen', async () => {
    await assertFails(createHouseholdBatch(as('founder'), 'founder', { code: CODE_A, redeem: false }).commit());
  });

  it('R-03g Haushalt ohne Code anlegen verboten', async () => {
    const db = as('founder');
    await assertFails(setDoc(doc(db, 'households/hhX'), {
      name: 'X', parties: PARTIES, inviteCode: null, createdBy: 'founder', createdAt: serverTimestamp(),
    }));
  });

  it('R-03h ADMIN-Mitglied in einem bestehenden Haushalt anlegen verboten', async () => {
    await assertFails(createHouseholdBatch(as('founder'), 'founder', { hid: 'hhA', redeemHid: 'hhA' }).commit());
    await assertFails(joinBatch(as('founder'), 'founder', { hid: 'hhA', code: START, role: 'ADMIN' }).commit());
  });

  it('R-03i falscher Gründer (createdBy) oder falsche Parteien verboten', async () => {
    await assertFails(createHouseholdBatch(as('founder'), 'founder', { household: { createdBy: 'adminA' } }).commit());
    await assertFails(createHouseholdBatch(as('founder'), 'founder', {
      household: { parties: [{ id: 'A', name: 'x' }] },
    }).commit());
  });

  it('R-03j Gründer mit Rolle MEMBER verboten', async () => {
    await assertFails(createHouseholdBatch(as('founder'), 'founder', { role: 'MEMBER' }).commit());
  });
});

// =====================================================================
describe('R-04 Haushalt A sieht nie Daten von Haushalt B', () => {
  it('R-04a Mitglieder sehen ihren eigenen Haushalt', async () => {
    for (const uid of ['adminA', 'memberA']) {
      const db = as(uid);
      await assertSucceeds(getDoc(doc(db, 'households/hhA')));
      await assertSucceeds(getDocs(collection(db, 'households/hhA/members')));
      await assertSucceeds(getDoc(doc(db, `users/${uid}`)));
    }
  });

  it('R-04b Lesen fremder Haushaltsdaten verboten (beide Richtungen)', async () => {
    for (const [uid, other, otherAdmin] of [['adminA', 'hhB', 'adminB'], ['memberA', 'hhB', 'adminB'], ['memberB', 'hhA', 'adminA']]) {
      const db = as(uid);
      await assertFails(getDoc(doc(db, `households/${other}`)));
      await assertFails(getDoc(doc(db, `households/${other}/members/${otherAdmin}`)));
      await assertFails(getDocs(collection(db, `households/${other}/members`)));
      await assertFails(getDoc(doc(db, `users/${otherAdmin}`)));
    }
  });

  it('R-04c Schreiben in fremde Haushalte verboten, auch für ADMINs', async () => {
    const db = as('adminA');
    await assertFails(updateDoc(doc(db, 'households/hhB'), { name: 'Übernommen' }));
    await assertFails(updateDoc(doc(db, 'households/hhB/members/memberB'), { role: 'ADMIN' }));
    await assertFails(deleteDoc(doc(db, 'households/hhB/members/memberB')));
    await assertFails(setDoc(doc(db, 'households/hhB/members/adminA'), {
      role: 'ADMIN', partyId: 'A', displayName: 'A', inviteCode: CODE_B, createdAt: serverTimestamp(),
    }));
    await assertFails(deleteDoc(doc(db, 'users/adminB')));
    await assertFails(rotateCodeBatch(db, 'adminA', { hid: 'hhB', oldCode: CODE_B }).commit());
  });

  it('R-04d fremde Einladungen sind nicht auflistbar', async () => {
    const own = query(collection(as('adminA'), 'invites'), where('householdId', '==', 'hhA'));
    await assertSucceeds(getDocs(own));
    const foreign = query(collection(as('adminA'), 'invites'), where('householdId', '==', 'hhB'));
    await assertFails(getDocs(foreign));
    await assertFails(getDocs(collection(as('adminA'), 'invites'))); // alles auflisten
    await assertFails(getDocs(query(collection(as('memberA'), 'invites'), where('householdId', '==', 'hhA')))); // MEMBER
  });

  it('R-04e Konto ohne Mitgliedschaft sieht und ändert nichts', async () => {
    const db = as('stranger');
    await assertFails(getDoc(doc(db, 'households/hhA')));
    await assertFails(getDocs(collection(db, 'households/hhA/members')));
    await assertFails(getDoc(doc(db, 'users/adminA')));
    await assertFails(getDocs(collection(db, 'users')));
    await assertFails(getDocs(collection(db, 'households')));
    await assertFails(getDocs(collection(db, 'invites')));
    await assertFails(updateDoc(doc(db, 'households/hhA'), { name: 'X' }));
    await assertFails(setDoc(doc(db, 'households/hhA/members/stranger'), {
      role: 'MEMBER', partyId: 'A', displayName: 'S', inviteCode: CODE_A, createdAt: serverTimestamp(),
    }));
  });

  it('R-04f Einladungs-Dokument ist nur einzeln abrufbar (bewusst, nicht erratbar)', async () => {
    await assertSucceeds(getDoc(doc(as('stranger'), `invites/${CODE_A}`)));
  });

  it('R-04g noch nicht freigegebene Sammlungen sind auch für Mitglieder und ADMINs gesperrt', async () => {
    for (const uid of ['adminA', 'memberA']) {
      const db = as(uid);
      await assertFails(setDoc(doc(db, 'households/hhA/transactions/t1'), { amountCents: 100 }));
      await assertFails(getDoc(doc(db, 'households/hhA/transactions/t1')));
      await assertFails(setDoc(doc(db, 'irgendwas/x'), { a: 1 }));
    }
  });

  it('R-04h Haushalt und Mitglieder lassen sich nicht löschen/umbenennen wie fremde', async () => {
    await assertFails(deleteDoc(doc(as('adminA'), 'households/hhA')));
    await assertFails(getDocs(collection(as('adminA'), 'households')));
  });
});

// =====================================================================
describe('R-05 Rollen ADMIN / MEMBER', () => {
  it('R-05a MEMBER kann sich nicht selbst befördern', async () => {
    const db = as('memberA');
    await assertFails(updateDoc(doc(db, 'households/hhA/members/memberA'), { role: 'ADMIN' }));
    await assertFails(setDoc(doc(db, 'households/hhA/members/memberA'), {
      role: 'ADMIN', partyId: 'B', displayName: 'memberA', inviteCode: CODE_A, createdAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(db, 'households/hhA/members/memberA'), { partyId: 'A' }));
  });

  it('R-05b MEMBER darf keine anderen ändern oder entfernen', async () => {
    const db = as('memberA');
    await assertFails(updateDoc(doc(db, 'households/hhA/members/adminA'), { role: 'MEMBER' }));
    await assertFails(deleteDoc(doc(db, 'households/hhA/members/adminA')));
  });

  it('R-05c MEMBER darf den eigenen Anzeigenamen ändern, sonst nichts', async () => {
    const db = as('memberA');
    await assertSucceeds(updateDoc(doc(db, 'households/hhA/members/memberA'), { displayName: 'Neuer Name' }));
    await assertFails(updateDoc(doc(db, 'households/hhA/members/memberA'), { displayName: '' }));
    await assertFails(updateDoc(doc(db, 'households/hhA/members/memberA'), { displayName: 'X', role: 'ADMIN' }));
  });

  it('R-05d MEMBER darf Haushalt und Codes nicht verwalten', async () => {
    const db = as('memberA');
    await assertFails(updateDoc(doc(db, 'households/hhA'), { name: 'Neu' }));
    await assertFails(rotateCodeBatch(db, 'memberA').commit());
    await assertFails(deleteDoc(doc(db, `invites/${CODE_A}`)));
  });

  it('R-05e ADMIN befördert und degradiert andere und ändert deren Partei', async () => {
    const db = as('adminA');
    await assertSucceeds(updateDoc(doc(db, 'households/hhA/members/memberA'), { role: 'ADMIN' }));
    await assertSucceeds(updateDoc(doc(db, 'households/hhA/members/memberA'), { role: 'MEMBER', partyId: 'A' }));
    await assertFails(updateDoc(doc(db, 'households/hhA/members/memberA'), { role: 'CHEF' }));
    await assertFails(updateDoc(doc(db, 'households/hhA/members/memberA'), { partyId: 'C' }));
    await assertFails(updateDoc(doc(db, 'households/hhA/members/memberA'), { displayName: 'Fremd' }));
  });

  it('R-05f ADMIN kann sich nicht selbst degradieren oder entfernen (immer mindestens ein ADMIN)', async () => {
    const db = as('adminA');
    await assertFails(updateDoc(doc(db, 'households/hhA/members/adminA'), { role: 'MEMBER' }));
    await assertFails(deleteDoc(doc(db, 'households/hhA/members/adminA')));
    await assertSucceeds(updateDoc(doc(db, 'households/hhA/members/adminA'), { partyId: 'B' })); // eigene Partei ja
  });

  it('R-05g zwei ADMINs: der degradierte kann den anderen nicht mehr degradieren', async () => {
    await assertSucceeds(updateDoc(doc(as('adminA'), 'households/hhA/members/memberA'), { role: 'ADMIN' }));
    await assertSucceeds(updateDoc(doc(as('memberA'), 'households/hhA/members/adminA'), { role: 'MEMBER' })); // memberA ist jetzt ADMIN
    await assertFails(updateDoc(doc(as('adminA'), 'households/hhA/members/memberA'), { role: 'MEMBER' })); // adminA ist jetzt MEMBER
    await assertFails(deleteDoc(doc(as('adminA'), 'households/hhA/members/memberA')));
  });

  it('R-05h ADMIN entfernt ein Mitglied samt Zeiger; dieses hat danach keinen Zugriff mehr', async () => {
    const db = as('adminA');
    const b = writeBatch(db);
    b.delete(doc(db, 'households/hhA/members/memberA'));
    b.delete(doc(db, 'users/memberA'));
    await assertSucceeds(b.commit());
    await assertFails(getDoc(doc(as('memberA'), 'households/hhA')));
    // mit dem noch gültigen Code kann die Person wieder beitreten
    await assertSucceeds(joinBatch(as('memberA'), 'memberA', { code: CODE_A }).commit());
  });

  it('R-05i ADMIN ändert den Haushaltsnamen, nicht die Parteien', async () => {
    const db = as('adminA');
    await assertSucceeds(updateDoc(doc(db, 'households/hhA'), { name: 'Neuer Name' }));
    await assertFails(updateDoc(doc(db, 'households/hhA'), { name: '' }));
    await assertFails(updateDoc(doc(db, 'households/hhA'), { parties: [{ id: 'A', name: 'x' }, { id: 'B', name: 'y' }] }));
    await assertFails(updateDoc(doc(db, 'households/hhA'), { createdBy: 'memberA' }));
  });
});

// =====================================================================
describe('R-06 Gemeinsamen Code erzeugen, erneuern, widerrufen', () => {
  it('R-06a ADMIN erneuert den Code (neu anlegen, aktiv setzen, alten löschen)', async () => {
    const db = as('adminA');
    await assertSucceeds(rotateCodeBatch(db, 'adminA').commit());
    assert((await getDoc(doc(db, 'households/hhA'))).data().inviteCode === CODE_NEW);
    await assertFails(joinBatch(as('n1'), 'n1', { code: CODE_A }).commit());
    await assertSucceeds(joinBatch(as('n2'), 'n2', { code: CODE_NEW }).commit());
  });

  it('R-06b Code-Dokument für fremden Haushalt oder mit fremden Parteien verboten', async () => {
    const db = as('adminA');
    await assertFails(rotateCodeBatch(db, 'adminA', { parties: [{ id: 'A', name: 'x' }, { id: 'B', name: 'y' }] }).commit());
  });

  it('R-06c Code ohne gleichzeitiges Aktivieren oder mit falschem Format verboten', async () => {
    const db = as('adminA');
    await assertFails(setDoc(doc(db, `invites/${CODE_NEW}`), {
      type: 'JOIN', householdId: 'hhA', parties: PARTIES, createdBy: 'adminA', createdAt: serverTimestamp(),
    })); // ohne households.inviteCode im selben Batch
    await assertFails(rotateCodeBatch(db, 'adminA', { newCode: 'abc' }).commit());
    await assertFails(rotateCodeBatch(db, 'adminA', { newCode: 'AAAA1111AAAA1111' }).commit()); // enthält 1 (verboten)
  });

  it('R-06d ADMIN kann den aktiven Code deaktivieren; danach kein Beitritt', async () => {
    await assertSucceeds(updateDoc(doc(as('adminA'), 'households/hhA'), { inviteCode: null }));
    await assertFails(joinBatch(as('newbie'), 'newbie', { code: CODE_A }).commit());
  });

  it('R-06e ADMIN kann den aktiven Code nicht auf einen fremden Code setzen', async () => {
    await assertFails(updateDoc(doc(as('adminA'), 'households/hhA'), { inviteCode: CODE_B }));
  });

  it('R-06f Einladungs-Dokumente sind nicht änderbar', async () => {
    await assertFails(updateDoc(doc(as('adminA'), `invites/${CODE_A}`), { householdId: 'hhB' }));
    await assertFails(updateDoc(doc(as('adminA'), `invites/${START}`), { usedBy: 'adminA', usedAt: serverTimestamp(), householdId: 'hhZ' }));
  });
});

// =====================================================================
describe('R-07 users-Zeiger', () => {
  it('R-07a nur der Benutzer selbst liest seinen Zeiger; Auflisten verboten', async () => {
    await assertSucceeds(getDoc(doc(as('memberA'), 'users/memberA')));
    await assertFails(getDoc(doc(as('memberA'), 'users/adminA')));
    await assertFails(getDocs(collection(as('memberA'), 'users')));
  });

  it('R-07b der Haushalt im Zeiger ist unveränderlich', async () => {
    await assertFails(updateDoc(doc(as('memberA'), 'users/memberA'), { householdId: 'hhB' }));
    await assertSucceeds(updateDoc(doc(as('memberA'), 'users/memberA'), { displayName: 'Neu' }));
  });

  it('R-07c den eigenen Zeiger kann man nicht löschen', async () => {
    await assertFails(deleteDoc(doc(as('memberA'), 'users/memberA')));
  });

  it('R-07d kein Zeiger ohne Mitglieds-Dokument', async () => {
    await assertFails(setDoc(doc(as('stranger'), 'users/stranger'), {
      householdId: 'hhA', displayName: 'S', createdAt: serverTimestamp(),
    }));
  });
});

// =====================================================================
// Die App schreibt mit Transaktionen (nicht mit Batches), damit offline nichts als gespeichert gilt.
// Diese Tests führen dieselben Schreibvorgänge wie HouseholdRepository.kt aus.
describe('R-08 Schreibvorgänge der App (Transaktionen)', () => {
  it('R-08a Beitritt per Transaktion', async () => {
    const db = as('newbie');
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'households/hhA/members/newbie'), {
        role: 'MEMBER', partyId: 'B', displayName: 'Neu', inviteCode: CODE_A, createdAt: serverTimestamp(),
      });
      tx.set(doc(db, 'users/newbie'), { householdId: 'hhA', displayName: 'Neu', createdAt: serverTimestamp() });
    }));
    await assertSucceeds(getDoc(doc(db, 'households/hhA')));
  });

  it('R-08b Haushalt anlegen per Transaktion (Start-Code, Parteien, ADMIN)', async () => {
    const db = as('founder');
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'households/hhNew'), {
        name: 'Neu', parties: PARTIES, inviteCode: null, createdBy: 'founder', createdAt: serverTimestamp(),
      });
      tx.set(doc(db, 'households/hhNew/members/founder'), {
        role: 'ADMIN', partyId: 'A', displayName: 'Gründer', inviteCode: START, createdAt: serverTimestamp(),
      });
      tx.set(doc(db, 'users/founder'), { householdId: 'hhNew', displayName: 'Gründer', createdAt: serverTimestamp() });
      tx.update(doc(db, `invites/${START}`), { usedBy: 'founder', usedAt: serverTimestamp(), householdId: 'hhNew' });
    }));
    // Der Gründer kann danach den ersten gemeinsamen Code erzeugen.
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.set(doc(db, `invites/${CODE_NEW}`), {
        type: 'JOIN', householdId: 'hhNew', parties: PARTIES, createdBy: 'founder', createdAt: serverTimestamp(),
      });
      tx.update(doc(db, 'households/hhNew'), 'inviteCode', CODE_NEW);
    }));
    await assertSucceeds(joinBatch(as('newbie'), 'newbie', { hid: 'hhNew', code: CODE_NEW }).commit());
  });

  it('R-08c Code erneuern und Mitglied entfernen per Transaktion', async () => {
    const db = as('adminA');
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.set(doc(db, `invites/${CODE_NEW}`), {
        type: 'JOIN', householdId: 'hhA', parties: PARTIES, createdBy: 'adminA', createdAt: serverTimestamp(),
      });
      tx.update(doc(db, 'households/hhA'), 'inviteCode', CODE_NEW);
      tx.delete(doc(db, `invites/${CODE_A}`));
    }));
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.delete(doc(db, 'households/hhA/members/memberA'));
      tx.delete(doc(db, 'users/memberA'));
    }));
    await assertSucceeds(runTransaction(db, async (tx) => {
      tx.update(doc(db, 'households/hhA/members/adminA'), { role: 'ADMIN', partyId: 'B' });
    }));
  });

  it('R-08d Beitritt mit widerrufenem Code scheitert auch als Transaktion', async () => {
    await assertSucceeds(rotateCodeBatch(as('adminA'), 'adminA').commit());
    const db = as('newbie');
    await assertFails(runTransaction(db, async (tx) => {
      tx.set(doc(db, 'households/hhA/members/newbie'), {
        role: 'MEMBER', partyId: 'A', displayName: 'Neu', inviteCode: CODE_A, createdAt: serverTimestamp(),
      });
      tx.set(doc(db, 'users/newbie'), { householdId: 'hhA', displayName: 'Neu', createdAt: serverTimestamp() });
    }));
  });
});

function assert(condition) {
  if (!condition) throw new Error('Erwartete Bedingung nicht erfüllt');
}
