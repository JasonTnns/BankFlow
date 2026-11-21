package com.example.bankflow.presentation.profile

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.domain.usecase.GetProfileUseCase
import com.example.bankflow.domain.usecase.UpdateProfileUseCase
import com.example.bankflow.testutil.MainDispatcherRule
import com.example.bankflow.testutil.StubProfileRepository
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = StubProfileRepository()

    private val auth = StubAuthRepository()

    private fun viewModel() = ProfileViewModel(
        GetProfileUseCase(repository),
        UpdateProfileUseCase(repository),
        DeleteAccountUseCase(repository),
        LogoutUseCase(auth),
    )

    private fun ProfileViewModel.fillDelete(password: String = "Passw0rd123") {
        onIntent(ProfileIntent.OpenDelete)
        onIntent(ProfileIntent.DeletePasswordChanged(password))
    }
    private fun ProfileViewModel.theme() = state.value.profile?.user?.themePreference
    private fun ProfileViewModel.name() = state.value.profile?.user?.fullName


    @Test
    fun `opening the screen loads the profile`() {
        val viewModel = viewModel()

        assertEquals("Alice Demo", viewModel.name())
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.loadError)
    }

    @Test
    fun `shows loading until the first load completes`() {
        val gate = CompletableDeferred<Unit>().also { repository.getGate = it }

        val viewModel = viewModel()

        assertTrue(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.profile)
        gate.complete(Unit)
        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.profile)
    }

    @Test
    fun `a failed first load shows the error, and Refresh recovers`() {
        repository.getResult = AppResult.Failure(AppError.NoConnection)
        val viewModel = viewModel()
        assertEquals(AppError.NoConnection, viewModel.state.value.loadError)
        assertNull(viewModel.state.value.profile)

        repository.getResult = null
        viewModel.onIntent(ProfileIntent.Refresh)

        assertNotNull(viewModel.state.value.profile)
        assertNull(viewModel.state.value.loadError)
    }

    @Test
    fun `a failed quiet refresh keeps the profile and shows no error`() {
        val viewModel = viewModel()
        repository.getResult = AppResult.Failure(AppError.Timeout)

        viewModel.onIntent(ProfileIntent.Refresh)

        assertEquals("Alice Demo", viewModel.name())
        assertNull(viewModel.state.value.loadError)
    }

    @Test
    fun `a quiet refresh shows no spinner and picks up changes from elsewhere`() {
        val viewModel = viewModel()
        repository.profile = testProfileRenamed("Alice Elsewhere")
        val gate = CompletableDeferred<Unit>().also { repository.getGate = it }

        viewModel.onIntent(ProfileIntent.Refresh)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Alice Demo", viewModel.name())

        gate.complete(Unit)
        assertEquals("Alice Elsewhere", viewModel.name())
    }

    @Test
    fun `Refresh while a load is in flight is ignored`() {
        val gate = CompletableDeferred<Unit>().also { repository.getGate = it }
        val viewModel = viewModel()

        repeat(3) { viewModel.onIntent(ProfileIntent.Refresh) }

        assertEquals(1, repository.getCalls)
        gate.complete(Unit)
    }

    @Test
    fun `an older refresh can never overwrite a change made while it was in flight`() {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>().also { repository.getGate = it }
        viewModel.onIntent(ProfileIntent.Refresh)

        viewModel.onIntent(ProfileIntent.SelectTheme(ThemePreference.DARK))
        assertEquals(ThemePreference.DARK, viewModel.theme())

        gate.complete(Unit)

        assertEquals(ThemePreference.DARK, viewModel.theme())
    }


    @Test
    fun `choosing a theme moves the selection at once and keeps it when the server agrees`() {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>().also { repository.updateGate = it }

        viewModel.onIntent(ProfileIntent.SelectTheme(ThemePreference.DARK))

        assertEquals(ThemePreference.DARK, viewModel.state.value.pendingTheme)
        assertEquals(ThemePreference.DARK, viewModel.state.value.selectedTheme)
        assertEquals(ThemePreference.SYSTEM, viewModel.theme())

        gate.complete(Unit)

        assertNull(viewModel.state.value.pendingTheme)
        assertEquals(ThemePreference.DARK, viewModel.theme())
        assertEquals(listOf(null to ThemePreference.DARK), repository.updates)
    }

    @Test
    fun `a refused theme change goes back and says why`() {
        val viewModel = viewModel()
        repository.updateError = AppError.NoConnection

        viewModel.onIntent(ProfileIntent.SelectTheme(ThemePreference.DARK))

        assertNull(viewModel.state.value.pendingTheme)
        assertEquals(ThemePreference.SYSTEM, viewModel.state.value.selectedTheme)
        assertEquals(AppError.NoConnection, viewModel.state.value.themeError)
    }

    @Test
    fun `trying again clears the old theme error`() {
        val viewModel = viewModel()
        repository.updateError = AppError.NoConnection
        viewModel.onIntent(ProfileIntent.SelectTheme(ThemePreference.DARK))

        repository.updateError = null
        viewModel.onIntent(ProfileIntent.SelectTheme(ThemePreference.DARK))

        assertNull(viewModel.state.value.themeError)
        assertEquals(ThemePreference.DARK, viewModel.theme())
    }

    @Test
    fun `choosing the theme that is already set sends nothing`() {
        val viewModel = viewModel()

        viewModel.onIntent(ProfileIntent.SelectTheme(ThemePreference.SYSTEM))

        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun `a second theme tap while one is in flight is ignored`() {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>().also { repository.updateGate = it }

        viewModel.onIntent(ProfileIntent.SelectTheme(ThemePreference.DARK))
        viewModel.onIntent(ProfileIntent.SelectTheme(ThemePreference.LIGHT))

        assertEquals(1, repository.updates.size)
        assertEquals(ThemePreference.DARK, viewModel.state.value.selectedTheme)
        gate.complete(Unit)
    }

    @Test
    fun `no theme change is possible before the profile has loaded`() {
        repository.getResult = AppResult.Failure(AppError.NoConnection)
        val viewModel = viewModel()

        viewModel.onIntent(ProfileIntent.SelectTheme(ThemePreference.DARK))

        assertTrue(repository.updates.isEmpty())
    }


    @Test
    fun `editing the name starts from the current one`() {
        val viewModel = viewModel()

        viewModel.onIntent(ProfileIntent.OpenNameEdit)

        assertEquals("Alice Demo", viewModel.state.value.nameEdit?.value)
    }

    @Test
    fun `the editor cannot open without a loaded profile`() {
        repository.getResult = AppResult.Failure(AppError.NoConnection)
        val viewModel = viewModel()

        viewModel.onIntent(ProfileIntent.OpenNameEdit)

        assertNull(viewModel.state.value.nameEdit)
    }

    @Test
    fun `the name field is limited to 100 characters`() {
        val viewModel = viewModel()
        viewModel.onIntent(ProfileIntent.OpenNameEdit)

        viewModel.onIntent(ProfileIntent.NameChanged("x".repeat(150)))

        assertEquals(100, viewModel.state.value.nameEdit?.value?.length)
    }

    @Test
    fun `saving sends the trimmed name, closes the editor and shows the new name`() {
        val viewModel = viewModel()
        viewModel.onIntent(ProfileIntent.OpenNameEdit)
        viewModel.onIntent(ProfileIntent.NameChanged("  Alice Renamed  "))

        viewModel.onIntent(ProfileIntent.SaveName)

        assertEquals(listOf("Alice Renamed" to null), repository.updates)
        assertNull(viewModel.state.value.nameEdit)
        assertEquals("Alice Renamed", viewModel.name())
    }

    @Test
    fun `an unchanged name closes the editor without a request`() {
        val viewModel = viewModel()
        viewModel.onIntent(ProfileIntent.OpenNameEdit)
        viewModel.onIntent(ProfileIntent.NameChanged("  Alice Demo "))

        viewModel.onIntent(ProfileIntent.SaveName)

        assertNull(viewModel.state.value.nameEdit)
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun `a blank name is reported under the field without a request`() {
        val viewModel = viewModel()
        viewModel.onIntent(ProfileIntent.OpenNameEdit)
        viewModel.onIntent(ProfileIntent.NameChanged("   "))

        viewModel.onIntent(ProfileIntent.SaveName)

        val edit = viewModel.state.value.nameEdit!!
        assertNotNull(edit.fieldError)
        assertNull("a field error is not a banner", edit.banner)
        assertFalse(edit.isSaving)
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun `a message from the server shows under the field and nothing typed is lost`() {
        repository.updateError = AppError.Validation(fieldErrors = mapOf("fullName" to listOf("Full name is too long")))
        val viewModel = viewModel()
        viewModel.onIntent(ProfileIntent.OpenNameEdit)
        viewModel.onIntent(ProfileIntent.NameChanged("Alice Renamed"))

        viewModel.onIntent(ProfileIntent.SaveName)

        val edit = viewModel.state.value.nameEdit!!
        assertEquals("Full name is too long", edit.fieldError)
        assertEquals("Alice Renamed", edit.value)
        assertEquals("Alice Demo", viewModel.name())
    }

    @Test
    fun `a network failure shows as a banner and the editor stays open`() {
        repository.updateError = AppError.NoConnection
        val viewModel = viewModel()
        viewModel.onIntent(ProfileIntent.OpenNameEdit)
        viewModel.onIntent(ProfileIntent.NameChanged("Alice Renamed"))

        viewModel.onIntent(ProfileIntent.SaveName)

        assertEquals(AppError.NoConnection, viewModel.state.value.nameEdit?.banner)
        assertFalse(viewModel.state.value.nameEdit!!.isSaving)
    }

    @Test
    fun `typing clears the error`() {
        repository.updateError = AppError.NoConnection
        val viewModel = viewModel()
        viewModel.onIntent(ProfileIntent.OpenNameEdit)
        viewModel.onIntent(ProfileIntent.NameChanged("Alice Renamed"))
        viewModel.onIntent(ProfileIntent.SaveName)

        viewModel.onIntent(ProfileIntent.NameChanged("Alice Renamed 2"))

        assertNull(viewModel.state.value.nameEdit?.error)
    }

    @Test
    fun `tapping Save repeatedly sends one request`() {
        val gate = CompletableDeferred<Unit>().also { repository.updateGate = it }
        val viewModel = viewModel()
        viewModel.onIntent(ProfileIntent.OpenNameEdit)
        viewModel.onIntent(ProfileIntent.NameChanged("Alice Renamed"))

        repeat(3) { viewModel.onIntent(ProfileIntent.SaveName) }

        assertEquals(1, repository.updates.size)
        assertTrue(viewModel.state.value.nameEdit!!.isSaving)
        gate.complete(Unit)
        assertNull(viewModel.state.value.nameEdit)
    }

    @Test
    fun `a theme change cannot start while the name is saving`() {
        val gate = CompletableDeferred<Unit>().also { repository.updateGate = it }
        val viewModel = viewModel()
        viewModel.onIntent(ProfileIntent.OpenNameEdit)
        viewModel.onIntent(ProfileIntent.NameChanged("Alice Renamed"))
        viewModel.onIntent(ProfileIntent.SaveName)

        viewModel.onIntent(ProfileIntent.SelectTheme(ThemePreference.DARK))

        assertEquals(1, repository.updates.size)
        assertTrue(viewModel.state.value.isUpdating)
        gate.complete(Unit)
    }

    @Test
    fun `cancelling closes the editor without saving`() {
        val viewModel = viewModel()
        viewModel.onIntent(ProfileIntent.OpenNameEdit)

        viewModel.onIntent(ProfileIntent.DismissNameEdit)

        assertNull(viewModel.state.value.nameEdit)
        assertTrue(repository.updates.isEmpty())
    }

    // ---------------------------------------------------------------- deleting the account

    @Test
    fun `deleting asks for the password first, nothing is sent yet`() {
        val viewModel = viewModel()

        viewModel.onIntent(ProfileIntent.OpenDelete)

        assertNotNull(viewModel.state.value.deleteForm)
        assertTrue(repository.deleteCalls.isEmpty())
    }

    @Test
    fun `an empty password is reported under the field, with no request and no sign-out`() {
        val viewModel = viewModel()
        viewModel.onIntent(ProfileIntent.OpenDelete)

        viewModel.onIntent(ProfileIntent.ConfirmDelete)

        val form = viewModel.state.value.deleteForm!!
        assertNotNull(form.passwordError)
        assertNull(form.banner)
        assertTrue(repository.deleteCalls.isEmpty())
        assertEquals(0, auth.logoutCalls)
    }

    @Test
    fun `the right password deletes the account and signs the user out`() {
        val viewModel = viewModel()
        viewModel.fillDelete("Passw0rd123")

        viewModel.onIntent(ProfileIntent.ConfirmDelete)

        assertEquals(listOf("Passw0rd123"), repository.deleteCalls)
        assertEquals(1, auth.logoutCalls)
        assertNull(viewModel.state.value.deleteForm)
    }

    @Test
    fun `a wrong password shows under the field, keeps the dialog and does not sign out`() {
        repository.deleteError = AppError.PasswordIncorrect
        val viewModel = viewModel()
        viewModel.fillDelete("wrong")

        viewModel.onIntent(ProfileIntent.ConfirmDelete)

        val form = viewModel.state.value.deleteForm!!
        assertEquals(UiText.Res(R.string.error_password_incorrect), form.passwordError)
        assertNull(form.banner)
        assertFalse(form.isDeleting)
        assertEquals("wrong", form.password)
        assertEquals(0, auth.logoutCalls)
    }

    @Test
    fun `a network failure shows as a banner and nothing is lost`() {
        repository.deleteError = AppError.NoConnection
        val viewModel = viewModel()
        viewModel.fillDelete()

        viewModel.onIntent(ProfileIntent.ConfirmDelete)

        assertEquals(AppError.NoConnection, viewModel.state.value.deleteForm?.banner)
        assertEquals(0, auth.logoutCalls)
    }

    @Test
    fun `typing clears the error`() {
        repository.deleteError = AppError.PasswordIncorrect
        val viewModel = viewModel()
        viewModel.fillDelete("wrong")
        viewModel.onIntent(ProfileIntent.ConfirmDelete)

        viewModel.onIntent(ProfileIntent.DeletePasswordChanged("wrong2"))

        assertNull(viewModel.state.value.deleteForm?.passwordError)
    }

    @Test
    fun `tapping Delete repeatedly sends one request`() {
        val gate = CompletableDeferred<Unit>().also { repository.deleteGate = it }
        val viewModel = viewModel()
        viewModel.fillDelete()

        repeat(3) { viewModel.onIntent(ProfileIntent.ConfirmDelete) }

        assertEquals(1, repository.deleteCalls.size)
        assertTrue(viewModel.state.value.isUpdating)
        gate.complete(Unit)
        assertEquals(1, auth.logoutCalls)
    }

    @Test
    fun `while deleting, the dialog cannot be dismissed and nothing else can be changed`() {
        val gate = CompletableDeferred<Unit>().also { repository.deleteGate = it }
        val viewModel = viewModel()
        viewModel.fillDelete()
        viewModel.onIntent(ProfileIntent.ConfirmDelete)

        viewModel.onIntent(ProfileIntent.DismissDelete)
        viewModel.onIntent(ProfileIntent.SelectTheme(ThemePreference.DARK))

        assertNotNull(viewModel.state.value.deleteForm)
        assertTrue(repository.updates.isEmpty())
        gate.complete(Unit)
    }

    @Test
    fun `the password field is limited, and cancelling closes the dialog without a request`() {
        val viewModel = viewModel()
        viewModel.fillDelete("x".repeat(300))
        assertEquals(200, viewModel.state.value.deleteForm?.password?.length)

        viewModel.onIntent(ProfileIntent.DismissDelete)

        assertNull(viewModel.state.value.deleteForm)
        assertTrue(repository.deleteCalls.isEmpty())
    }

    @Test
    fun `the dialog cannot open while another change is in flight`() {
        val gate = CompletableDeferred<Unit>().also { repository.updateGate = it }
        val viewModel = viewModel()
        viewModel.onIntent(ProfileIntent.SelectTheme(ThemePreference.DARK))

        viewModel.onIntent(ProfileIntent.OpenDelete)

        assertNull(viewModel.state.value.deleteForm)
        gate.complete(Unit)
    }

    private fun testProfileRenamed(name: String) =
        com.example.bankflow.testutil.testProfile(name = name)
}