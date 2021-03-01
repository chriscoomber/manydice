package chriscoomber.manydice

/**
 * Construct a new fair dice from its number of faces, numbered from 1 to [faces].
 */
fun fairDice(faces: Int, name: String? = null): FiniteRandomVariable<Int> =
    PhysicalRandomVariable.fromProbabilityMassFunction(
        (1..faces).associateWith { 1f/faces },
        name
    )

/**
 * Construct a new fair dice with faces labelled differently. Pass in a map from face values to the
 * number of faces with that value.
 */
fun <T> fairDice(facesMap: Map<T, Int>, name: String? = null): FiniteRandomVariable<T> =
    PhysicalRandomVariable.fromProbabilityMassFunction(
        facesMap.mapValues { (_, numFaces) -> numFaces.toFloat() / facesMap.values.sum() },
        name
    )

fun fairDiceSum(quantity: Int, faces: Int, name: String? = null): FiniteRandomVariable<Int> {
    var acc = PhysicalRandomVariable.fromProbabilityMassFunction(mapOf(0 to 1f)) as FiniteRandomVariable<Int>  // Start with a dice that's always 0

    repeat(quantity) {
        acc = (acc + fairDice(faces)).forgetDependencies()
    }

    return if (name != null) acc.setName(name) else acc
}

