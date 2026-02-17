package com.unamentis.ui.readinglist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.unamentis.R

/**
 * Bottom sheet content for importing a web article by URL.
 *
 * Features:
 * - URL text field with keyboard type URL
 * - Auto-prepends https:// if no scheme
 * - Loading state with progress indicator
 * - Error message display
 * - Auto-focus on appear
 */
@Composable
fun URLImportSheet(
    isLoading: Boolean,
    errorMessage: String? = null,
    onImport: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var urlText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val importLabel = stringResource(R.string.reading_list_import_url_action)
    val urlFieldLabel = stringResource(R.string.reading_list_url_field_label)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.reading_list_url_instructions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = urlFieldLabel },
            label = { Text(stringResource(R.string.reading_list_url_placeholder)) },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false,
                ),
            keyboardActions =
                KeyboardActions(
                    onGo = {
                        if (urlText.isNotBlank() && !isLoading) {
                            onImport(normalizeUrl(urlText))
                        }
                    },
                ),
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onImport(normalizeUrl(urlText)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = importLabel },
            enabled = urlText.isNotBlank() && !isLoading,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(importLabel)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enabled = !isLoading,
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

/**
 * Normalize a URL by prepending https:// if no scheme is provided.
 */
private fun normalizeUrl(url: String): String {
    val trimmed = url.trim()
    return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        "https://$trimmed"
    } else {
        trimmed
    }
}
