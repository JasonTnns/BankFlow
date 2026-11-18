package com.example.bankflow.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun signIn() = device.prepareSignedInApp()

    @Test
    fun startup() = rule.collect(
        packageName = Target.PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitFor(Ids.ACCOUNT_CARD)
    }

    @Test
    fun userJourneys() = rule.collect(packageName = Target.PACKAGE) {
        pressHome()
        startActivityAndWait()
        device.waitFor(Ids.ACCOUNT_CARD)

        device.flingList(Ids.DASHBOARD_LIST, Direction.DOWN, times = 2)
        device.flingList(Ids.DASHBOARD_LIST, Direction.UP, times = 2)

        device.openAccount()
        device.flingList(Ids.HISTORY_LIST, Direction.DOWN, times = 4)

        device.waitFor(Ids.TRANSACTION_ROW).click()
        device.waitFor(Ids.DETAILS_AMOUNT)
        device.pressBack()
        device.waitFor(Ids.HISTORY_LIST)
        device.pressBack()
        device.waitFor(Ids.DASHBOARD_LIST)

        device.openTab(Ids.TAB_TRANSFER, Ids.TRANSFER_CONTINUE)
        device.openTab(Ids.TAB_INSIGHTS, Ids.INSIGHTS_LIST)
        device.openTab(Ids.TAB_PROFILE, Ids.PROFILE_LIST)
        device.openTab(Ids.TAB_HOME, Ids.DASHBOARD_LIST)
    }
}