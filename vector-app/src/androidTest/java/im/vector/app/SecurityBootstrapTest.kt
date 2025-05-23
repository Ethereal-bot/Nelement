/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import im.vector.app.core.utils.getMatrixInstance
import im.vector.app.features.MainActivity
import im.vector.app.features.crypto.recover.SetupMode
import im.vector.app.features.home.HomeActivity
import im.vector.lib.strings.CommonStrings
import org.hamcrest.CoreMatchers.not
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.matrix.android.sdk.api.session.Session
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@LargeTest
@Ignore
class SecurityBootstrapTest : VerificationTestBase() {

    var existingSession: Session? = null

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun createSessionWithCrossSigning() {
        val matrix = getMatrixInstance()
        val userName = "foobar_${Random.nextLong()}"
        existingSession = createAccountAndSync(matrix, userName, password, true)
        stubAllExternalIntents()
    }

    private fun stubAllExternalIntents() {
        // By default Espresso Intents does not stub any Intents. Stubbing needs to be setup before
        // every test run. In this case all external Intents will be blocked.
        Intents.init()
        intending(not(isInternal())).respondWith(ActivityResult(Activity.RESULT_OK, null))
    }

    @Test
    fun testBasicBootstrap() {
        val userId: String = existingSession!!.myUserId

        uiTestBase.login(userId = userId, password = password, homeServerUrl = homeServerUrl)

        // Thread.sleep(6000)
        withIdlingResource(activityIdlingResource(HomeActivity::class.java)) {
            onView(withId(R.id.roomListContainer))
                    .check(matches(isDisplayed()))
                    .perform(closeSoftKeyboard())
        }

        val activity = EspressoHelper.getCurrentActivity()!!
        val uiSession = (activity as HomeActivity).activeSessionHolder.getActiveSession()

        withIdlingResource(initialSyncIdlingResource(uiSession)) {
            onView(withId(R.id.roomListContainer))
                    .check(matches(isDisplayed()))
        }

        activity.navigator.open4SSetup(activity, SetupMode.NORMAL)

        Thread.sleep(1000)

        onView(withId(R.id.bootstrapSetupSecureUseSecurityKey))
                .check(matches(isDisplayed()))

        onView(withId(R.id.bootstrapSetupSecureUseSecurityPassphrase))
                .check(matches(isDisplayed()))
                .perform(click())

        onView(isRoot())
                .perform(waitForView(withText(CommonStrings.bootstrap_info_text_2)))

        // test back
        onView(isRoot()).perform(pressBack())

        Thread.sleep(1000)

        onView(withId(R.id.bootstrapSetupSecureUseSecurityKey))
                .check(matches(isDisplayed()))

        onView(withId(R.id.bootstrapSetupSecureUseSecurityPassphrase))
                .check(matches(isDisplayed()))
                .perform(click())

        onView(isRoot())
                .perform(waitForView(withText(CommonStrings.bootstrap_info_text_2)))

        onView(withId(R.id.ssss_passphrase_enter_edittext))
                .perform(typeText("person woman man camera tv"))

        onView(withId(R.id.bootstrapSubmit))
                .perform(closeSoftKeyboard(), click())

        // test bad pass
        onView(withId(R.id.ssss_passphrase_enter_edittext))
                .perform(typeText("person woman man cmera tv"))

        onView(withId(R.id.bootstrapSubmit))
                .perform(closeSoftKeyboard(), click())

        onView(withText(CommonStrings.passphrase_passphrase_does_not_match)).check(matches(isDisplayed()))

        onView(withId(R.id.ssss_passphrase_enter_edittext))
                .perform(replaceText("person woman man camera tv"))

        onView(withId(R.id.bootstrapSubmit))
                .perform(closeSoftKeyboard(), click())

        onView(withId(R.id.bottomSheetScrollView))
                .perform(waitForView(withText(CommonStrings.bottom_sheet_save_your_recovery_key_content)))

        intending(hasAction(Intent.ACTION_SEND)).respondWith(ActivityResult(Activity.RESULT_OK, null))

        onView(withId(R.id.recoveryCopy))
                .perform(click())

        // Dismiss dialog
        onView(withText(CommonStrings.ok)).inRoot(RootMatchers.isDialog()).perform(click())

        onView(withId(R.id.bottomSheetScrollView))
                .perform(waitForView(withText(CommonStrings.bottom_sheet_save_your_recovery_key_content)))

        onView(withText(CommonStrings._continue)).perform(click())

        // Assert that all is configured
        val crossSigningInitialized = runBlockingTest {
            uiSession.cryptoService().crossSigningService().isCrossSigningInitialized()
        }
        assert(crossSigningInitialized)
        assert(uiSession.cryptoService().crossSigningService().canCrossSign())
        assert(uiSession.cryptoService().crossSigningService().allPrivateKeysKnown())
        assert(uiSession.cryptoService().keysBackupService().isEnabled())
        assert(uiSession.cryptoService().keysBackupService().currentBackupVersion != null)
        assert(uiSession.sharedSecretStorageService().isRecoverySetup())
        assert(uiSession.sharedSecretStorageService().isMegolmKeyInBackup())
    }
}
