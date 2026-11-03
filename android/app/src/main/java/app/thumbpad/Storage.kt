package app.thumbpad

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Computer(val host:String,val port:Int,val fp:String,val token:String,val name:String) {
    fun json()=JSONObject().put("host",host).put("port",port).put("fp",fp).put("token",token).put("name",name)
    companion object {fun from(j:JSONObject)=Computer(j.getString("host"),j.getInt("port"),j.getString("fp"),j.getString("token"),j.getString("name"))}
}
class Storage(context:Context) {
    val prefs=context.getSharedPreferences("thumbpad",Context.MODE_PRIVATE)
    val id:String=prefs.getString("id",null)?:UUID.randomUUID().toString().replace("-","").also{prefs.edit().putString("id",it).apply()}
    private fun key():SecretKey {val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};return store.getKey("thumbpad",null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("thumbpad",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()}
    fun computers():List<Computer> {
        val raw=prefs.getString("computers",null)?:return emptyList()
        val b=Base64.decode(raw,Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(0,12)))
        val arr=JSONArray(String(c.doFinal(b.copyOfRange(12,b.size))));return (0 until arr.length()).map{Computer.from(arr.getJSONObject(it))}
    }
    fun save(computers:List<Computer>){val arr=JSONArray();computers.forEach{arr.put(it.json())};val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());prefs.edit().putString("computers",Base64.encodeToString(c.iv+c.doFinal(arr.toString().toByteArray()),Base64.NO_WRAP)).apply()}
}
