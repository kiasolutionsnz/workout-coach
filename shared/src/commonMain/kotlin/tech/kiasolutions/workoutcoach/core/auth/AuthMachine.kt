package tech.kiasolutions.workoutcoach.core.auth

/** Secret fields are never included in diagnostics. Native adapters verify the
 * identity through /auth/v1/user before constructing an accepted session. */
class AuthSession(val userId:String,val accessToken:String,val refreshToken:String,val expiresEpochSeconds:Long) {
    init {
        require(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(userId))
        require(accessToken.isNotBlank() && refreshToken.isNotBlank() && expiresEpochSeconds>0)
    }
    override fun toString()="AuthSession([redacted])"
}
interface SecureSessionStore {
    @Throws(Exception::class)
    fun read():AuthSession?
    @Throws(Exception::class)
    fun write(session:AuthSession)
    @Throws(Exception::class)
    fun clear()
}
enum class AuthStatus { GUEST, CHECKING, SIGNING_IN, SIGNED_IN, UNAVAILABLE }
/** All calls serialized by native main/UI actor. Requests run off that actor;
 * generation prevents cancelled and previous-account completions being applied. */
class AuthMachine(private val store:SecureSessionStore) {
    var status=AuthStatus.GUEST;private set
    var generation=0L;private set
    var namespace="guest";private set
    private var accepted:AuthSession?=null
    private var revocable:AuthSession?=null
    private var expectedUser:String?=null
    @Throws(Exception::class)
    fun beginLogin():Long { invalidate();status=AuthStatus.SIGNING_IN;return generation }
    @Throws(Exception::class)
    fun beginRestore():AuthSession? {
        invalidateMemory();val saved=try{store.read()}catch(_:Exception){null}
        if(saved==null){store.clear();status=AuthStatus.GUEST;return null}
        revocable=saved;expectedUser=saved.userId;status=AuthStatus.CHECKING;return saved
    }
    @Throws(Exception::class)
    fun beginRefresh():AuthSession? {
        val previous=accepted ?: return null
        val previousNamespace=namespace
        invalidateMemory();revocable=previous;namespace=previousNamespace;expectedUser=previous.userId;status=AuthStatus.CHECKING;return previous
    }
    @Throws(Exception::class)
    fun accept(attempt:Long,session:AuthSession,nowEpochSeconds:Long):Boolean {
        if(attempt!=generation || status !in listOf(AuthStatus.CHECKING,AuthStatus.SIGNING_IN))return false
        if(session.expiresEpochSeconds<=nowEpochSeconds || (expectedUser!=null && expectedUser!=session.userId)) {fail(attempt);return false}
        try {store.write(session)}catch(_:Exception){fail(attempt);return false}
        accepted=session;revocable=session;namespace="account:${session.userId.lowercase()}";status=AuthStatus.SIGNED_IN;expectedUser=null;return true
    }
    @Throws(Exception::class)
    fun fail(attempt:Long) {if(attempt==generation){invalidate();status=AuthStatus.UNAVAILABLE}}
    @Throws(Exception::class)
    fun logout():AuthSession? {val previous=revocable;invalidate();return previous}
    fun session(nowEpochSeconds:Long):AuthSession? = accepted?.takeIf{it.expiresEpochSeconds>nowEpochSeconds && status==AuthStatus.SIGNED_IN}
    private fun invalidateMemory(){generation++;accepted=null;revocable=null;expectedUser=null;namespace="guest";status=AuthStatus.GUEST}
    private fun invalidate(){invalidateMemory();store.clear()}
}
