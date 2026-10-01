package de.hagi089.obelix.ui.documents

import androidx.annotation.StringRes
import de.hagi089.obelix.R
import de.hagi089.obelix.data.documents.DocumentCategory

@StringRes
fun documentCategoryLabel(category: DocumentCategory): Int = when (category) {
    DocumentCategory.VEHICLE -> R.string.document_category_vehicle
    DocumentCategory.INSURANCE -> R.string.document_category_insurance
    DocumentCategory.INVOICE -> R.string.document_category_invoice
    DocumentCategory.WARRANTY -> R.string.document_category_warranty
    DocumentCategory.MANUAL -> R.string.document_category_manual
    DocumentCategory.OTHER -> R.string.document_category_other
}
