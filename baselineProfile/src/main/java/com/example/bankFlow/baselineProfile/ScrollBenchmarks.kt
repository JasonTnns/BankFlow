package com.example.bankflow.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollDashboard() = scroll(Ids.DASHBOARD_LIST) { }

    @Test
    fun scrollAccountHistory() = scroll(Ids.HISTORY_LIST) { device.openAccount() }

    private fun scroll(listId: String, openScreen: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = Target.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 8,
        setupBlock = {
            killProcess()
            pressHome()
            startActivityAndWait()
            device.signInIfNeeded()
            device.waitFor(Ids.ACCOUNT_CARD)
            openScreen()
        },
    ) {
        device.flingList(listId, Direction.DOWN, times = 3)
        device.flingList(listId, Direction.UP, times = 3)
    }
}