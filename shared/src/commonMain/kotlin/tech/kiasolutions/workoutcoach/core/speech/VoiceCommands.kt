package tech.kiasolutions.workoutcoach.core.speech

enum class VoiceAction { START, PAUSE, RESUME, SKIP_REST, END_SET, REQUEST_END, CONFIRM_END, CANCEL_END, ROUTINES, HISTORY, SETTINGS, BACK }

class VoiceEndConfirmation {
    private var requestedAt:Long?=null
    fun request(nowMillis:Long){requestedAt=nowMillis}
    fun cancel(){requestedAt=null}
    fun confirm(nowMillis:Long):Boolean {
        val requested=requestedAt;requestedAt=null
        return requested!=null && nowMillis>=requested && nowMillis-requested<=8000
    }
}

/** Only exact, final, prefixed commands. Never retain transcripts or accept partial results. */
fun parseVoiceCommand(text:String):VoiceAction? {
    if(text.length>100)return null
    val phrase=text.lowercase().replace(Regex("[^a-z ]")," ").trim().replace(Regex(" +")," ")
    if(!phrase.startsWith("coach "))return null
    return when(phrase.removePrefix("coach ")) {
        "start", "start workout"->VoiceAction.START
        "pause", "stop reps"->VoiceAction.PAUSE
        "resume"->VoiceAction.RESUME
        "skip rest"->VoiceAction.SKIP_REST
        "end set"->VoiceAction.END_SET
        "end workout"->VoiceAction.REQUEST_END
        "confirm end"->VoiceAction.CONFIRM_END
        "cancel", "keep going"->VoiceAction.CANCEL_END
        "routines", "open routines"->VoiceAction.ROUTINES
        "history", "open history"->VoiceAction.HISTORY
        "settings", "open settings"->VoiceAction.SETTINGS
        "back"->VoiceAction.BACK
        else->null
    }
}
