@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bankflow.presentation.transfer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun TransferScreen(
    onDone: () -> Unit,
    onManageRecipients: () -> Unit,
    viewModel: TransferViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(TransferIntent.RefreshRecipients)
    }

    TransferContent(
        state = state,
        onIntent = viewModel::onIntent,
        onDone = onDone,
        onManageRecipients = onManageRecipients,
    )
}

@Composable
fun TransferContent(
    state: TransferState,
    onIntent: (TransferIntent) -> Unit,
    onDone: () -> Unit,
    onManageRecipients: () -> Unit = {},
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
                title = { Text(stringResource(if (state.step == TransferStep.REVIEW) R.string.transfer_review_title else R.string.transfer_title)) },
                navigationIcon = {
                    if (state.step == TransferStep.REVIEW) {
                        IconButton(
                            onClick = { onIntent(TransferIntent.BackToForm) },
                            enabled = !state.isSubmitting,
                            modifier = Modifier.testTag("review_top_back"),
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                    }
                },
            )
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (state.step) {
            TransferStep.FORM -> TransferFormContent(state, onIntent, content, onManageRecipients)
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
                    saveRecipient = state.saveRecipient,
                    canSaveRecipient = state.unsavedRecipientNumber != null,
                    onSaveRecipient = { onIntent(TransferIntent.SaveRecipient) },
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