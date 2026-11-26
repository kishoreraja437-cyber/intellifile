package com.simats.intellifile.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics

/**
 * Modifier extension to connect Compose text fields with Android Autofill and Google Password Manager
 * using the official semantics-based ContentType API.
 */
fun Modifier.supportAutofill(
    contentType: ContentType
): Modifier = this.semantics {
    this.contentType = contentType
}
