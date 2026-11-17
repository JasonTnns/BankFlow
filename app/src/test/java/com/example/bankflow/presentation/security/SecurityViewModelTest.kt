package com.example.bankflow.presentation.security

import app.cash.turbine.test
import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.domain.security.AuthOutcome
import com.example.bankflow.domain.security.BiometricAvailability
import com.example.bankflow.testutil.FakeAppLockSettings
import com.example.bankflow.testutil.FakeBiometricSupport
import com.example.bankflow.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SecurityViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeAppLockSettings(initial = false)
    private val biometrics = FakeBiometricSupport()

    private fun viewModel() = SecurityViewModel(settings, biometrics)

    @Test
    fun `the state shows the stored setting and what this device can do`() {
        settings.state.value = true
        biometrics.current = BiometricAvailability.NONE_ENROLLED

        val state = viewModel().state.value

        assertTrue(state.enabled)
        assertEquals(BiometricAvailability.NONE_ENROLLED, state.availability)
    }

    @Test
    fun `turning on asks for verification first and changes nothing yet`() = runTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onIntent(SecurityIntent.Toggle(true))
            assertEquals(SecurityEffect.Authenticate(true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertFalse(viewModel.state.value.enabled)
        assertFalse(settings.state.value)
    }

    @Test
    fun `a successful verification switches it on`() {
        val viewModel = viewModel()

        viewModel.onIntent(SecurityIntent.AuthResult(enable = true, outcome = AuthOutcome.Success))

        assertTrue(viewModel.state.value.enabled)
        assertTrue(settings.state.value)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `a cancelled prompt changes nothing and shows no error`() {
        val viewModel = viewModel()

        viewModel.onIntent(SecurityIntent.AuthResult(true, AuthOutcome.Failed(AuthFailure.CANCELLED)))

        assertFalse(viewModel.state.value.enabled)
        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun `a lockout is reported and nothing changes`() {
        val viewModel = viewModel()

        viewModel.onIntent(SecurityIntent.AuthResult(true, AuthOutcome.Failed(AuthFailure.LOCKED_OUT)))

        assertEquals(AuthFailure.LOCKED_OUT, viewModel.state.value.failure)
        assertFalse(settings.state.value)
    }

    @Test
    fun `it cannot be switched on when the phone has nothing to verify with`() = runTest {
        biometrics.current = BiometricAvailability.NONE_ENROLLED
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onIntent(SecurityIntent.Toggle(true))
            expectNoEvents()
            cancel()
        }

        assertEquals(AuthFailure.UNAVAILABLE, viewModel.state.value.failure)
        assertFalse(settings.state.value)
    }

    @Test
    fun `turning off also asks for verification`() = runTest {
        settings.state.value = true
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onIntent(SecurityIntent.Toggle(false))
            assertEquals(SecurityEffect.Authenticate(false), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(settings.state.value)

        viewModel.onIntent(SecurityIntent.AuthResult(false, AuthOutcome.Success))
        assertFalse(settings.state.value)
    }

    @Test
    fun `turning off is possible without a prompt once verification has become impossible`() = runTest {
        settings.state.value = true
        biometrics.current = BiometricAvailability.NONE_ENROLLED
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.canToggle)

        viewModel.effects.test {
            viewModel.onIntent(SecurityIntent.Toggle(false))
            expectNoEvents()
            cancel()
        }

        assertFalse(settings.state.value)
    }

    @Test
    fun `the switch is disabled when it is off and cannot be used`() {
        biometrics.current = BiometricAvailability.NO_HARDWARE

        assertFalse(viewModel().state.value.canToggle)
    }

    @Test
    fun `asking for the setting it already has does nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onIntent(SecurityIntent.Toggle(false))
            expectNoEvents()
            cancel()
        }
    }

    @Test
    fun `refreshing picks up changes made in the phone's settings`() {
        biometrics.current = BiometricAvailability.NONE_ENROLLED
        val viewModel = viewModel()

        biometrics.current = BiometricAvailability.AVAILABLE
        viewModel.onIntent(SecurityIntent.Refresh)

        assertEquals(BiometricAvailability.AVAILABLE, viewModel.state.value.availability)
    }

    @Test
    fun `a storage failure leaves the setting as it was and says so`() {
        settings.failOnWrite = true
        val viewModel = viewModel()

        viewModel.onIntent(SecurityIntent.AuthResult(true, AuthOutcome.Success))

        assertFalse(viewModel.state.value.enabled)
        assertEquals(AuthFailure.ERROR, viewModel.state.value.failure)
        assertFalse(viewModel.state.value.isSaving)
    }
}