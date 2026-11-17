package com.example.bankflow.presentation.security

import com.example.bankflow.domain.model.User
import com.example.bankflow.domain.repository.AuthRepository
import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.domain.security.UnlockResult
import com.example.bankflow.domain.security.VaultState
import com.example.bankflow.testutil.FakeAppLockSettings
import com.example.bankflow.testutil.FakeSessionVault
import com.example.bankflow.testutil.MainDispatcherRule
import com.example.bankflow.testutil.MutableAuthRepository
import com.example.bankflow.testutil.TEST_USER
import com.example.bankflow.testutil.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeAppLockSettings(initial = true)
    private val auth = MutableAuthRepository(TEST_USER)
    private val vault = FakeSessionVault(VaultState.OPEN)
    private val clock = TestClock()

    private fun viewModel(repository: AuthRepository = auth) = AppLockViewModel(settings, repository, vault, clock)

    private fun gatedAuth(): AuthRepository = object : AuthRepository by auth {
        override val currentUser: Flow<User?> = vault.stateFlow.flatMapLatest { state ->
            when (state) {
                VaultState.OPEN -> auth.user
                VaultState.EMPTY -> flowOf<User?>(null)
                else -> emptyFlow<User?>()
            }
        }
    }

    private fun AppLockViewModel.awayFor(duration: Duration) {
        onIntent(AppLockIntent.Stopped)
        clock.advance(duration)
        onIntent(AppLockIntent.Started)
    }

    private val shortAbsence = Duration.ofSeconds(10)
    private val longAbsence = AppLockViewModel.LOCK_AFTER


    @Test
    fun `the state is ready only once it is known which screen to show`() {
        assertFalse(AppLockState().isReady)
        assertFalse(AppLockState(vaultState = VaultState.OPEN, signedIn = true).isReady)
        assertTrue(AppLockState(vaultState = VaultState.OPEN, signedIn = true, enabled = false).isReady)
        assertTrue(AppLockState(vaultState = VaultState.OPEN, signedIn = false).isReady)
        assertTrue(AppLockState(vaultState = VaultState.EMPTY, signedIn = false).isReady)
        assertFalse(AppLockState(vaultState = VaultState.UNKNOWN, signedIn = false).isReady)
        assertTrue("a locked vault needs nothing else", AppLockState(vaultState = VaultState.LOCKED).isReady)
    }

    @Test
    fun `the lock covers the app when the vault is locked, or when signed in, switched on and locked`() {
        val open = VaultState.OPEN
        assertTrue(AppLockState(vaultState = VaultState.LOCKED).showLock)
        assertTrue(AppLockState(vaultState = open, signedIn = true, enabled = true, locked = true).showLock)
        assertFalse(AppLockState(vaultState = open, signedIn = true, enabled = false, locked = true).showLock)
        assertFalse(AppLockState(vaultState = open, signedIn = false, enabled = true, locked = true).showLock)
        assertFalse(AppLockState(vaultState = open, signedIn = true, enabled = true, locked = false).showLock)
        assertFalse(AppLockState(vaultState = open, signedIn = null, enabled = true, locked = true).showLock)
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
        assertEquals("an open vault needs no unlocking", 0, vault.unlocks)
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


    @Test
    fun `a locked vault shows the lock even though nothing else is known yet`() {
        vault.stateFlow.value = VaultState.LOCKED

        val state = viewModel(gatedAuth()).state.value

        assertTrue(state.isReady)
        assertTrue(state.showLock)
        assertNull("the session cannot be read yet", state.signedIn)
    }

    @Test
    fun `a locked vault shows the lock even when the preference is off`() {
        settings.state.value = false
        vault.stateFlow.value = VaultState.LOCKED

        assertTrue(viewModel(gatedAuth()).state.value.showLock)
    }

    @Test
    fun `unlocking the vault opens the app and does not ask a second time`() {
        vault.stateFlow.value = VaultState.LOCKED
        val viewModel = viewModel(gatedAuth())

        viewModel.onIntent(AppLockIntent.UnlockSucceeded)

        assertEquals(1, vault.unlocks)
        assertEquals(VaultState.OPEN, viewModel.state.value.vaultState)
        assertTrue(viewModel.state.value.signedIn == true)
        assertFalse("the unlock WAS the cold-start check", viewModel.state.value.showLock)
    }

    @Test
    fun `a proof that was too old keeps the lock and asks again`() {
        vault.stateFlow.value = VaultState.LOCKED
        vault.unlockResult = UnlockResult.AUTHENTICATION_REQUIRED
        val viewModel = viewModel(gatedAuth())

        viewModel.onIntent(AppLockIntent.UnlockSucceeded)

        assertEquals(AuthFailure.ERROR, viewModel.state.value.failure)
        assertTrue(viewModel.state.value.showLock)
    }

    @Test
    fun `a destroyed key signs the user out and shows the login screen`() {
        vault.stateFlow.value = VaultState.LOCKED
        vault.unlockResult = UnlockResult.KEY_INVALIDATED
        val viewModel = viewModel(gatedAuth())

        viewModel.onIntent(AppLockIntent.UnlockSucceeded)

        val state = viewModel.state.value
        assertEquals(VaultState.EMPTY, state.vaultState)
        assertEquals(false, state.signedIn)
        assertTrue(state.isReady)
        assertFalse(state.showLock)
    }

    @Test
    fun `logging out from the lock screen leaves a clean signed-out app`() {
        vault.stateFlow.value = VaultState.LOCKED
        val viewModel = viewModel(gatedAuth())
        assertTrue(viewModel.state.value.showLock)

        vault.stateFlow.value = VaultState.EMPTY

        assertFalse(viewModel.state.value.showLock)
        assertEquals(false, viewModel.state.value.signedIn)
    }
}