package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*
import kotlin.math.*

class ShoulderPressAnalyzer(private val config:CycleConfig=CycleConfig(readyAngle=85.0,departureAngle=60.0,depthAngle=20.0)):ExerciseAnalyzer {
    private val cycles=mapOf(Side.LEFT to AngleCycle(config),Side.RIGHT to AngleCycle(config))
    private val baseline=mutableMapOf<Side,Double>();private val travel=mutableMapOf<Side,Double>()
    private val pending=mutableMapOf<Side,Pair<Long,RepResult>>()
    override fun reset(){cycles.values.forEach{it.reset()};baseline.clear();travel.clear();pending.clear()}
    override fun analyze(pose:TrackedPose):Analysis {
        if(pose.cycleReset)reset()
        val sides=listOf(Side.LEFT,Side.RIGHT)
        if(sides.any { val b=bodySide(it); !pose.has(b.shoulder,b.elbow,b.wrist,b.hip) }) {reset();return Analysis(MovementPhase.UNKNOWN,false)}
        pending.entries.removeAll{pose.atMillis-it.value.first>1500}
        var reliable=true
        for(side in sides){
            val b=bodySide(side);val cycle=cycles.getValue(side)
            val s=pose.points.getValue(b.shoulder);val e=pose.points.getValue(b.elbow);val w=pose.points.getValue(b.wrist);val h=pose.points.getValue(b.hip)
            val torso=distance(s,h);val elbow=angleDegrees(s,e,w);val measurement=elbow?.let{180-it}
            val overhead=elbow!=null && elbow>=160 && w.y<s.y-torso*.3
            val readyPosture=measurement==null || measurement<config.readyAngle || abs(w.y-s.y)<=torso*.2
            val valid=s.y<h.y && torso>.08 && abs(s.x-h.x)<torso*.4 && w.y<=s.y+.05 && readyPosture && (measurement==null || measurement>config.depthAngle || overhead)
            if(!valid){reliable=false;baseline.remove(side);travel.remove(side);pending.remove(side)}
            if(cycle.phase==MovementPhase.READY && measurement!=null && measurement>=config.readyAngle){baseline[side]=w.y;travel[side]=0.0}
            baseline[side]?.let{travel[side]=max(travel[side]?:0.0,it-w.y)}
            val rep=cycle.update(pose.atMillis,measurement,valid,side)
            if(rep!=null){pending[side]=pose.atMillis to rep.copy(full=rep.full && (travel[side]?:0.0)>=torso*.2);baseline[side]=w.y;travel[side]=0.0}
        }
        if(!reliable){reset();return Analysis(MovementPhase.UNKNOWN,false)}
        val reps=if(pending.size==2){val l=pending.getValue(Side.LEFT).second;val r=pending.getValue(Side.RIGHT).second;pending.clear();listOf(RepResult(l.full && r.full,Side.BOTH,max(l.durationMillis,r.durationMillis)))}else emptyList()
        return Analysis(cycles.getValue(Side.LEFT).phase,true,reps)
    }
}
