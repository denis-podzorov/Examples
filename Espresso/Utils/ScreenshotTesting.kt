package package.name.utils

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.Screenshot
import java.io.File
import java.io.FileOutputStream

/**
 * The ScreenshotTesting contains public methods for operations
 * with screenshots at screenshot tests: making, saving, compare and others
 */
object ScreenshotTesting {

    /**
     * 1. Makes screenshot from current Activity
     * 2. Cuts screenshot, if new dimensions of screenshot are recorded in ScreenshotParameters
     * 3. Saves it on AVD memory on the path:
     *    /data/data/{package name}/files/{name of a testing class}/{name of a test method and screen density}.png)
     *      where, for example:
     *      {package name} is "package.name"
     *      {name of a testing class} is may be "FragmentAuthPhoneInputViewTest" - name of class which
     *                                contains tests of any screen
     *      {name of a test method and screen density} is may be "authPhoneInputFragmentDesign_1080x2340_400dpi"
     *                                              - name of the test in the testing class and screen density
     * 4. Gets a reference screenshot, name of which corresponds with name of the current screenshot
     *    and which is located in folder src/androidTest/assets/{name of a testing class} at the mobile project
     * 5. Compares the reference screenshot with the current screenshot
     */
    fun compareViewScreenWithReference(scrParam: ScreenshotParameters){
        lateinit var testBmp: Bitmap
        val bmp = Screenshot.capture(scrParam.testingView).bitmap
        val filename = "${scrParam.refName}_${bmp.width}x${bmp.height}_${bmp.density}dpi.png"

        if(scrParam.widthShareFromLeft == 0.0f && scrParam.heightShareFromBottom == 0.0f &&
            scrParam.widthShareOfResult == 100.0f && scrParam.heightShareOfResult == 100.0f){
            testBmp = bmp
        }else{
            var x: Int = if(scrParam.widthShareFromLeft == 0.0f) 0
                else Math.round(bmp.width / 100 * scrParam.widthShareFromLeft)

            var y: Int = if(scrParam.heightShareFromBottom == 0.0f) 0
                else Math.round(bmp.height / 100 * scrParam.heightShareFromBottom)

            var width: Int = if(scrParam.widthShareOfResult == 100.0f) bmp.width - x
                else Math.round((bmp.width - x) / 100 * scrParam.widthShareOfResult)

            var height: Int = if(scrParam.heightShareOfResult == 100.0f) bmp.height - y
                else Math.round((bmp.height - y) / 100 * scrParam.heightShareOfResult)

            testBmp = Bitmap.createBitmap(bmp, x, y, width, height)
        }

        saveScreenshot(scrParam.folderName, filename, testBmp, scrParam.quality)
        var refBmp = getReference(scrParam.folderName, filename)

        testBmp.compare(refBmp)
    }

    /**
     * Saves screenshot on AVD memory on the path:
     *   /data/data/{package name}/files/{name of a testing class}/{name of a test method and screen density}.png)
     *     where, for example:
     *     {package name} is "package.name"
     *     {name of a testing class} is may be "FragmentAuthPhoneInputViewTest" - name of class which
     *                               contains tests of any screen
     *     {name of a test method and screen density} is may be "authPhoneInputFragmentDesign_1080x2340_400dpi"
     *                                              - name of the test in the testing class and screen density
     */
    fun saveScreenshot(
        folderName: String,
        filename: String,
        bmp: Bitmap,
        quality: Int
    ) {
        val path = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, folderName)

        if (!path.exists()) {
            path.mkdirs()
        }
        FileOutputStream("$path/$filename").use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, quality, out)
        }
        Log.i("saveScreenshot","Saved screenshot to $path/$filename")
    }

    /**
     * Deletes all screenshots from AVD memory folder on the path:
     *  /data/data/{package name}/files/{name of a testing class}
     *      where, for example:
     *      {package name} is "package.name"
     *      {name of a testing class} is may be "FragmentAuthPhoneInputViewTest" - name of class which
     *                                  contains tests of any screen
     *  This method should be called in "companion object" of the testing class under @BeforeClass annotation
     */
    fun clearExistingImages(folderName: String) {
        val path = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, folderName)
        path.deleteRecursively()
    }

    /**
     * Compares this Bitmap with other Bitmap by size and pixels
     */
    fun Bitmap.compare(other: Bitmap) {
        if (this.width != other.width ||
            this.height != other.height ||
            this.density != other.density) {
            throw AssertionError(
                "Size of screenshot does not match golden file (check device density)")
        }
        // Compare row by row to save memory on device
        val row1 = IntArray(width)
        val row2 = IntArray(width)
        for (column in 0 until height) {
            // Read one row per bitmap and compare
            this.getRow(row1, column)
            other.getRow(row2, column)
            if (!row1.contentEquals(row2)) {
                throw AssertionError("Sizes match but bitmap content has differences")
            }
        }
    }

    /**
     * Gets a reference screenshot from mobile project,
     * which located in folder src/androidTest/assets/{folderName}
     * and has name "filename"
     */
    fun getReference(folderName: String, filename: String): Bitmap{

        val appPackageName = "${getApplicationContext<Context>().getPackageName()}.test"

        return getApplicationContext<Context>().packageManager
            .getResourcesForApplication(appPackageName).assets
            .open("$folderName/$filename")
            .use { BitmapFactory.decodeStream(it) }
    }

    private fun Bitmap.getRow(pixels: IntArray, column: Int) {
        this.getPixels(pixels, 0, width, 0, column, width, 1)
    }

}

/**
 * Contains parameters of a screenshot
 * folderName: String - name of testing class
 * refName: String - name of a test method
 * testingView: Activity - an activity or a fragment, which is tested
 * quality: Int - quality of a screenshot for saving
 * widthShareFromLeft: Float - percents of new screenshot width from left to right
 *                              (x coordinate of left corner of a new screenshot)
 * heightShareFromBottom: Float - percents of new screenshot height from bottom to top
 *                              (y coordinate of bottom of a new screenshot)
 * widthShareOfResult: Float - percents of new screenshot width from right to left
 *                              (percents of pixels in each row at result screenshot)
 * heightShareOfResult: Float - percents of new screenshot width from top to bottom
 *                              (percents of rows at result screenshot)
 */
data class ScreenshotParameters(
    val folderName: String,
    val refName: String,
    val testingView: Activity,
    val quality: Int = 100,
    val widthShareFromLeft: Float = 0.0f,
    val heightShareFromBottom: Float = 0.0f,
    val widthShareOfResult: Float = 100.0f,
    val heightShareOfResult: Float = 100.0f
)