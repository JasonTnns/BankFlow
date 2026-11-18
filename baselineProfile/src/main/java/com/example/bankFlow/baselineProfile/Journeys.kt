package com.example.bankflow.baselineprofile

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

object Target {
    const val PACKAGE = "com.example.bankflow"
    const val ACTIVITY = "$PACKAGE/$PACKAGE.MainActivity"

    const val EMAIL = "alice@example.com"
    const val PASSWORD = "Passw0rd123"

    const val WAIT_MS = 15_000L
}

private const val POLL_MS = 200L
private const val HINT = "Is the backend running (npm start) and seeded (npm run seed -- --reset)?"

fun UiDevice.launchTarget() {
    executeShellCommand("am start -n ${Target.ACTIVITY}")
}

fun UiDevice.stopTarget() {
    executeShellCommand("am force-stop ${Target.PACKAGE}")
}

fun UiDevice.isShown(id: String): Boolean = hasObject(By.res(id))

fun UiDevice.waitFor(id: String, timeoutMs: Long = Target.WAIT_MS): UiObject2 =
    wait(Until.findObject(By.res(id)), timeoutMs) ?: error("Timed out waiting for '$id'. $HINT")

fun UiDevice.signInIfNeeded() {
    val deadline = System.currentTimeMillis() + Target.WAIT_MS
    while (System.currentTimeMillis() < deadline) {
        when {
            isShown(Ids.DASHBOARD_LIST) -> return
            isShown(Ids.LOGIN_EMAIL) -> {
                signIn()
                return
            }
        }
        Thread.sleep(POLL_MS)
    }
    error("Neither the login form nor the dashboard appeared. $HINT")
}

private fun UiDevice.signIn() {
    findObject(By.res(Ids.LOGIN_EMAIL)).text = Target.EMAIL
    findObject(By.res(Ids.LOGIN_PASSWORD)).text = Target.PASSWORD
    if (isKeyboardShown()) pressBack()
    waitFor(Ids.LOGIN_SUBMIT).click()
    waitFor(Ids.DASHBOARD_LIST)
}

private fun UiDevice.isKeyboardShown(): Boolean =
    executeShellCommand("dumpsys input_method").contains("mInputShown=true")

fun UiDevice.prepareSignedInApp() {
    stopTarget()
    launchTarget()
    signInIfNeeded()
    waitFor(Ids.ACCOUNT_CARD)
    pressHome()
}


fun UiDevice.flingList(id: String, direction: Direction = Direction.DOWN, times: Int = 3) {
    repeat(times) {
        val list = waitFor(id)
        list.setGestureMargin(displayWidth / 5)
        list.fling(direction)
        waitForIdle()
    }
}


fun UiDevice.openAccount(nameContains: String = "Everyday Checking") {
    val card = wait(Until.findObject(By.res(Ids.ACCOUNT_CARD).descContains(nameContains)), Target.WAIT_MS)
        ?: error("No account card for '$nameContains'. $HINT")
    card.click()
    waitFor(Ids.HISTORY_LIST)
    waitFor(Ids.TRANSACTION_ROW)
}

fun UiDevice.openTab(tabId: String, contentId: String) {
    waitFor(tabId).click()
    waitFor(contentId)
}