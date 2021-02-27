package chriscoomber.manydice

/**
 * Construct a new fair dice from its number of faces, numbered from 1 to [faces].
 */
fun fairDice(faces: Int, name: String? = null) = PhysicalRandomVariable(
    PrimitiveFiniteSampleSpace(faces, { _ -> 1f/faces }),
    name ?: "d$faces"
) { outcome -> outcome }

/**
 * Construct a new fair dice with faces labelled differently. Pass in a map from face values to the
 * number of faces with that value.
 */
fun <T> fairDice(facesMap: Map<T, Int>, name: String? = null): PhysicalRandomVariable<T> {
    val outcomeToFace = mutableListOf<T>()
    for ((faceValue, occurances) in facesMap) {
        repeat(occurances) {
            outcomeToFace.add(faceValue)
        }
    }

    return PhysicalRandomVariable(
        PrimitiveFiniteSampleSpace(facesMap.values.sum(), { _ -> 1f / facesMap.values.sum() }),
        name ?: "d${facesMap.values.sum()} (modified faces)"
    ) { outcome -> outcomeToFace[outcome-1] }
}
