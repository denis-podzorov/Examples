package package.name.ui

import android.app.Activity
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.runner.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class FragmentAuthPhoneInputViewTest {

    @get:Rule
    var testName = TestName()

    /**
     * Перед запуском всех тестов:
     * - очистка каталогов скриншотов в AVD
     */
    companion object FragmentAuthPhoneInputViewTest {
        val folderName : String = this.javaClass.simpleName
        var currentActivity: Activity? = null

        @BeforeClass
        @JvmStatic
        fun clearExistingImagesBeforeStart() {
            clearExistingImages(folderName)
        }
    }

    /**
     * Перед запуском каждого теста:
     * - запуск тестируемого Фрагмента
     * - получение текущей Activity
     */
    @Before fun launchFragment(){
        KeyValueStore.mocks = true

        val testArgs = bundleOf()
        val scenario = launchFragmentInContainer<AuthPhoneInputFragment>(testArgs, R.style.AppTheme)

        getInstrumentation().runOnMainSync {
            run {
                currentActivity = ActivityLifecycleMonitorRegistry.getInstance().
                                  getActivitiesInStage(Stage.RESUMED).elementAtOrNull(0)
            }
        }
    }

    /**
     * Проверка дизайна authPhoneInputFragment
     */
    @Test fun authPhoneInputFragmentDesign() {
        val currentTestName = getMethodName(testName)

        onView(withId(R.id.phoneInput)).perform(clearText())
        onView(withId(R.id.versionTextInput)).perform(clearText())
        onView(withId(R.id.baseUrlText)).perform(clearText())
        onView(withId(R.id.baseUrlText)).perform(closeSoftKeyboard())
        onView(withId(R.id.baseUrlText)).perform(click())

        clearFocusOnActivity(currentActivity)

        val scrParam = ScreenshotParameters(folderName, currentTestName, currentActivity!!)
        compareViewScreenWithReference(scrParam)
    }

    /**
     * Не указан номер
     */
    @Test fun noPhoneNumber() {
        val currentTestName = getMethodName(testName)

        onView(withId(R.id.phoneInput)).perform(clearText())
        onView(withId(R.id.versionTextInput)).perform(clearText())
        onView(withId(R.id.baseUrlText)).perform(clearText())
        onView(withId(R.id.baseUrlText)).perform(closeSoftKeyboard())

        onView(withId(R.id.continueButton)).perform(click())

        clearFocusOnActivity(currentActivity)

        val scrParam = ScreenshotParameters(
            folderName,
            currentTestName,
            currentActivity!!,
            100,
            0.0f,
            0.0f,
            100.0f,
            90.0f)
        compareViewScreenWithReference(scrParam)
    }

    /**
     * Ввод некорректных символов номера телефона
     */
    @Test fun incorrectPhoneNumberSymbols() {
        val currentTestName = getMethodName(testName)

        onView(withId(R.id.phoneInput)).perform(clearText())
        onView(withId(R.id.phoneInput)).perform(typeText(StringConstants.SYMBOLS_PHONE_INCORRECT))
        onView(withId(R.id.baseUrlText)).perform(click())
        onView(withId(R.id.baseUrlText)).perform(clearText())
        onView(withId(R.id.baseUrlText)).perform(closeSoftKeyboard())
        onView(withId(R.id.versionTextInput)).perform(clearText())

        clearFocusOnActivity(currentActivity)

        val scrParam = ScreenshotParameters(
            folderName,
            currentTestName,
            currentActivity!!,
            100,
            0.0f,
            0.0f,
            100.0f,
            90.0f)
        compareViewScreenWithReference(scrParam)
    }

    /**
     * Номер телефона отсутствует в базе данных
     */
    @Test fun phoneNumberNotFound() {
        val currentTestName = getMethodName(testName)

        AuthApiMock.testStatusCode = 422 // "Номер не найден. Проверьте правильность ввода"
        AuthApiMock.testMessage = "Unprocessable Entity"

        onView(withId(R.id.phoneInput)).perform(clearText())
        onView(withId(R.id.phoneInput)).perform(typeText(StringConstants.PHONE_NUMBER_INCORRECT))
        onView(withId(R.id.versionTextInput)).perform(clearText())
        onView(withId(R.id.baseUrlText)).perform(clearText())
        onView(withId(R.id.baseUrlText)).perform(typeText(StringConstants.URL_ENVIRONMENT_REGRESS))
        onView(withId(R.id.baseUrlText)).perform(closeSoftKeyboard())
        onView(withId(R.id.versionTextInput)).perform(clearText())
        onView(withId(R.id.continueButton)).perform(click())

        clearFocusOnActivity(currentActivity)

        val scrParam = ScreenshotParameters(
            folderName,
            currentTestName,
            currentActivity!!,
            100,
            0.0f,
            0.0f,
            100.0f,
            90.0f)
        compareViewScreenWithReference(scrParam)
    }
}


