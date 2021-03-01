package chriscoomber.manydice

import com.benasher44.uuid.uuid4

/**
 * A random variable is a function from a particular sample space Omega to some measurable space E
 */
interface FiniteRandomVariable<E>: RandomVariable<FiniteOutcome, E> {
    /**
     * The sample space that this random variable is defined on.
     */
    override val sampleSpace: FiniteSampleSpace

    /**
     * Evaluate the random variable at a particular outcome.
     */
    override fun evaluate(outcome: FiniteOutcome): E

    /**
     * Copy the random variable to a disjoint copy of its sample space, rendering it independent of
     * all other random variables.
     *
     * All of the primitive spaces in the random variable will have their IDs adjusted in the same
     * way depending on [mangler]. This allows you to copy two non-independent RVs in such a way
     * that their dependency remains unchanged. For example, X and Y are two independent dice with
     * disjoint sample spaces A and B, then Z = X + Y is a RV on sample space A x B. X and Z are not
     * independent: if X = 1 then Z <=7 always. If you call `X1 = X.copy(mangler)` and
     * `Z1 = Z.copy(mangler)` then X1 is a RV on a copy of A (let's call it A1). The crucial fact is
     * that Z1 is a RV on A1 x B1; i.e. that A1 is the same A1 that's used in X, so they share
     * outcomes from A1.
     */
    fun copy(mangler: String = uuid4().toString()): FiniteRandomVariable<E>

    fun setName(newName: String): FiniteRandomVariable<E>

    val probabilityMassFunction: Map<E, Float>
        get() {
            // Run through every possible outcome and find out that outcome's value when evaluated,
            // and also the probability of that outcome, and record them all in a nice big map from
            // value to total probability.
            val resultMap = mutableMapOf<E, Float>()
            for (outcome in sampleSpace.space) {
                val value = evaluate(outcome)
                val probability = sampleSpace.measureSingleOutcome(outcome)
                resultMap[value] = (resultMap[value] ?: 0f) + probability
            }
            return resultMap
        }

    fun conditionalProbabilityMassFunction(event: FiniteEvent): Map<E, Float> {
        event.forEach { sampleSpace.requireOutcomeIsAnElementOfThisSpace(it) }

        // Run through every possible outcome RESTRICTED TO THE GIVEN EVENT and find out that
        // outcome's value when evaluated, and also the probability of that outcome SCALED UP BASED
        // ON THE PROBABILITY OF THE GIVEN EVENT, and record them all in a nice big map.
        val probOfEvent = event.map { sampleSpace.measureSingleOutcome(it) }.sum()

        val resultMap = mutableMapOf<E, Float>()
        for (outcome in event) {
            val value = evaluate(outcome)
            val probability = sampleSpace.measureSingleOutcome(outcome) / probOfEvent
            resultMap[value] = (resultMap[value] ?: 0f) + probability
        }
        return resultMap
    }

    fun <E2> conditionalProbabilityMassFunction(other: FiniteRandomVariable<E2>, condition: (E2) -> Boolean): Map<E, Float> {
        // First we massage both this and other so they're using the same probability space
        val thisMassaged = combine(this, other) { v1, _ -> v1 }
        val otherMassaged = combine(this, other) { _, v2 -> v2 }

        // Now we can ask for the PMF of this given other
        return thisMassaged.conditionalProbabilityMassFunction(otherMassaged.toEvent(condition))
    }

    fun toEvent(condition: (E) -> Boolean): FiniteEvent {
        return sampleSpace.space.filter { condition(evaluate(it)) }.toSet()
    }

    /**
     * Simplify the sample space, at the cost of forgetting dependencies. The RV produced by this function will have
     * the same distribution, but will be independent to all other RVs. This can be necessary when combining together
     * more than 5 or so random variables, as otherwise the sample space gets out of hand.
     */
    fun forgetDependencies(): FiniteRandomVariable<E> = PhysicalRandomVariable.fromProbabilityMassFunction(this.probabilityMassFunction)
}

fun <E, R> FiniteRandomVariable<E>.map(name: String? = null, mapping: (E) -> R) = MapFiniteRandomVariable(this, name, mapping)

// TODO more combines
fun <E1, E2, R> combine(x1: FiniteRandomVariable<E1>, x2: FiniteRandomVariable<E2>, name: String? = null, mapping: (E1, E2) -> R) = CombineFiniteRandomVariable(x1, x2, name, mapping)
fun <T1, T2, T3, R> combine(x1: FiniteRandomVariable<T1>, x2: FiniteRandomVariable<T2>, x3: FiniteRandomVariable<T3>, name: String? = null, mapping: (T1, T2, T3) -> R) =
    combine(combine(x1, x2, null, { v1, v2 -> Pair(v1, v2) }), x3, name) {
            (v1, v2), v3 -> mapping(v1, v2, v3)
    }
fun <T1, T2, T3, T4, R> combine(x1: FiniteRandomVariable<T1>, x2: FiniteRandomVariable<T2>, x3: FiniteRandomVariable<T3>, x4: FiniteRandomVariable<T4>, name: String? = null, mapping: (T1, T2, T3, T4) -> R) =
    combine(combine(x1, x2, x3, null, { v1, v2, v3 -> Triple(v1, v2, v3) }), x4, name) {
            (v1, v2, v3), v4 -> mapping(v1, v2, v3, v4)
    }
data class Tuple4<T1, T2, T3, T4>(val x1: T1, val x2: T2, val x3: T3, val x4: T4) {
    override fun toString() = "($x1, $x2, $x3, $x4)"
}
fun <T1, T2, T3, T4, T5, R> combine(x1: FiniteRandomVariable<T1>, x2: FiniteRandomVariable<T2>, x3: FiniteRandomVariable<T3>, x4: FiniteRandomVariable<T4>, x5: FiniteRandomVariable<T5>, name: String? = null, mapping: (T1, T2, T3, T4, T5) -> R) =
    combine(combine(x1, x2, x3, x4, null, { v1, v2, v3, v4 -> Tuple4(v1, v2, v3, v4) }), x5, name) {
            (v1, v2, v3, v4), v5 -> mapping(v1, v2, v3, v4, v5)
    }

class MapFiniteRandomVariable<E, R>(val upstream: FiniteRandomVariable<E>, val name: String? = null, val mapping: (E) -> R) :
    FiniteRandomVariable<R> {
    override val sampleSpace = upstream.sampleSpace
    override fun evaluate(outcome: Map<PrimitiveFiniteSampleSpace, Int>) = mapping(upstream.evaluate(outcome))
    override fun copy(mangler: String) = MapFiniteRandomVariable(upstream.copy(mangler), name?.plus( " (copy)"), mapping)
    override fun toString(): String = name ?: "Map($upstream)"
    override fun setName(newName: String) = MapFiniteRandomVariable(upstream, name, mapping)
}

class CombineFiniteRandomVariable<E1, E2, R>(val upstream1: FiniteRandomVariable<E1>, val upstream2: FiniteRandomVariable<E2>, val name: String? = null, val mapping: (E1, E2) -> R) :
    FiniteRandomVariable<R> {
    override val sampleSpace = upstream1.sampleSpace.combine(upstream2.sampleSpace)
    override fun evaluate(outcome: Map<PrimitiveFiniteSampleSpace, Int>): R {
        // First we need to project the outcome onto the upstream's spaces.
        val projection1 = when (val space = upstream1.sampleSpace) {
            is PrimitiveFiniteSampleSpace -> outcome.filterKeys { it == space }
            is ProductFiniteSampleSpace -> outcome.filterKeys { it in space.primitiveSpaces }
        }
        val projection2 = when (val space = upstream2.sampleSpace) {
            is PrimitiveFiniteSampleSpace -> outcome.filterKeys { it == space }
            is ProductFiniteSampleSpace -> outcome.filterKeys { it in space.primitiveSpaces }
        }

        // Now we simply evaluate the upstream RVs on their sample spaces (which are contained in
        // the combined sample space worked out above). Note that we're correctly handling possibly
        // dependent RVs: if upstream1 and upstream2 share a primitive space in their sample spaces,
        // then they will both use the component of [outcome] which came from that space.
        val upstream1Val = upstream1.evaluate(projection1)
        val upstream2Val = upstream2.evaluate(projection2)

        return mapping(upstream1Val, upstream2Val)
    }
    override fun copy(mangler: String) = CombineFiniteRandomVariable(upstream1.copy(mangler), upstream2.copy(mangler), name?.plus( " (copy)"), mapping)
    override fun toString(): String = name ?: "Combination($upstream1, $upstream2)"
    override fun setName(newName: String) = CombineFiniteRandomVariable(upstream1, upstream2, name, mapping)
}


// Simplify basic operations for sensible types
operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Int>) = combine(this, other) { v1, v2 -> v1 + v2 }
operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Int>) = combine(this, other) { v1, v2 -> v1 - v2 }
operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Int>) = combine(this, other) { v1, v2 -> v1 * v2 }
operator fun FiniteRandomVariable<Int>.plus(other: Int) = this.map { it + other }
operator fun FiniteRandomVariable<Int>.minus(other: Int) = this.map { it - other }
operator fun FiniteRandomVariable<Int>.times(other: Int) = this.map { it * other}

// Rolling
/**
 * Roll this random variable without the context of any other random variables. Will roll all random
 * variables necessary to get an answer, you won't be able to find out their values!
 */
fun <E> FiniteRandomVariable<E>.rollAlone(): E = evaluate(sampleSpace.randomOutcome())
// TODO more combinations rolled together
/** Roll this collection of random variables together, providing all of their values in a tuple **/
fun <E1, E2> rollTogether(x1: FiniteRandomVariable<E1>, x2: FiniteRandomVariable<E2>) = combine(x1, x2) { v1, v2 -> Pair(v1, v2) }.rollAlone()
fun <E1, E2, E3> rollTogether(x1: FiniteRandomVariable<E1>, x2: FiniteRandomVariable<E2>, x3: FiniteRandomVariable<E3>) = combine(x1, x2, x3) { v1, v2, v3 -> Triple(v1, v2, v3) }.rollAlone()
fun <E1, E2, E3, E4> rollTogether(x1: FiniteRandomVariable<E1>, x2: FiniteRandomVariable<E2>, x3: FiniteRandomVariable<E3>, x4: FiniteRandomVariable<E4>) = combine(x1, x2, x3, x4) { v1, v2, v3, v4 -> Tuple4(v1, v2, v3, v4) }.rollAlone()

fun rollTogether(randomVariables: List<FiniteRandomVariable<*>>): Map<FiniteRandomVariable<*>, *> {
    if (randomVariables.count() == 0) return emptyMap<FiniteRandomVariable<*>, Unit>()
    val variables = randomVariables.toMutableList()
    val first = variables.removeAt(0)
    return variables.fold(first.map { mapOf(first to it) } as FiniteRandomVariable<Map<FiniteRandomVariable<*>, *>>) { acc, item ->
        combine(acc, item) { v1, v2 -> v1.toMutableMap().apply { set(item, v2) } }
    }.rollAlone()
}
