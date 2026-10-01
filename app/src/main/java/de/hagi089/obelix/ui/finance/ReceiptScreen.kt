package de.hagi089.obelix.ui.finance

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.FileSize
import java.io.File

/** Zeigt einen Beleg: Bilder in der App, PDFs über eine externe PDF-App. */
@Composable
fun ReceiptScreen(
    viewModel: ReceiptViewModel,
    ref: FileRef,
    modifier: Modifier = Modifier,
    @StringRes loadingRes: Int = R.string.receipt_loading,
    @StringRes imageDescriptionRes: Int = R.string.receipt_image_description,
    @StringRes pdfInfoRes: Int = R.string.receipt_pdf_info,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        ReceiptState.Loading -> Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(stringResource(loadingRes))
        }
        is ReceiptState.Failed -> Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(current.error.messageRes),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Button(onClick = viewModel::load, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_retry)) }
        }
        is ReceiptState.Image -> Box(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Image(
                bitmap = current.bitmap,
                contentDescription = stringResource(imageDescriptionRes),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is ReceiptState.Pdf -> PdfPane(file = current.file, ref = ref, pdfInfoRes = pdfInfoRes, modifier = modifier)
    }
}

@Composable
private fun PdfPane(file: File, ref: FileRef, @StringRes pdfInfoRes: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var noPdfApp by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(ref.name, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(pdfInfoRes, FileSize.format(ref.sizeBytes)), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    context.startActivity(intent)
                    noPdfApp = false
                } catch (e: ActivityNotFoundException) {
                    noPdfApp = true
                }
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.receipt_open_pdf)) }
        if (noPdfApp) {
            Text(
                text = stringResource(R.string.receipt_no_pdf_app),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}
