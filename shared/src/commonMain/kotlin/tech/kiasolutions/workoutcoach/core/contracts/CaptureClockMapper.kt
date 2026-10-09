package tech.kiasolutions.workoutcoach.core.contracts

/** Camera timestamp epochs are platform-specific. Anchor once, preserve capture intervals,
 * and reject late/future/duplicate samples in the workout's monotonic time domain. */
class CaptureClockMapper(private val clock:WorkoutClock,private val expiryMillis:Long=250) {
    init{require(expiryMillis>0)}
    private var firstSensor:Long?=null;private var firstLocal=0L;private var previous:Long?=null
    fun map(sensorMillis:Long):Long? {
        if(sensorMillis<0 || previous?.let{sensorMillis<=it}==true)return null
        val now=clock.nowMillis();require(now>=0)
        if(firstSensor==null){firstSensor=sensorMillis;firstLocal=now}
        val base=firstSensor!!
        if(sensorMillis<base)return null
        val delta=sensorMillis-base
        if(delta>Long.MAX_VALUE-firstLocal)return null
        val mapped=firstLocal+delta
        if(mapped>now || now-mapped>expiryMillis)return null
        previous=sensorMillis
        return mapped
    }
    fun reset(){firstSensor=null;previous=null;firstLocal=0}
}
