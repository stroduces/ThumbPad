package app.thumbpad
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

@Suppress("DEPRECATION")
class Discovery(context:Context,private val found:(String,String,Int)->Unit) {
    private val nsd=context.getSystemService(NsdManager::class.java)
    private var running=false
    private val listener=object:NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type:String){}
        override fun onDiscoveryStopped(type:String){}
        override fun onStartDiscoveryFailed(type:String,error:Int){running=false}
        override fun onStopDiscoveryFailed(type:String,error:Int){running=false}
        override fun onServiceLost(info:NsdServiceInfo){}
        override fun onServiceFound(info:NsdServiceInfo){nsd.resolveService(info,object:NsdManager.ResolveListener {
            override fun onResolveFailed(info:NsdServiceInfo,error:Int){}
            override fun onServiceResolved(info:NsdServiceInfo){info.host?.hostAddress?.let{found(info.serviceName,it,info.port)}}
        })}
    }
    fun start(){if(!running){running=true;runCatching{nsd.discoverServices("_thumbpad._tcp.",NsdManager.PROTOCOL_DNS_SD,listener)}.onFailure{running=false}}}
    fun stop(){if(running){runCatching{nsd.stopServiceDiscovery(listener)};running=false}}
}
