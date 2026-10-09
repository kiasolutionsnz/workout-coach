package tech.kiasolutions.workoutcoach.core.contracts

/** Strict deterministic replay input, never a camera/video upload format.
 * TSV: sequence, capture-ms, width, height, rotation, mirrored, semicolon joint rows.
 * Joint row: NAME,x,y,confidence. Empty final column represents no detected joints. */
object PoseFixtureCodec {
    fun decode(line: String): PoseFrame {
        require(line.length <= 10_000) { "Oversized replay frame" }
        val columns = line.split('\t')
        require(columns.size == 7) { "Invalid replay columns" }
        val mirrored = when (columns[5]) { "true" -> true; "false" -> false; else -> error("Invalid mirror flag") }
        val transform = ImageTransform(columns[2].toInt(), columns[3].toInt(), Rotation.valueOf(columns[4]), mirrored)
        val joints = mutableMapOf<Joint, Landmark>()
        if (columns[6].isNotEmpty()) for (row in columns[6].split(';')) {
            val values = row.split(',')
            require(values.size == 4) { "Invalid joint row" }
            val joint = Joint.valueOf(values[0])
            require(joint !in joints) { "Duplicate joint" }
            joints[joint] = Landmark(Point2(values[1].toDouble(), values[2].toDouble()), values[3].toDouble())
        }
        return PoseFrame(columns[0].toLong(), columns[1].toLong(), transform, joints)
    }
}
