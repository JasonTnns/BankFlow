@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bankflow.presentation.transfer

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun TransferScreen(
    onDone: () -> Unit,
    viewModel: TransferViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TransferContent(state = state, onIntent = viewModel::onIntent, onDone = onDone)
}

@Composable
fun TransferContent(
    state: TransferState,
    onIntent: (TransferIntent) -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(enabled = state.step == TransferStep.REVIEW && !state.isSubmitting) {
        onIntent(TransferIntent.BackToForm)
    }
    BackHandler(enabled = state.step == TransferStep.FAILED) {
        onIntent(TransferIntent.EditAfterFailure)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.step == TransferStep.REVIEW) "Review transfer" else "Send money") },
                navigationIcon = {
                    if (state.step == TransferStep.REVIEW) {
                        IconButton(
                            onClick = { onIntent(TransferIntent.BackToForm) },
                            enabled = !state.isSubmitting,
                            modifier = Modifier.testTag("review_top_back"),
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
            )
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (state.step) {
            TransferStep.FORM -> TransferFormContent(state, onIntent, content)
            TransferStep.REVIEW -> state.review?.let { review ->
                TransferReviewContent(review, state.isSubmitting, state.submitError, onIntent, content)
            }
            TransferStep.SUCCESS -> state.result?.let { result ->
                TransferSuccessContent(
                    result = result,
                    review = state.review,
                    onDone = {
                        onIntent(TransferIntent.StartNew)
                        onDone()
                    },
                    onAnother = { onIntent(TransferIntent.StartNew) },
                    modifier = content,
                )
            }
            TransferStep.FAILED -> state.failure?.let { error ->
                TransferFailureContent(
                    error = error,
                    onEdit = { onIntent(TransferIntent.EditAfterFailure) },
                    onClose = {
                        onIntent(TransferIntent.StartNew)
                        onDone()
                    },
                    modifier = content,
                )
            }
        }
    }
}