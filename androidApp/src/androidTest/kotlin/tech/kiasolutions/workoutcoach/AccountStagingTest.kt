package tech.kiasolutions.workoutcoach
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.json.JSONObject
import tech.kiasolutions.workoutcoach.account.*
import tech.kiasolutions.workoutcoach.core.auth.*
import tech.kiasolutions.workoutcoach.core.persistence.*
class AccountStagingTest {
    @Test fun realStagingSignInRefreshSwitchAndRevocationPreserveLocalNamespaces(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=java.io.File(context.filesDir,"auth-fixture.json")
        Assume.assumeTrue("Private native fixture is supplied only for local staging qualification",file.exists())
        val fixture=JSONObject(file.readText());val a=fixture.getJSONObject("A");val b=fixture.getJSONObject("B")
        val store=AndroidSessionStore(context);store.clear();val machine=AuthMachine(store)
        var current:AuthSession?=null
        try {
            val first=AccountApi.login(a.getString("email"),a.getString("password"));current=first
            Assert.assertTrue(machine.accept(machine.beginLogin(),first,System.currentTimeMillis()/1000))
            val oldScope=machine.namespace;val repoA=androidRepository(context,oldScope);repoA.savePreference("native-isolation","A");repoA.close()
            val restored=AuthMachine(AndroidSessionStore(context));val saved=restored.beginRestore()!!
            val fresh=AccountApi.refresh(saved);current=fresh;Assert.assertTrue(restored.accept(restored.generation,fresh,System.currentTimeMillis()/1000))
            AccountApi.logout(fresh);restored.logout();var rejected=false;try{AccountApi.refresh(fresh)}catch(_:Exception){rejected=true};Assert.assertTrue("Revoked refresh must be denied",rejected)
            val second=AccountApi.login(b.getString("email"),b.getString("password"));current=second
            Assert.assertTrue(machine.accept(machine.beginLogin(),second,System.currentTimeMillis()/1000));Assert.assertNotEquals(oldScope,machine.namespace)
            val repoB=androidRepository(context,machine.namespace);Assert.assertNull(repoB.preferences()["native-isolation"]);repoB.close()
            val preserved=androidRepository(context,oldScope);Assert.assertEquals("A",preserved.preferences()["native-isolation"]);preserved.close()
            Assert.assertNull(androidRepository(context,"guest").use{it.preferences()["native-isolation"]})
        }finally{current?.let{try{AccountApi.logout(it)}catch(_:Exception){}};store.clear();file.delete();AccountScope.namespace="guest"}
    }
}
