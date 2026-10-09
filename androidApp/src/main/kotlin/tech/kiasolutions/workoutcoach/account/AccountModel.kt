package tech.kiasolutions.workoutcoach.account

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import tech.kiasolutions.workoutcoach.core.auth.*
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

object AccountScope {var namespace="guest";internal set}
class AndroidSessionStore(context:Context):SecureSessionStore {
    private val prefs=context.getSharedPreferences("private-auth",Context.MODE_PRIVATE)
    private val alias="workout-coach-session-v1"
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun read():AuthSession? {
        val text=prefs.getString("session",null)?:return null
        val sealed=Base64.decode(text,Base64.NO_WRAP);require(sealed.size>28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,sealed.copyOfRange(0,12)))
        cipher.updateAAD(alias.toByteArray());val value=JSONObject(String(cipher.doFinal(sealed.copyOfRange(12,sealed.size)),Charsets.UTF_8))
        return AuthSession(value.getString("id"),value.getString("access"),value.getString("refresh"),value.getLong("expiry"))
    }
    override fun write(session:AuthSession) {
        val value=JSONObject().put("id",session.userId).put("access",session.accessToken).put("refresh",session.refreshToken).put("expiry",session.expiresEpochSeconds).toString().toByteArray()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(alias.toByteArray())
        check(prefs.edit().putString("session",Base64.encodeToString(cipher.iv+cipher.doFinal(value),Base64.NO_WRAP)).commit())
    }
    override fun clear(){check(prefs.edit().remove("session").commit())}
}
object AccountApi {
    val base=tech.kiasolutions.workoutcoach.BuildConfig.WORKOUT_API_BASE_URL
    private fun request(path:String,body:JSONObject?=null,access:String?=null):JSONObject {
        val connection=URL(base+path).openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects=false;connection.connectTimeout=15000;connection.readTimeout=15000
        connection.setRequestProperty("User-Agent","WorkoutCoach/0.1");connection.setRequestProperty("Content-Type","application/json")
        if(access!=null)connection.setRequestProperty("Authorization","Bearer $access")
        try {
            if(body!=null){connection.requestMethod="POST";connection.doOutput=true;connection.outputStream.use{it.write(body.toString().toByteArray())}}
            check(connection.responseCode in 200..299){"Account request unavailable"}
            val bytes=connection.inputStream.use{input->val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096);while(true){val count=input.read(buffer);if(count<0)break;check(output.size()+count<=65536);output.write(buffer,0,count)};output.toByteArray()}
            return if(bytes.isEmpty())JSONObject() else JSONObject(String(bytes,Charsets.UTF_8))
        }finally{connection.disconnect()}
    }
    private fun verified(token:JSONObject):AuthSession {
        val access=token.getString("access_token");val user=request("/auth/v1/user",access=access)
        val id=user.getString("id");check(user.getString("role")=="authenticated" && token.getJSONObject("user").getString("id")==id)
        val lifetime=token.getLong("expires_in");check(lifetime in 1..3600)
        val now=System.currentTimeMillis()/1000;val expiry=token.optLong("expires_at",now+lifetime);check(expiry in (now+1)..(now+3600))
        return AuthSession(id,access,token.getString("refresh_token"),expiry)
    }
    fun login(email:String,password:String)=verified(request("/auth/v1/token?grant_type=password",JSONObject().put("email",email).put("password",password)))
    fun refresh(session:AuthSession)=verified(request("/auth/v1/token?grant_type=refresh_token",JSONObject().put("refresh_token",session.refreshToken)))
    fun logout(session:AuthSession){request("/auth/v1/logout",JSONObject(),session.accessToken)}
}
data class AccountState(val namespace:String="guest",val busy:Boolean=false,val message:String?=null)
class AccountModel(application:Application):AndroidViewModel(application) {
    private val machine=AuthMachine(AndroidSessionStore(application))
    private val mutable=MutableStateFlow(AccountState());val state=mutable.asStateFlow()
    init {
        try {machine.beginRestore()?.let{saved->exchange(machine.generation){AccountApi.refresh(saved)}}}catch(_:Exception){publish("Couldn’t restore the account. Guest workouts are available.")}
        viewModelScope.launch{while(isActive){delay(30000);val now=System.currentTimeMillis()/1000;val session=machine.session(now);if(machine.status==AuthStatus.SIGNED_IN && (session==null || session.expiresEpochSeconds-now<60))refresh()}}
    }
    private fun publish(message:String?=null){AccountScope.namespace=machine.namespace;mutable.value=AccountState(machine.namespace,machine.status in listOf(AuthStatus.CHECKING,AuthStatus.SIGNING_IN),message)}
    private fun exchange(attempt:Long,action:()->AuthSession){publish();viewModelScope.launch{
        try{val session=withContext(Dispatchers.IO){action()};val accepted=machine.accept(attempt,session,System.currentTimeMillis()/1000);publish();if(!accepted){try{withContext(Dispatchers.IO){AccountApi.logout(session)}}catch(_:Exception){}}}
        catch(_:Exception){if(attempt==machine.generation){try{machine.fail(attempt)}catch(_:Exception){};publish("Couldn’t sign in. Check your details or try again. Guest workouts are available.")}}
    }}
    fun login(email:String,password:String){if(email.isBlank()||password.isBlank()){publish("Enter your email and password.");return};try{val attempt=machine.beginLogin();exchange(attempt){AccountApi.login(email.trim(),password)}}catch(_:Exception){publish("Secure account storage is unavailable. Guest workouts are available.")}}
    fun refresh(){try{machine.beginRefresh()?.let{saved->exchange(machine.generation){AccountApi.refresh(saved)}}}catch(_:Exception){publish("Couldn’t refresh the account. Guest workouts are available.")}}
    fun logout(){try{val previous=machine.logout();publish();if(previous!=null)viewModelScope.launch{try{withContext(Dispatchers.IO){AccountApi.logout(previous)}}catch(_:Exception){publish("Signed out on this phone. Server sign-out could not be confirmed.")}}}catch(_:Exception){publish("Couldn’t clear secure storage. Try again.")}}
}
