package com.maximebier.verso.ui.collections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.DangerButton
import com.maximebier.verso.ui.components.PrimaryButton
import com.maximebier.verso.ui.components.VersoDialog
import com.maximebier.verso.ui.components.VersoTextButton
import com.maximebier.verso.ui.details.DetailsTextField

/** « Renommer la collection » : champ prérempli ; « Renommer » désactivé tant que le nom est vide. */
@Composable
fun RenameCollectionDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    VersoDialog(
        title = stringResource(R.string.collection_rename_title),
        onDismissRequest = onDismiss,
        buttons = {
            VersoTextButton(text = stringResource(R.string.collection_dialog_cancel), onClick = onDismiss)
            PrimaryButton(
                text = stringResource(R.string.collection_rename_confirm),
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            )
        },
    ) {
        DetailsTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.new_collection_name),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** « Supprimer « Nom » ? » : les livres restent dans la bibliothèque. */
@Composable
fun DeleteCollectionDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    VersoDialog(
        title = stringResource(R.string.collection_delete_title, name),
        onDismissRequest = onDismiss,
        buttons = {
            VersoTextButton(text = stringResource(R.string.collection_dialog_cancel), onClick = onDismiss)
            DangerButton(text = stringResource(R.string.collection_delete_confirm), onClick = onConfirm)
        },
    ) {
        Text(stringResource(R.string.collection_delete_body))
    }
}
