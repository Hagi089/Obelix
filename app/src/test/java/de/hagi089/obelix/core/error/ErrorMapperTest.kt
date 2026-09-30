package de.hagi089.obelix.core.error

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ErrorMapperTest {

    @Test
    fun ioException_isNetworkError() {
        assertEquals(AppError.NETWORK, ErrorMapper.map(IOException("timeout")))
    }

    @Test
    fun unknownException_isUnknownError() {
        assertEquals(AppError.UNKNOWN, ErrorMapper.map(IllegalStateException("x")))
    }

    @Test
    fun firestoreCodes_areMappedToUserFacingErrors() {
        assertEquals(AppError.PERMISSION_DENIED, ErrorMapper.mapFirestore(FirebaseFirestoreException.Code.PERMISSION_DENIED))
        assertEquals(AppError.NOT_FOUND, ErrorMapper.mapFirestore(FirebaseFirestoreException.Code.NOT_FOUND))
        assertEquals(AppError.UNAUTHENTICATED, ErrorMapper.mapFirestore(FirebaseFirestoreException.Code.UNAUTHENTICATED))
        assertEquals(AppError.UNAVAILABLE, ErrorMapper.mapFirestore(FirebaseFirestoreException.Code.UNAVAILABLE))
        assertEquals(AppError.UNAVAILABLE, ErrorMapper.mapFirestore(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))
        assertEquals(AppError.UNKNOWN, ErrorMapper.mapFirestore(FirebaseFirestoreException.Code.INTERNAL))
    }

    @Test
    fun authExceptions_areMappedToSpecificErrors() {
        assertEquals(AppError.INVALID_CREDENTIALS, ErrorMapper.classify(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "x")))
        assertEquals(AppError.EMAIL_IN_USE, ErrorMapper.classify(FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "x")))
        assertEquals(AppError.TOO_MANY_REQUESTS, ErrorMapper.classify(FirebaseTooManyRequestsException("x")))
        assertEquals(AppError.NETWORK, ErrorMapper.classify(FirebaseNetworkException("x")))
    }

    @Test
    fun weakPassword_isNotMistakenForInvalidCredentials() {
        // WeakPassword ist eine Unterart von InvalidCredentials und muss vorher geprüft werden.
        assertEquals(AppError.WEAK_PASSWORD, ErrorMapper.classify(FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "x", "zu kurz")))
    }

    @Test
    fun appException_keepsItsError() {
        assertEquals(AppError.EMAIL_IN_USE, ErrorMapper.classify(AppException(AppError.EMAIL_IN_USE)))
    }

    @Test
    fun everyError_hasMessage() {
        AppError.entries.forEach { error -> assertTrue("$error ohne Meldung", error.messageRes != 0) }
    }
}
