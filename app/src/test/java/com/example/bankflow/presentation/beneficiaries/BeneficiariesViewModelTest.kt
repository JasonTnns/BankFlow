package com.example.bankflow.presentation.beneficiaries

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.usecase.AddBeneficiaryUseCase
import com.example.bankflow.domain.usecase.DeleteBeneficiaryUseCase
import com.example.bankflow.domain.usecase.GetBeneficiariesUseCase
import com.example.bankflow.testutil.MainDispatcherRule
import com.example.bankflow.testutil.StubBeneficiaryRepository
import com.example.bankflow.testutil.testBeneficiary
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BeneficiariesViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = StubBeneficiaryRepository()

    private fun viewModel() = BeneficiariesViewModel(
        GetBeneficiariesUseCase(repository),
        AddBeneficiaryUseCase(repository),
        DeleteBeneficiaryUseCase(repository),
    )

    private fun BeneficiariesViewModel.fillAdd(nickname: String = "Mum", number: String = "200000000001") {
        onIntent(BeneficiariesIntent.OpenAdd)
        onIntent(BeneficiariesIntent.NicknameChanged(nickname))
        onIntent(BeneficiariesIntent.NumberChanged(number))
    }

    private fun BeneficiariesViewModel.nicknames() = state.value.items.map { it.nickname }


    @Test
    fun `opening the screen loads the saved recipients`() {
        repository.listResult = AppResult.Success(listOf(testBeneficiary("b1", "Mum")))

        val viewModel = viewModel()

        assertEquals(listOf("Mum"), viewModel.nicknames())
        assertTrue(viewModel.state.value.hasLoaded)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `no recipients is an empty state, not an error`() {
        val state = viewModel().state.value

        assertTrue(state.isEmpty)
        assertNull(state.loadError)
    }

    @Test
    fun `a failed load shows the error, and Retry recovers`() {
        repository.listResult = AppResult.Failure(AppError.NoConnection)
        val viewModel = viewModel()
        assertEquals(AppError.NoConnection, viewModel.state.value.loadError)
        assertFalse(viewModel.state.value.hasLoaded)

        repository.listResult = AppResult.Success(listOf(testBeneficiary("b1", "Mum")))
        viewModel.onIntent(BeneficiariesIntent.Retry)

        assertEquals(listOf("Mum"), viewModel.nicknames())
        assertNull(viewModel.state.value.loadError)
    }


    @Test
    fun `adding a recipient keeps the list sorted by name, ignoring case, and closes the dialog`() {
        repository.listResult = AppResult.Success(listOf(testBeneficiary("b1", "Zed", "300000000001")))
        val viewModel = viewModel()
        viewModel.fillAdd(nickname = "alpha")

        viewModel.onIntent(BeneficiariesIntent.SaveNew)

        assertEquals(listOf("alpha", "Zed"), viewModel.nicknames())
        assertNull(viewModel.state.value.addForm)
    }

    @Test
    fun `typed values are cleaned before they are sent`() {
        val viewModel = viewModel()
        viewModel.fillAdd(nickname = "  Mum  ", number = "2000 0000-0001")

        viewModel.onIntent(BeneficiariesIntent.SaveNew)

        assertEquals(listOf("Mum" to "200000000001"), repository.added)
    }

    @Test
    fun `an incomplete form reports each field without a network call`() {
        val viewModel = viewModel()
        viewModel.fillAdd(nickname = "", number = "123")

        viewModel.onIntent(BeneficiariesIntent.SaveNew)

        val form = viewModel.state.value.addForm!!
        assertNotNull(form.nicknameError)
        assertNotNull(form.accountNumberError)
        assertNull("field errors are not banners", form.banner)
        assertFalse(form.isSaving)
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun `an unknown number is reported under the number field and the form stays open`() {
        repository.addResult = AppResult.Failure(AppError.NotFound)
        val viewModel = viewModel()
        viewModel.fillAdd()

        viewModel.onIntent(BeneficiariesIntent.SaveNew)

        val form = viewModel.state.value.addForm!!
        assertEquals("No account found with that number", form.accountNumberError)
        assertNull(form.banner)
        assertEquals("Mum", form.nickname)
        assertFalse(form.isSaving)
    }

    @Test
    fun `a recipient that is already saved is reported under the number field`() {
        repository.addResult = AppResult.Failure(AppError.BeneficiaryExists)
        val viewModel = viewModel()
        viewModel.fillAdd()

        viewModel.onIntent(BeneficiariesIntent.SaveNew)

        assertEquals("This recipient is already saved", viewModel.state.value.addForm?.accountNumberError)
    }

    @Test
    fun `the server's own-account message shows under the number field`() {
        repository.addResult = AppResult.Failure(
            AppError.Validation(fieldErrors = mapOf("accountNumber" to listOf("That is one of your own accounts"))),
        )
        val viewModel = viewModel()
        viewModel.fillAdd()

        viewModel.onIntent(BeneficiariesIntent.SaveNew)

        assertEquals("That is one of your own accounts", viewModel.state.value.addForm?.accountNumberError)
    }

    @Test
    fun `a network failure shows in the dialog's banner`() {
        repository.addResult = AppResult.Failure(AppError.NoConnection)
        val viewModel = viewModel()
        viewModel.fillAdd()

        viewModel.onIntent(BeneficiariesIntent.SaveNew)

        assertEquals(AppError.NoConnection, viewModel.state.value.addForm?.banner)
    }

    @Test
    fun `editing a field clears its error`() {
        repository.addResult = AppResult.Failure(AppError.NotFound)
        val viewModel = viewModel()
        viewModel.fillAdd()
        viewModel.onIntent(BeneficiariesIntent.SaveNew)

        viewModel.onIntent(BeneficiariesIntent.NumberChanged("2000000000019"))

        assertNull(viewModel.state.value.addForm?.accountNumberError)
    }

    @Test
    fun `the number field keeps only digits, at most twelve`() {
        val viewModel = viewModel()
        viewModel.onIntent(BeneficiariesIntent.OpenAdd)

        viewModel.onIntent(BeneficiariesIntent.NumberChanged("20a0-00 0000 0001 99"))

        assertEquals("200000000001", viewModel.state.value.addForm?.accountNumber)
    }

    @Test
    fun `tapping Save repeatedly sends one request`() {
        val gate = CompletableDeferred<Unit>().also { repository.gate = it }
        val viewModel = viewModel()
        viewModel.fillAdd()

        repeat(3) { viewModel.onIntent(BeneficiariesIntent.SaveNew) }

        assertEquals(1, repository.added.size)
        assertTrue(viewModel.state.value.addForm!!.isSaving)
        gate.complete(Unit)
        assertNull(viewModel.state.value.addForm)
    }

    @Test
    fun `cancelling closes the dialog without saving`() {
        val viewModel = viewModel()
        viewModel.fillAdd()

        viewModel.onIntent(BeneficiariesIntent.DismissAdd)

        assertNull(viewModel.state.value.addForm)
        assertTrue(repository.added.isEmpty())
    }


    private fun withTwo() {
        repository.listResult = AppResult.Success(
            listOf(testBeneficiary("b1", "Bob", "200000000001"), testBeneficiary("b2", "Mum", "300000000001")),
        )
    }

    @Test
    fun `deleting asks first, and cancelling changes nothing`() {
        withTwo()
        val viewModel = viewModel()

        viewModel.onIntent(BeneficiariesIntent.AskDelete(viewModel.state.value.items[0]))
        assertEquals("b1", viewModel.state.value.pendingDelete?.id)

        viewModel.onIntent(BeneficiariesIntent.CancelDelete)

        assertNull(viewModel.state.value.pendingDelete)
        assertTrue(repository.deleted.isEmpty())
        assertEquals(2, viewModel.state.value.items.size)
    }

    @Test
    fun `confirming removes only that recipient`() {
        withTwo()
        val viewModel = viewModel()
        viewModel.onIntent(BeneficiariesIntent.AskDelete(viewModel.state.value.items[0]))

        viewModel.onIntent(BeneficiariesIntent.ConfirmDelete)

        assertEquals(listOf("b1"), repository.deleted)
        assertEquals(listOf("Mum"), viewModel.nicknames())
        assertNull(viewModel.state.value.pendingDelete)
        assertFalse(viewModel.state.value.isDeleting)
    }

    @Test
    fun `a recipient that is already gone counts as deleted`() {
        withTwo()
        repository.deleteResult = AppResult.Failure(AppError.NotFound)
        val viewModel = viewModel()
        viewModel.onIntent(BeneficiariesIntent.AskDelete(viewModel.state.value.items[0]))

        viewModel.onIntent(BeneficiariesIntent.ConfirmDelete)

        assertEquals(listOf("Mum"), viewModel.nicknames())
        assertNull(viewModel.state.value.actionError)
    }

    @Test
    fun `a failed delete keeps the recipient and reports the error`() {
        withTwo()
        repository.deleteResult = AppResult.Failure(AppError.NoConnection)
        val viewModel = viewModel()
        viewModel.onIntent(BeneficiariesIntent.AskDelete(viewModel.state.value.items[0]))

        viewModel.onIntent(BeneficiariesIntent.ConfirmDelete)

        assertEquals(2, viewModel.state.value.items.size)
        assertEquals(AppError.NoConnection, viewModel.state.value.actionError)
        assertNull(viewModel.state.value.pendingDelete)
        assertFalse(viewModel.state.value.isDeleting)
    }

    @Test
    fun `confirming twice deletes once`() {
        withTwo()
        val gate = CompletableDeferred<Unit>().also { repository.gate = it }
        val viewModel = viewModel()
        viewModel.onIntent(BeneficiariesIntent.AskDelete(viewModel.state.value.items[0]))

        repeat(3) { viewModel.onIntent(BeneficiariesIntent.ConfirmDelete) }

        assertEquals(1, repository.deleted.size)
        gate.complete(Unit)
    }
}