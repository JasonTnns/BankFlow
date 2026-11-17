package com.example.bankflow.presentation.security

import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.testutil.FakeAppLockSettings
import com.example.bankflow.testutil.MainDispatcherRule
import com.example.bankflow.testutil.MutableAuthRepository
import com.example.bankflow.testutil.TEST_USER
import com.example.bankflow.testutil.TestClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

class AppLockViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeAppLockSettings(initial = true)
    private val auth = MutableAuthRepository(TEST_USER)
    private val clock = TestClock()

    private fun viewModel() = AppLockViewModel(settings, auth, clock)

    private fun AppLockViewModel.awayFor(duration: Duration) {
        onIntent(AppLockIntent.Stopped)
        clock.advance(duration)
        onIntent(AppLockIntent.Started)
    }

    private val shortAbsence = Duration.ofSeconds(10)
    private val longAbsence = AppLockViewModel.LOCK_AFTER


    @Test
    fun `the state is ready only once it is known who is signed in and whether the lock is on`() {
        assertFalse(AppLockState().isReady)
        assertFalse(AppLockState(signedIn = true).isReady)
        assertTrue(AppLockState(signedIn = true, enabled = false).isReady)
        assertTrue(AppLockState(signedIn = false).isReady)
    }

    @Test
    fun `the lock covers the app only when signed in, switched on and locked`() {
        assertTrue(AppLockState(signedIn = true, enabled = true, locked = true).showLock)
        assertFalse(AppLockState(signedIn = true, enabled = false, locked = true).showLock)
        assertFalse(AppLockState(signedIn = false, enabled = true, locked = true).showLock)
        assertFalse(AppLockState(signedIn = true, enabled = true, locked = false).showLock)
        assertFalse(AppLockState(signedIn = null, enabled = true, locked = true).showLock)
    }


    @Test
    fun `a start that finds a stored session is locked when the lock is on`() {
        val state = viewModel().state.value

        assertTrue(state.isReady)
        assertTrue(state.showLock)
    }

    @Test
    fun `a start with the lock off is never locked`() {
        settings.state.value = false

        assertFalse(viewModel().state.value.showLock)
    }

    @Test
    fun `a start with nobody signed in shows no lock`() {
        auth.user.value = null

        val state = viewModel().state.value

        assertTrue(state.isReady)
        assertFalse(state.showLock)
    }

    @Test
    fun `signing in with a password after a signed-out start does not lock`() {
        auth.user.value = null
        val viewModel = viewModel()

        auth.user.value = TEST_USER

        assertTrue(viewModel.state.value.signedIn == true)
        assertFalse(viewModel.state.value.showLock)
    }

    @Test
    fun `logging out while locked removes the lock screen`() {
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.showLock)

        auth.user.value = null

        assertFalse(viewModel.state.value.showLock)
        assertTrue(viewModel.state.value.isReady)
    }


    @Test
    fun `unlocking opens the app`() {
        val viewModel = viewModel()

        viewModel.onIntent(AppLockIntent.UnlockSucceeded)

        assertFalse(viewModel.state.value.showLock)
    }

    @Test
    fun `a cancelled prompt keeps the lock without an error`() {
        val viewModel = viewModel()

        viewModel.onIntent(AppLockIntent.UnlockFailed(AuthFailure.CANCELLED))

        assertTrue(viewModel.state.value.showLock)
        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun `a lockout is reported, and a later success clears it`() {
        val viewModel = viewModel()

        viewModel.onIntent(AppLockIntent.UnlockFailed(AuthFailure.LOCKED_OUT))
        assertEquals(AuthFailure.LOCKED_OUT, viewModel.state.value.failure)
        assertTrue(viewModel.state.value.showLock)

        viewModel.onIntent(AppLockIntent.UnlockSucceeded)
        assertNull(viewModel.state.value.failure)
        assertFalse(viewModel.state.value.showLock)
    }


    @Test
    fun `a short absence does not lock, a rotation certainly does not`() {
        val viewModel = viewModel()
        viewModel.onIntent(AppLockIntent.UnlockSucceeded)

        viewModel.awayFor(shortAbsence)
        viewModel.awayFor(Duration.ZERO)

        assertFalse(viewModel.state.value.showLock)
    }

    @Test
    fun `a long absence locks, exactly at the limit too`() {
        val viewModel = viewModel()
        viewModel.onIntent(AppLockIntent.UnlockSucceeded)

        viewModel.awayFor(longAbsence)

        assertTrue(viewModel.state.value.showLock)
    }

    @Test
    fun `just under the limit does not lock`() {
        val viewModel = viewModel()
        viewModel.onIntent(AppLockIntent.UnlockSucceeded)

        viewModel.awayFor(longAbsence.minusMillis(1))

        assertFalse(viewModel.state.value.showLock)
    }

    @Test
    fun `it can lock again after being unlocked`() {
        val viewModel = viewModel()
        viewModel.onIntent(AppLockIntent.UnlockSucceeded)
        viewModel.awayFor(longAbsence)
        viewModel.onIntent(AppLockIntent.UnlockSucceeded)

        viewModel.awayFor(longAbsence)

        assertTrue(viewModel.state.value.showLock)
    }

    @Test
    fun `an absence with the lock off never locks`() {
        settings.state.value = false
        val viewModel = viewModel()

        viewModel.awayFor(longAbsence.multipliedBy(10))

        assertFalse(viewModel.state.value.showLock)
    }

    @Test
    fun `an absence while signed out never locks`() {
        auth.user.value = null
        val viewModel = viewModel()

        viewModel.awayFor(longAbsence.multipliedBy(10))

        assertFalse(viewModel.state.value.showLock)
    }

    @Test
    fun `coming to the screen without having left it changes nothing`() {
        val viewModel = viewModel()
        viewModel.onIntent(AppLockIntent.UnlockSucceeded)

        viewModel.onIntent(AppLockIntent.Started)

        assertFalse(viewModel.state.value.showLock)
    }

    @Test
    fun `one absence is only counted once`() {
        val viewModel = viewModel()
        viewModel.onIntent(AppLockIntent.UnlockSucceeded)
        viewModel.awayFor(longAbsence)
        viewModel.onIntent(AppLockIntent.UnlockSucceeded)

        viewModel.onIntent(AppLockIntent.Started)

        assertFalse(viewModel.state.value.showLock)
    }


    @Test
    fun `switching the lock on while running does not lock the user out at once`() {
        settings.state.value = false
        val viewModel = viewModel()

        settings.state.value = true

        assertFalse(viewModel.state.value.showLock)
        viewModel.awayFor(longAbsence)
        assertTrue(viewModel.state.value.showLock)
    }
}