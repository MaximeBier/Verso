package com.maximebier.verso.ui.library

import android.text.TextUtils
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import com.maximebier.verso.R
import com.maximebier.verso.importer.RejectReason
import com.maximebier.verso.ui.common.formatDate
import com.maximebier.verso.ui.components.PrimaryButton
import com.maximebier.verso.ui.components.VersoDialog
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoTextButton

/** 1.05 — Livre déjà importé. Toucher à l'extérieur équivaut à « Ignorer » (choix sûr). */
@Composable
fun DuplicateImportDialog(
    fileName: String,
    existingTitle: String,
    importedAt: Long,
    onReplace: () -> Unit,
    onIgnore: () -> Unit,
) {
    val date = formatDate(importedAt, stringResource(R.string.common_date_pattern_full))
    // Chaîne en CDATA avec <b> : on encode les valeurs puis on interprète le HTML (référence §1).
    val body = stringResource(
        R.string.import_duplicate_body,
        TextUtils.htmlEncode(fileName),
        TextUtils.htmlEncode(existingTitle),
        date,
    )
    VersoDialog(
        title = stringResource(R.string.import_duplicate_title),
        onDismissRequest = onIgnore,
        buttons = {
            VersoTextButton(text = stringResource(R.string.import_duplicate_replace), onClick = onReplace)
            PrimaryButton(text = stringResource(R.string.import_duplicate_ignore), onClick = onIgnore)
        },
    ) {
        Text(AnnotatedString.fromHtml(body))
        Text(stringResource(R.string.import_duplicate_position_kept))
    }
}

/** 1.06 — Fichier refusé : pas un EPUB, DRM Adobe ou LCP, ou EPUB illisible. */
@Composable
fun RejectedImportDialog(reason: RejectReason, fileName: String, onDismiss: () -> Unit) {
    val message = when (reason) {
        RejectReason.NOT_EPUB -> stringResource(R.string.import_error_not_epub, fileName)
        RejectReason.DRM -> stringResource(R.string.import_error_drm, fileName)
        RejectReason.UNREADABLE -> stringResource(R.string.import_error_unreadable, fileName)
    }
    VersoDialog(
        title = stringResource(R.string.import_error_title),
        onDismissRequest = onDismiss,
        icon = VersoIcons.AlertTriangle,
        buttons = { PrimaryButton(text = stringResource(R.string.import_error_ok), onClick = onDismiss) },
    ) {
        Text(message)
        Text(stringResource(R.string.import_error_nothing_added))
    }
}
