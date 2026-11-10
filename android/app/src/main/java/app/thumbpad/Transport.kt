package app.thumbpad

import android.os.SystemClock
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

class Transport(private val scope:CoroutineScope,private val storage:Storage,private val status:(String,Boolean)->Unit,private val paired:(Computer)->Unit,private val rtt:(Long)->Unit,private val keyRemapping:(Boolean)->Unit) {
    private val queue=EventQueue()
    private val socketLock=Any()
    private var generation=0L
    @Volatile private var socket:Socket?=null
    @Volatile var connected=false;private set
    private var job:Job?=null
    @Volatile private var reconnect=true
    @Volatile private var lastAck=0L
    private fun now()=SystemClock.elapsedRealtimeNanos()/1000
    fun send(kind:Int,a:Float=0f,b:Float=0f){if(!connected)return;if(!queue.offer(EventQueue.Event(kind,a,b,now()))){disconnect();status("Input queue full · reconnect",false)}}
    fun disconnect(){synchronized(socketLock){generation++;reconnect=false;connected=false;queue.clear();job?.cancel();runCatching{socket?.close()};socket=null};status("Disconnected",false)}
    private fun current(expected:Long)=synchronized(socketLock){generation==expected}
    private fun own(value:Socket,expected:Long){synchronized(socketLock){if(generation!=expected){value.close();throw CancellationException("Computer switched")};socket=value}}
    fun connect(computer:Computer,secret:String?=null){disconnect();reconnect=true;val expected=synchronized(socketLock){generation};job=scope.launch(Dispatchers.IO){var target=computer;var invitation=secret;var delayMs=500L
        while(isActive&&reconnect&&current(expected)){try{
            queue.clear();withContext(Dispatchers.Main){status(if(invitation!=null)"Check the code on your desktop · approve phone" else "Connecting to ${target.name}…",false)}
            session(target,invitation,expected){ready->target=ready;invitation=null}
            delayMs=500
        }catch(e:Exception){if(!isActive||!current(expected))break
            connected=false;queue.clear()
            withContext(Dispatchers.Main){status("${e.message?.take(100)?:"Connection lost"} · retrying",false)}
            if(invitation!=null)break
            delay(delayMs);delayMs=(delayMs*2).coerceAtMost(8000)
        }}
    }}
    private suspend fun session(c:Computer,secret:String?,expected:Long,ready:(Computer)->Unit):Computer?=coroutineScope {
        require(c.fp.matches(Regex("[0-9a-fA-F]{64}"))) {"Invalid certificate fingerprint"}
        // This is a strict certificate-pin trust manager: every connection checks the exact DER SHA-256,
        // validity dates and TLS proof of private-key possession. No trust-all fallback exists.
        val trust=object:X509TrustManager {
            override fun getAcceptedIssuers():Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String){throw java.security.cert.CertificateException("client not supported")}
            override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String){
                require(chain.isNotEmpty());chain[0].checkValidity()
                val expected=c.fp.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
                if(!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(chain[0].encoded),expected))throw java.security.cert.CertificateException("Certificate fingerprint changed · pair again")
            }
        }
        val context=SSLContext.getInstance("TLS").apply{init(null,arrayOf(trust),SecureRandom())}
        val raw=Socket();own(raw,expected)
        try {
        raw.connect(InetSocketAddress(c.host,c.port),4000);raw.tcpNoDelay=true
        ensureActive()
        val s=context.socketFactory.createSocket(raw,"thumbpad.local",c.port,true) as SSLSocket;own(s,expected)
        try {
            s.enabledProtocols=s.supportedProtocols.filter{it=="TLSv1.3"||it=="TLSv1.2"}.toTypedArray();s.soTimeout=5000;s.startHandshake()
            val input=DataInputStream(s.inputStream);val output=DataOutputStream(s.outputStream)
            val hello=JSONObject().put("version",1).put("id",storage.id).put("name",android.os.Build.MODEL.take(80))
            if(secret!=null)hello.put("secret",secret)else hello.put("token",c.token)
            val b=hello.toString().toByteArray();output.writeByte(b.size and 255);output.writeByte(b.size ushr 8);output.write(b);output.flush()
            s.soTimeout=65000;val n=input.readUnsignedByte() or (input.readUnsignedByte() shl 8);require(n in 1..2048)
            val reply=ByteArray(n);input.readFully(reply);val response=JSONObject(String(reply));require(response.getBoolean("ok"))
            val next=if(response.has("token")&&!response.isNull("token"))c.copy(token=response.getString("token"))else c
            ready(next)
            withContext(Dispatchers.Main){
                ensureActive()
                if (!current(expected)) throw CancellationException("Computer switched")
                keyRemapping(response.optBoolean("key_remapping"))
                paired(next)
                status("Connected · ${c.name}",true)
            }
            synchronized(socketLock){if(generation!=expected)throw CancellationException("Computer switched");connected=true};lastAck=now();s.soTimeout=4000
            val reader=launch(Dispatchers.IO){val ack=ByteArray(32);while(isActive){input.readFully(ack);require(ack[0]=='T'.code.toByte()&&ack[3]==5.toByte());lastAck=now();val stamp=java.nio.ByteBuffer.wrap(ack).order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong(12);withContext(Dispatchers.Main){rtt((now()-stamp)/1000)}}}
            try {var seq=0L;var beat=0L
                while(isActive&&reconnect){val time=now();if(time-lastAck>4_000_000)error("Heartbeat lost")
                    val e=queue.poll(time)
                    if(e!=null){output.write(Packet(e.kind,++seq,e.created,e.a,e.b).encode());output.flush()}
                    else if(time-beat>=1_000_000){output.write(Packet(5,++seq,time).encode());output.flush();beat=time}
                    else delay(2)
                }
            } finally {reader.cancel();runCatching{output.write(Packet(6,Long.MAX_VALUE,now()).encode());output.flush()};s.close()}
            next
        }finally{s.close();synchronized(socketLock){if(socket===s){connected=false;socket=null}}}
        }finally{raw.close();synchronized(socketLock){if(socket===raw){connected=false;socket=null}}}
    }
}
