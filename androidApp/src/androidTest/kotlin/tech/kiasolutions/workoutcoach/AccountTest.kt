package tech.kiasolutions.workoutcoach
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith
import tech.kiasolutions.workoutcoach.account.*
import tech.kiasolutions.workoutcoach.core.auth.*
@RunWith(AndroidJUnit4::class)
class AccountTest {
    @Test fun nativeKeystoreEncryptsRoundTripAndCorruptionFailsClosed(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=AndroidSessionStore(context);store.clear()
        try {
            val session=AuthSession("11111111-1111-4111-8111-111111111111","synthetic-access-secret","synthetic-refresh-secret",500)
            store.write(session)
            val prefs=context.getSharedPreferences("private-auth",0)
            val raw=prefs.getString("session","")!!;Assert.assertFalse(raw.contains(session.accessToken));Assert.assertFalse(raw.contains(session.refreshToken))
            Assert.assertEquals(session.userId,AndroidSessionStore(context).read()!!.userId)
            prefs.edit().putString("session",raw.dropLast(5)+"AAAAA").commit()
            val machine=AuthMachine(AndroidSessionStore(context));Assert.assertNull(machine.beginRestore());Assert.assertEquals("guest",machine.storageNamespace);Assert.assertFalse(prefs.contains("session"))
        }finally{store.clear()}
    }
}
