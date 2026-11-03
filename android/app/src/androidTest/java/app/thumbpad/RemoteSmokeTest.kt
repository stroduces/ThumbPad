package app.thumbpad
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import org.junit.Assume.assumeNotNull
import android.graphics.Bitmap
@RunWith(AndroidJUnit4::class)class RemoteSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name:String){val instrumentation=InstrumentationRegistry.getInstrumentation();val bitmap=instrumentation.uiAutomation.takeScreenshot();val path=java.io.File(compose.activity.getExternalFilesDir(null),name);path.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    @Test fun demoClickAndDragRelease(){compose.onNodeWithText("Find your computer").performClick();compose.onNodeWithText("Try the trackpad in demo mode").performScrollTo().performClick();compose.onNodeWithText("Left click").performClick();compose.onNodeWithText("▢  Drag lock").performClick();compose.onNodeWithText("▣  Drag locked").assertExists();compose.onNodeWithText("Left click").performClick();compose.onNodeWithText("▢  Drag lock").assertExists();screenshot("android-demo.png")}
    @Test fun encryptedReceiverPairClickDragReconnect(){
        val invitation=InstrumentationRegistry.getArguments().getString("pairingUri")
        assumeNotNull("Pass pairingUri for the isolated TLS receiver integration",invitation)
        lateinit var model:RemoteModel
        compose.runOnUiThread{model=ViewModelProvider(compose.activity)[RemoteModel::class.java];model.pair(invitation!!)}
        compose.waitUntil(20_000){model.connected}
        compose.onNodeWithText("Left click").performClick();compose.onNodeWithText("Right click").performClick()
        compose.onNodeWithText("▢  Drag lock").performClick()
        compose.onNodeWithContentDescription("Trackpad.",substring=true).performTouchInput{swipe(center,center+androidx.compose.ui.geometry.Offset(60f,30f),200)}
        compose.onNodeWithText("Left click").performClick()
        compose.onNodeWithText("▢  Drag lock").assertExists();screenshot("android-connected.png")
        compose.runOnUiThread{model.disconnect();model.reconnect()}
        compose.waitUntil(15_000){model.connected}
        compose.runOnUiThread{model.disconnect()}
    }
}
