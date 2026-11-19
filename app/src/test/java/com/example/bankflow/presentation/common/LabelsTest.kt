package com.example.bankflow.presentation.common

import com.example.bankflow.domain.model.AccountType
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.domain.model.TransferKind
import com.example.bankflow.domain.model.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelsTest {

    private fun assertEachHasItsOwnText(name: String, ids: List<Int>) {
        assertTrue("$name: a value has no text", ids.none { it == 0 })
        assertEquals("$name: two values share one text", ids.size, ids.toSet().size)
    }

    @Test
    fun `every value of every enum has its own label`() {
        assertEachHasItsOwnText("Category", Category.entries.map { it.labelRes() })
        assertEachHasItsOwnText("AccountType", AccountType.entries.map { it.labelRes() })
        assertEachHasItsOwnText("Direction", Direction.entries.map { it.labelRes() })
        assertEachHasItsOwnText("TransferStatus", TransferStatus.entries.map { it.labelRes() })
        assertEachHasItsOwnText("TransferKind", TransferKind.entries.map { it.labelRes() })
        assertEachHasItsOwnText("Theme", ThemePreference.entries.map { it.labelRes() })
        assertEachHasItsOwnText("Theme description", ThemePreference.entries.map { it.descriptionRes() })
    }
}