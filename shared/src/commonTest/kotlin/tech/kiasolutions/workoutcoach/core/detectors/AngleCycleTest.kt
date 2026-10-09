package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import kotlin.test.*

class AngleCycleTest {
    private fun cycle(f: AngleCycle, depth: Double = 90.0): RepResult? {
        f.update(0,170.0,true,Side.LEFT); f.update(200,170.0,true,Side.LEFT)
        f.update(400,140.0,true,Side.LEFT); f.update(600,depth,true,Side.LEFT)
        f.update(800,170.0,true,Side.LEFT)
        return f.update(1000,170.0,true,Side.LEFT)
    }
    @Test fun completeAndShallowCyclesAreDistinctAndLockoutDoesNotRepeat() {
        val f = AngleCycle(); assertTrue(cycle(f)!!.full)
        repeat(10){ assertNull(f.update(1200+it*100L,170.0,true,Side.LEFT)) }
        assertFalse(cycle(AngleCycle(),120.0)!!.full)
    }
    @Test fun flexedFirstFrameAndNoisyReadyCannotStartFullCycle() {
        val f=AngleCycle()
        f.update(0,90.0,true,Side.LEFT); f.update(200,170.0,true,Side.LEFT)
        assertNull(f.update(300,140.0,true,Side.LEFT)); assertEquals(MovementPhase.UNKNOWN,f.phase)
        f.update(400,170.0,true,Side.LEFT); f.update(500,150.0,true,Side.LEFT)
        f.update(600,170.0,true,Side.LEFT); assertEquals(MovementPhase.UNKNOWN,f.phase)
    }
    @Test fun gapInvalidPostureAndRapidCyclesCannotCount() {
        val f=AngleCycle()
        f.update(0,170.0,true,Side.LEFT); f.update(200,170.0,true,Side.LEFT); f.update(400,90.0,true,Side.LEFT)
        assertNull(f.update(2000,170.0,true,Side.LEFT)); assertEquals(MovementPhase.UNKNOWN,f.phase)
        f.update(2200,170.0,true,Side.LEFT); f.update(2400,90.0,true,Side.LEFT)
        f.update(2500,90.0,false,Side.LEFT); assertNull(f.update(2700,170.0,true,Side.LEFT))
        val fast = AngleCycle(); fast.update(0,170.0,true,Side.LEFT); fast.update(200,170.0,true,Side.LEFT)
        fast.update(250,90.0,true,Side.LEFT); fast.update(300,170.0,true,Side.LEFT)
        assertNull(fast.update(450,170.0,true,Side.LEFT))
    }
    @Test fun invalidConfigurationAndNumbersAreRejected() {
        assertFailsWith<IllegalArgumentException>{CycleConfig(depthAngle=170.0)}
        assertFailsWith<IllegalArgumentException>{CycleConfig(readyAngle=Double.NaN)}
        assertNull(AngleCycle().update(0,Double.NaN,true,Side.LEFT))
    }
    @Test fun switchingSidesCannotFinishOldCycleAndHoldsNeverBridgeGaps() {
        val f=AngleCycle(); f.update(0,170.0,true,Side.LEFT); f.update(200,170.0,true,Side.LEFT); f.update(400,90.0,true,Side.LEFT)
        assertNull(f.update(600,170.0,true,Side.RIGHT)); assertNull(f.update(800,170.0,true,Side.RIGHT))
        val hold = ObservedHold(); hold.update(0,true); assertEquals(200L,hold.update(200,true).validMillis)
        assertEquals(200L,hold.update(30200,true).validMillis)
        assertEquals(0L,hold.update(30300,false).streakMillis)
        assertEquals(200L,hold.update(30400,true).validMillis)
    }
}
