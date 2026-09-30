package de.hagi089.obelix.core.error

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
    fun everyError_hasMessage() {
        AppError.entries.forEach { error -> assertTrue("$error ohne Meldung", error.messageRes != 0) }
    }
}
