package tech.kiasolutions.workoutcoach.core.auth
import kotlin.test.*
class AuthTest {
    private val a="11111111-1111-4111-8111-111111111111"
    private val b="22222222-2222-4222-8222-222222222222"
    private class Store:SecureSessionStore {var saved:AuthSession?=null;var reject=false;override fun read()=saved;override fun write(session:AuthSession){if(reject)error("Unavailable");saved=session};override fun clear(){saved=null}}
    private fun token(id:String)=AuthSession(id,"synthetic-access","synthetic-refresh",500)
    @Test fun cancelledAndPreviousAccountResponsesCannotRestoreIdentity(){
        val store=Store();val m=AuthMachine(store);val first=m.beginLogin();m.logout()
        assertFalse(m.accept(first,token(a),100));assertNull(store.saved);assertEquals("guest",m.namespace)
        val second=m.beginLogin();assertTrue(m.accept(second,token(b),100));assertEquals("account:$b",m.namespace)
        assertFalse(m.accept(first,token(a),100));assertEquals(b,store.saved!!.userId)
    }
    @Test fun restoredIdentityMustBeServerVerifiedAndRefreshCannotSwitchOwner(){
        val store=Store();store.saved=token(a);val m=AuthMachine(store);assertEquals(a,m.beginRestore()!!.userId);assertEquals("guest",m.namespace)
        assertTrue(m.accept(m.generation,token(a),100));m.beginRefresh();assertEquals("account:$a",m.namespace);assertNull(m.session(100))
        assertFalse(m.accept(m.generation,token(b),100));assertNull(store.saved)
    }
    @Test fun logoutDuringRefreshReturnsSessionForServerRevocation(){val m=AuthMachine(Store());assertTrue(m.accept(m.beginLogin(),token(a),100));m.beginRefresh();assertEquals(a,m.logout()!!.userId);assertEquals("guest",m.namespace)}
    @Test fun expiryAndSecureStorageFailureFailClosedWithoutBlockingGuest(){
        val store=Store();val m=AuthMachine(store);assertFalse(m.accept(m.beginLogin(),token(a),501));assertEquals("guest",m.namespace)
        store.reject=true;assertFalse(m.accept(m.beginLogin(),token(a),100));assertEquals("guest",m.namespace);assertNull(m.session(100))
        assertFalse(token(a).toString().contains("synthetic"))
    }
}
