package app.thumbpad
import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

class RemoteModel(app:Application):AndroidViewModel(app) {
    val storage=Storage(app)
    var computers by mutableStateOf(runCatching{storage.computers()}.getOrDefault(emptyList()));private set
    var selected by mutableStateOf<Computer?>(null);private set
    var status by mutableStateOf("Your desk. A little closer.");private set
    var connected by mutableStateOf(false);private set
    var demo by mutableStateOf(false);private set
    var drag by mutableStateOf(false);private set
    var precise by mutableStateOf(false)
    var rtt by mutableLongStateOf(0);private set
    var events by mutableIntStateOf(0);private set
    var demoX by mutableFloatStateOf(0f);private set
    var demoY by mutableFloatStateOf(0f);private set
    var lastEvent by mutableStateOf("Swipe to try your trackpad");private set
    var pairingCode by mutableStateOf("");private set
    var sensitivity by mutableFloatStateOf(storage.prefs.getFloat("sensitivity",1.4f))
    var acceleration by mutableFloatStateOf(storage.prefs.getFloat("acceleration",0.25f))
    var scrollSpeed by mutableFloatStateOf(storage.prefs.getFloat("scroll",1f))
    var natural by mutableStateOf(storage.prefs.getBoolean("natural",true))
    var haptics by mutableStateOf(storage.prefs.getBoolean("haptics",true))
    var tap by mutableStateOf(storage.prefs.getBoolean("tap",true))
    var leftHanded by mutableStateOf(storage.prefs.getBoolean("left",false))
    var awake by mutableStateOf(storage.prefs.getBoolean("awake",true))
    var palm by mutableStateOf(storage.prefs.getBoolean("palm",true))
    var batteryMode by mutableStateOf(storage.prefs.getBoolean("battery",false))
    var horizontal by mutableStateOf(storage.prefs.getBoolean("horizontal",false))
    var extraControls by mutableStateOf(storage.prefs.getBoolean("extras",false))
    var discovered=mutableStateListOf<String>();private set
    private var foreground=false
    private var resumeWanted=false
    private val transport=Transport(viewModelScope,storage,{text,up->status=text;connected=up;if(!up)drag=false},{c->
        selected=c;computers=(computers.filterNot{it.fp==c.fp}+c);storage.save(computers);pairingCode=""
    },{rtt=it})
    private val discovery=Discovery(app){name,host,port->
        val value="$name · $host:$port";android.os.Handler(android.os.Looper.getMainLooper()).post{if(!discovered.contains(value))discovered.add(value)}
        // Discovery never establishes trust; only refresh an already pinned host after DHCP change.
    }
    private val connectivity=app.getSystemService(ConnectivityManager::class.java)
    private val callback=object:ConnectivityManager.NetworkCallback(){
        override fun onLost(network:Network){android.os.Handler(android.os.Looper.getMainLooper()).post{transport.disconnect();connected=false;drag=false}}
        override fun onAvailable(network:Network){android.os.Handler(android.os.Looper.getMainLooper()).post{if(foreground&&resumeWanted)selected?.takeIf{it.token.isNotEmpty()}?.let{connect(it)}}}
    }
    init {runCatching{connectivity.registerDefaultNetworkCallback(callback)}}
    fun startDiscovery(){foreground=true;discovery.start();if(resumeWanted&&!connected&&!demo)selected?.takeIf{it.token.isNotEmpty()}?.let{connect(it)}}
    fun stopDiscovery()=discovery.stop()
    fun saveSettings(){storage.prefs.edit().putFloat("sensitivity",sensitivity).putFloat("acceleration",acceleration).putFloat("scroll",scrollSpeed).putBoolean("natural",natural).putBoolean("haptics",haptics).putBoolean("tap",tap).putBoolean("left",leftHanded).putBoolean("awake",awake).putBoolean("palm",palm).putBoolean("battery",batteryMode).putBoolean("horizontal",horizontal).putBoolean("extras",extraControls).apply()}
    fun connect(c:Computer){resumeWanted=true;demo=false;drag=false;selected=c;transport.connect(c)}
    fun reconnect(){selected?.let{connect(it)}}
    fun pair(uri:String){runCatching{
        val u=Uri.parse(uri.trim());require(u.scheme=="thumbpad"&&u.host=="pair"){"Use the invitation shown on your desktop"}
        val host=requireNotNull(u.getQueryParameter("host"));val port=u.getQueryParameter("port")?.toInt()?:47321
        require(port in 1024..65535&&host.matches(Regex("[A-Za-z0-9.:-]{1,253}"))) {"Invalid LAN address"}
        val fp=requireNotNull(u.getQueryParameter("fp"));require(fp.matches(Regex("[a-fA-F0-9]{64}")))
        val secret=requireNotNull(u.getQueryParameter("secret"));require(secret.matches(Regex("[a-fA-F0-9]{64}")))
        val c=Computer(host,port,fp,"",host);resumeWanted=true;demo=false;drag=false;selected=c;pairingCode=secret.take(6).uppercase();transport.connect(c,secret)
    }.onFailure{status=it.message?:"Invalid invitation"}}
    fun forget(c:Computer){if(selected?.fp==c.fp)disconnect();computers=computers.filterNot{it.fp==c.fp};storage.save(computers)}
    fun disconnect(){resumeWanted=false;transport.disconnect();connected=false;drag=false;pairingCode=""}
    fun safePause(){foreground=false;emit(6);transport.disconnect();connected=false;drag=false;stopDiscovery()}
    fun demo(){disconnect();demo=true;status="Demo · stays on this phone";events=0;demoX=0f;demoY=0f}
    fun emit(kind:Int,a:Float=0f,b:Float=0f){if(demo){events++;lastEvent=when(kind){1->{demoX+=a;demoY+=b;"Cursor ${demoX.toInt()}, ${demoY.toInt()}"};2->"${if(a==0f)"Left" else "Right"} ${if(b==1f)"down" else "up"}";3->"Scroll ${a.toInt()}, ${b.toInt()}";4->"Shortcut ${a.toInt()}";else->"Released"}}else transport.send(kind,a,b)}
    fun click(button:Int){if(button==0&&drag){lock();return};emit(2,button.toFloat(),1f);emit(2,button.toFloat(),0f)}
    fun lock(){if(!connected&&!demo)return;drag=!drag;emit(2,0f,if(drag)1f else 0f)}
    fun move(dx:Float,dy:Float,dt:Float){val (x,y)=pointerDelta(dx,dy,dt,sensitivity,acceleration,precise);emit(1,x,y)}
    override fun onCleared(){disconnect();stopDiscovery();runCatching{connectivity.unregisterNetworkCallback(callback)}}
}
