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
import org.junit.Assert.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
        compose.onNodeWithText("▢  Drag lock").assertExists()
        compose.runOnUiThread{model.scrollWheel=true}
        compose.onNodeWithContentDescription("Scroll wheel").performTouchInput{
            val radius=width*.36f
            down(center+androidx.compose.ui.geometry.Offset(radius,0f))
            for(step in 1..8){val angle=(step*PI/16).toFloat();moveTo(center+androidx.compose.ui.geometry.Offset(radius*cos(angle),radius*sin(angle)),16)}
            up()
        }
        compose.runOnIdle{assertTrue(model.connected)}
        screenshot("android-connected.png")
        compose.runOnUiThread{model.disconnect();model.reconnect()}
        compose.waitUntil(15_000){model.connected}
        compose.runOnUiThread{model.disconnect()}
    }
    @Test fun scrollWheelCanBeAddedAndOnlyScrolls(){
        lateinit var model:RemoteModel
        compose.runOnUiThread{
            model=ViewModelProvider(compose.activity)[RemoteModel::class.java]
            model.demo();model.scrollWheel=false;model.natural=true;model.saveSettings()
        }
        compose.onNodeWithContentDescription("Scroll wheel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNodeWithContentDescription("Show scroll wheel").performScrollTo().performClick()
        compose.onNodeWithText("Save & close").performClick()
        compose.onNodeWithContentDescription("Scroll wheel").assertExists().performTouchInput{
            val radius=width*.36f
            down(center+androidx.compose.ui.geometry.Offset(radius,0f))
            for(step in 1..8){val angle=(step*PI/16).toFloat();moveTo(center+androidx.compose.ui.geometry.Offset(radius*cos(angle),radius*sin(angle)),16)}
            up()
        }
        var eventsAfterTurn=0
        compose.runOnIdle{
            assertTrue(model.lastEvent.startsWith("Scroll 0, "))
            assertTrue(model.events>0)
            assertEquals(0f,model.demoX);assertEquals(0f,model.demoY)
            assertFalse(model.drag)
            assertTrue(model.storage.prefs.getBoolean("scroll_wheel",false))
            eventsAfterTurn=model.events
        }
        compose.onNodeWithContentDescription("Scroll wheel").performTouchInput{click(center)}
        compose.runOnIdle{assertEquals(eventsAfterTurn,model.events)}
        screenshot("android-scroll-wheel.png")
        compose.runOnUiThread{model.leftHanded=true}
        val wheel=compose.onNodeWithContentDescription("Scroll wheel").fetchSemanticsNode().boundsInRoot
        val trackpad=compose.onNodeWithContentDescription("Trackpad.",substring=true).fetchSemanticsNode().boundsInRoot
        assertTrue(wheel.center.x<trackpad.center.x)
        compose.runOnUiThread{model.scrollWheel=false;model.leftHanded=false;model.saveSettings()}
        compose.onNodeWithContentDescription("Scroll wheel").assertDoesNotExist()
    }
}
