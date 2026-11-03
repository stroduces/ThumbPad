package app.thumbpad

import android.os.Bundle
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity:ComponentActivity() {
    private val model:RemoteModel by viewModels()
    private val scan=registerForActivityResult(ScanContract()){result->result.contents?.let{model.pair(it)}}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();setContent{ThumbPad(model){scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan ThumbPad’s desktop invitation").setBeepEnabled(false).setOrientationLocked(false))}}}
    override fun onStart(){super.onStart();model.startDiscovery()}
    override fun onStop(){model.safePause();super.onStop()}
}
private val Mint=Color(0xFFB8E6CC)
@Composable fun ThumbPad(m:RemoteModel,scan:()->Unit){
    val dark=isSystemInDarkTheme()
    val scheme=if(dark)darkColorScheme(primary=Mint,onPrimary=Color(0xFF173224),background=Color(0xFF101713),surface=Color(0xFF1B261F),surfaceVariant=Color(0xFF25372B),onSurface=Color(0xFFE7F0E9),onSurfaceVariant=Color(0xFFA3B6A8))else lightColorScheme(primary=Color(0xFF245F3D),onPrimary=Color.White,background=Color(0xFFF2F6F0),surface=Color.White,surfaceVariant=Color(0xFFE2EDE3),onSurface=Color(0xFF14261A),onSurfaceVariant=Color(0xFF526A58))
    MaterialTheme(colorScheme=scheme){Surface(Modifier.fillMaxSize(),color=scheme.background){RemoteScreen(m,scan)}}
}
@Composable private fun RemoteScreen(m:RemoteModel,scan:()->Unit){
    var devices by rememberSaveableState(false)
    var settings by rememberSaveableState(false)
    val context=androidx.compose.ui.platform.LocalContext.current
    val haptic=LocalHapticFeedback.current
    val activity=context as? ComponentActivity
    DisposableEffect(m.connected,m.awake){if(m.connected&&m.awake)activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);onDispose{activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}
    fun buzz(){if(m.haptics)haptic.performHapticFeedback(HapticFeedbackType.LongPress)}
    val battery=remember{context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let{val level=it.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);val scale=it.getIntExtra(BatteryManager.EXTRA_SCALE,100);if(level>=0)"${level*100/scale}%" else ""}?:""}
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("↗",fontSize=30.sp,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(end=9.dp))
            Text("ThumbPad",fontSize=23.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-1).sp)
            Spacer(Modifier.weight(1f));Text(battery,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={settings=true},modifier=Modifier.semantics{contentDescription="Open settings"}){Text("⚙",fontSize=23.sp)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Column {Text(if(m.demo)"FIND YOUR FEEL" else "MAKE ROOM TO MOVE",fontSize=10.sp,letterSpacing=2.sp,color=MaterialTheme.colorScheme.primary);Text("Your desk. Your stride.",fontSize=24.sp,letterSpacing=(-.8).sp,modifier=Modifier.padding(top=6.dp))}
        }
        Surface(onClick={devices=true},shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().heightIn(min=76.dp)){
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant){Text(if(m.demo)"◉" else "▣",fontSize=24.sp,modifier=Modifier.padding(9.dp),color=MaterialTheme.colorScheme.primary)}
                Column(Modifier.weight(1f).padding(start=12.dp)){Text(if(m.demo)"Practice space" else m.selected?.name?:"Find your computer",fontSize=15.sp,fontWeight=FontWeight.Medium);Text(if(m.connected)"● Connected · ${m.rtt} ms round trip" else m.status,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)}
                Text("⌄",fontSize=22.sp,color=MaterialTheme.colorScheme.primary)
            }
        }
        if(m.pairingCode.isNotEmpty())Text("Confirm ${m.pairingCode} on your desktop",color=MaterialTheme.colorScheme.primary,fontSize=14.sp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            FilterChip(selected=m.precise,onClick={m.precise=!m.precise;buzz()},label={Text("◎  Precise")},modifier=Modifier.heightIn(min=48.dp).weight(1f))
            FilterChip(selected=m.drag,onClick={m.lock();buzz()},label={Text(if(m.drag)"▣  Drag locked" else "▢  Drag lock")},modifier=Modifier.heightIn(min=48.dp).weight(1f))
        }
        TouchArea(m,Modifier.weight(1f).fillMaxWidth(),onTap={button->m.click(button);buzz()})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Button(onClick={m.click(0);buzz()},enabled=m.connected||m.demo,shape=RoundedCornerShape(20.dp),modifier=Modifier.weight(1.4f).height(68.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("Left click",fontSize=17.sp);if(m.drag)Text("Tap to release",fontSize=10.sp)}}
            OutlinedButton(onClick={m.click(1);buzz()},enabled=m.connected||m.demo,shape=RoundedCornerShape(20.dp),modifier=Modifier.weight(1f).height(68.dp)){Text("Right click",fontSize=15.sp)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
            TextButton(onClick={m.emit(4,0f);buzz()},enabled=m.connected||m.demo,modifier=Modifier.heightIn(min=48.dp)){Text("‹  Back")}
            TextButton(onClick={m.emit(4,2f);buzz()},enabled=m.connected||m.demo,modifier=Modifier.heightIn(min=48.dp)){Text("Ⅱ  Play / pause")}
            TextButton(onClick={m.emit(4,1f);buzz()},enabled=m.connected||m.demo,modifier=Modifier.heightIn(min=48.dp)){Text("Forward  ›")}
        }
        if(m.extraControls)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){for((label,id) in listOf("Vol −" to 4,"Vol +" to 3,"Tab" to 7,"Enter" to 5)){TextButton(onClick={m.emit(4,id.toFloat());buzz()},enabled=m.connected||m.demo){Text(label)}}}
        Text(if(m.demo)"${m.lastEvent} · ${m.events} events" else if(m.drag)"Drag is locked · swipe, then tap Left click to release" else "Swipe to move · tap to click · slide the edge to scroll",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.align(Alignment.CenterHorizontally))
    }
    if(devices)Devices(m,onDismiss={devices=false},scan={devices=false;scan()})
    if(settings)Settings(m){m.saveSettings();settings=false}
}
@Composable private fun rememberSaveableState(initial:Boolean)=androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(initial)}

@OptIn(ExperimentalComposeUiApi::class)
@Composable private fun TouchArea(m:RemoteModel,modifier:Modifier,onTap:(Int)->Unit){
    val density=LocalDensity.current.density
    val classifier=remember{GestureClassifier()}
    var lastX by remember{mutableFloatStateOf(0f)};var lastY by remember{mutableFloatStateOf(0f)}
    var lastT by remember{mutableLongStateOf(0)};var fingers by remember{mutableIntStateOf(1)}
    var touching by remember{mutableStateOf(false)};var scroll by remember{mutableStateOf(false)}
    var rejected by remember{mutableStateOf(false)}
    val shape=RoundedCornerShape(30.dp)
    BoxWithConstraints(modifier.clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp,if(m.drag)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,shape)){
        val widthPx=constraints.maxWidth.toFloat()
        Canvas(Modifier.fillMaxSize()){
            val ink=if(m.drag)Color(0xFFB8E6CC)else Color(0xFF6C8E78)
            val spacing=22.dp.toPx();var x=spacing
            while(x<size.width){var y=spacing;while(y<size.height){drawCircle(ink.copy(alpha=.17f),1.dp.toPx(),androidx.compose.ui.geometry.Offset(x,y));y+=spacing};x+=spacing}
            val edge=if(m.leftHanded)27.dp.toPx()else size.width-27.dp.toPx()
            drawLine(ink.copy(alpha=.5f),androidx.compose.ui.geometry.Offset(edge,size.height*.3f),androidx.compose.ui.geometry.Offset(edge,size.height*.7f),4.dp.toPx(),StrokeCap.Round)
            if(touching)drawCircle(ink.copy(alpha=.15f),35.dp.toPx(),androidx.compose.ui.geometry.Offset(lastX,lastY))
        }
        Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){Text(if(m.drag)"DRAG LOCKED" else if(m.connected||m.demo)"A LITTLE SPACE TO WANDER" else "READY WHEN YOU ARE",fontSize=10.sp,letterSpacing=2.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(10.dp));Text(if(m.demo)"${m.demoX.toInt()}   /   ${m.demoY.toInt()}" else if(m.connected)"Move at your own pace." else "Pair your computer to begin.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Box(Modifier.fillMaxSize().semantics{contentDescription="Trackpad. Swipe with one finger to move. Tap to click. Slide the ${if(m.leftHanded)"left" else "right"} edge to scroll. Use dedicated click and drag buttons below."}.pointerInteropFilter{e->
            if(!m.connected&&!m.demo)return@pointerInteropFilter false
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{lastX=e.x;lastY=e.y;lastT=e.eventTime;fingers=1;touching=true;rejected=m.palm&&e.getSize(0)>.6f;scroll=if(m.leftHanded)e.x<54*density else e.x>widthPx-54*density;classifier.down(e.eventTime)}
                MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_POINTER_UP->{fingers=maxOf(fingers,e.pointerCount);classifier.move(0f,0f,fingers);val remaining=if(e.actionMasked==MotionEvent.ACTION_POINTER_UP&&e.actionIndex==0)1 else 0;lastX=e.getX(remaining);lastY=e.getY(remaining);lastT=e.eventTime}
                MotionEvent.ACTION_MOVE->{val x=e.x;val y=e.y;val dx=(x-lastX)/density;val dy=(y-lastY)/density
                    classifier.move(dx,dy,e.pointerCount)
                    if(!rejected){if(scroll||e.pointerCount>1){val v=scrollDelta(dy,m.scrollSpeed,m.natural);val h=if(m.horizontal)scrollDelta(dx,m.scrollSpeed,m.natural)else 0f;m.emit(3,h,v)}else m.move(dx,dy,(e.eventTime-lastT).toFloat())}
                    lastX=x;lastY=y;lastT=e.eventTime
                }
                MotionEvent.ACTION_UP->{touching=false;if(!rejected&&!scroll&&m.tap)classifier.tap(e.eventTime)?.let(onTap)}
                MotionEvent.ACTION_CANCEL->{touching=false;m.emit(6);if(m.drag)m.lock()}
            };true
        })
        if(m.scrollWheel){
            val wheelSize=minOf(144.dp,maxWidth*.48f,(maxHeight-20.dp).coerceAtLeast(48.dp))
            ScrollWheel(
                enabled=m.connected||m.demo,
                onScroll=m::wheelScroll,
                modifier=Modifier.align(if(m.leftHanded)Alignment.BottomStart else Alignment.BottomEnd).padding(10.dp).size(wheelSize)
            )
        }
    }
}
@Composable private fun SettingToggle(label:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically){Text(label,modifier=Modifier.weight(1f),fontSize=13.sp);Switch(checked=checked,onCheckedChange=onChange,modifier=Modifier.semantics{contentDescription=label})}}
@Composable private fun Devices(m:RemoteModel,onDismiss:()->Unit,scan:()->Unit){
    var invitation by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Your computers")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Connect on the same Wi-Fi. Approve the matching code on your desktop.")
        Button(onClick=scan,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Scan desktop QR")}
        OutlinedTextField(value=invitation,onValueChange={invitation=it},label={Text("Paste pairing invitation")},modifier=Modifier.fillMaxWidth(),maxLines=3)
        OutlinedButton(onClick={m.pair(invitation);onDismiss()},enabled=invitation.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Pair with invitation")}
        for(c in m.computers){Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={m.connect(c);onDismiss()},modifier=Modifier.weight(1f)){Text("▣  ${c.name}")};TextButton(onClick={m.forget(c)}){Text("Forget")}}}
        if(m.discovered.isNotEmpty()){Text("Nearby · scan the QR on one of these computers",fontSize=12.sp);for(s in m.discovered)Text(s,fontSize=11.sp)}
        if(m.selected!=null)TextButton(onClick={m.reconnect();onDismiss()}){Text("Reconnect selected computer")}
        TextButton(onClick={m.demo();onDismiss()}){Text("Try the trackpad in demo mode")}
        if(m.connected)TextButton(onClick={m.disconnect();onDismiss()}){Text("Disconnect")}
    }},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}
@Composable private fun Settings(m:RemoteModel,onDismiss:()->Unit){
    AlertDialog(onDismissRequest=onDismiss,title={Text("Make it feel like you")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        Text("Pointer feel",fontWeight=FontWeight.SemiBold)
        Row {for((label,speed) in listOf("Precision" to .7f,"Balanced" to 1.4f,"Fast" to 2.4f)){TextButton(onClick={m.sensitivity=speed}){Text(label,fontSize=11.sp)}}}
        Text("Sensitivity · ${"%.1f".format(m.sensitivity)}");Slider(value=m.sensitivity,onValueChange={m.sensitivity=it},valueRange=.3f..3f)
        Text("Acceleration");Slider(value=m.acceleration,onValueChange={m.acceleration=it},valueRange=0f..1f)
        Text("Scroll speed");Slider(value=m.scrollSpeed,onValueChange={m.scrollSpeed=it},valueRange=.2f..3f)
        SettingToggle("Show scroll wheel",m.scrollWheel){m.scrollWheel=it}
        SettingToggle("Natural scrolling",m.natural){m.natural=it}
        SettingToggle("Horizontal scrolling",m.horizontal){m.horizontal=it}
        SettingToggle("Tap to click",m.tap){m.tap=it}
        SettingToggle("Click haptics",m.haptics){m.haptics=it}
        SettingToggle("Left-handed layout",m.leftHanded){m.leftHanded=it}
        SettingToggle("Palm rejection",m.palm){m.palm=it}
        SettingToggle("Keep screen awake while connected",m.awake){m.awake=it}
        SettingToggle("Extra volume / keyboard controls",m.extraControls){m.extraControls=it}
        Text("No account. Encrypted local connection. Camera is used only to scan your desktop’s QR code.",fontSize=11.sp)
    }},confirmButton={TextButton(onClick=onDismiss){Text("Save & close")}})
}
