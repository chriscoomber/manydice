package chriscoomber.manydice

import com.benasher44.uuid.uuid4
import kotlin.jvm.JvmName

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
    fun clone(mangler: String = uuid4().toString()): FiniteRandomVariable<E>

    fun setName(newName: String): FiniteRandomVariable<E>
    val name: String

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

    fun conditionalProbabilityMassFunction(other: FiniteRandomVariable<Boolean>): Map<E, Float> =
        conditionalProbabilityMassFunction(other) { it }

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
    fun forgetDependencies(): FiniteRandomVariable<E> = PhysicalRandomVariable.fromProbabilityMassFunction(this.probabilityMassFunction, this.name)
}

inline fun <E, R> FiniteRandomVariable<E>.map(name: String? = null, crossinline mapping: (E) -> R) = MapFiniteRandomVariable(this, name) { mapping(it) }

// TODO more combines
inline fun <E1, E2, R> combine(x1: FiniteRandomVariable<E1>, x2: FiniteRandomVariable<E2>, name: String? = null, crossinline mapping: (E1, E2) -> R) =
    CombineFiniteRandomVariable(x1, x2, name) { v1, v2 -> mapping(v1, v2) }
inline fun <T1, T2, T3, R> combine(x1: FiniteRandomVariable<T1>, x2: FiniteRandomVariable<T2>, x3: FiniteRandomVariable<T3>, name: String? = null, crossinline mapping: (T1, T2, T3) -> R) =
    combine(combine(x1, x2, null) { v1, v2 -> Pair(v1, v2) }, x3, name) {
        (v1, v2), v3 -> mapping(v1, v2, v3)
    }
inline fun <T1, T2, T3, T4, R> combine(x1: FiniteRandomVariable<T1>, x2: FiniteRandomVariable<T2>, x3: FiniteRandomVariable<T3>, x4: FiniteRandomVariable<T4>, name: String? = null, crossinline mapping: (T1, T2, T3, T4) -> R) =
    combine(combine(x1, x2, x3, null) { v1, v2, v3 -> Triple(v1, v2, v3) }, x4, name) {
        (v1, v2, v3), v4 -> mapping(v1, v2, v3, v4)
    }
data class Tuple4<T1, T2, T3, T4>(val x1: T1, val x2: T2, val x3: T3, val x4: T4) {
    override fun toString() = "($x1, $x2, $x3, $x4)"
}
inline fun <T1, T2, T3, T4, T5, R> combine(x1: FiniteRandomVariable<T1>, x2: FiniteRandomVariable<T2>, x3: FiniteRandomVariable<T3>, x4: FiniteRandomVariable<T4>, x5: FiniteRandomVariable<T5>, name: String? = null, crossinline mapping: (T1, T2, T3, T4, T5) -> R) =
    combine(combine(x1, x2, x3, x4, null) { v1, v2, v3, v4 -> Tuple4(v1, v2, v3, v4) }, x5, name) {
        (v1, v2, v3, v4), v5 -> mapping(v1, v2, v3, v4, v5)
    }

class MapFiniteRandomVariable<E, R>(private val upstream: FiniteRandomVariable<E>, name: String? = null, val mapping: (E) -> R) :
    FiniteRandomVariable<R> {
    override val name = name ?: "Map($upstream)"
    override val sampleSpace = upstream.sampleSpace
    override fun evaluate(outcome: Map<PrimitiveFiniteSampleSpace, Int>) = mapping(upstream.evaluate(outcome))
    override fun clone(mangler: String) = MapFiniteRandomVariable(upstream.clone(mangler), "Copy($name)", mapping)
    override fun toString(): String = name
    override fun setName(newName: String) = MapFiniteRandomVariable(upstream, name, mapping)
}

class CombineFiniteRandomVariable<E1, E2, R>(private val upstream1: FiniteRandomVariable<E1>, private val upstream2: FiniteRandomVariable<E2>, name: String? = null, val mapping: (E1, E2) -> R) :
    FiniteRandomVariable<R> {
    override val name = name ?: "Combination($upstream1, $upstream2)"
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
    override fun clone(mangler: String) = CombineFiniteRandomVariable(upstream1.clone(mangler), upstream2.clone(mangler), "Copy($name)", mapping)
    override fun toString(): String = name
    override fun setName(newName: String) = CombineFiniteRandomVariable(upstream1, upstream2, name, mapping)
}

// Simplify basic operations for sensible types
private inline fun <E, R> FiniteRandomVariable<E>.unaryOperator(crossinline function: (E) -> R): FiniteRandomVariable<R> =
    this.map(mapping=function)
private inline fun <E1, E2, R> FiniteRandomVariable<E1>.binaryOperator(other: E2, crossinline function: (E1, E2) -> R): FiniteRandomVariable<R> =
    this.map { function(it, other) }
private inline fun <E1, E2, R> FiniteRandomVariable<E1>.binaryOperatorCombine(other: FiniteRandomVariable<E2>, crossinline function: (E1, E2) -> R): FiniteRandomVariable<R> =
    combine(this, other, mapping=function)

// Numbers: Byte Short Int Long Float Double
// Logic: Boolean
// Text: Char String
// TODO: finish this!

// Int
operator fun FiniteRandomVariable<Int>.unaryMinus() = this.unaryOperator(Int::unaryMinus)
operator fun FiniteRandomVariable<Int>.unaryPlus() = this.unaryOperator(Int::unaryPlus)
operator fun FiniteRandomVariable<Int>.inc() = this.unaryOperator(Int::inc)
operator fun FiniteRandomVariable<Int>.dec() = this.unaryOperator(Int::inc)
operator fun FiniteRandomVariable<Int>.plus(other: Byte) = this.binaryOperator(other, Int::plus)
operator fun FiniteRandomVariable<Int>.plus(other: Short) = this.binaryOperator(other, Int::plus)
operator fun FiniteRandomVariable<Int>.plus(other: Int) = this.binaryOperator(other, Int::plus)
operator fun FiniteRandomVariable<Int>.plus(other: Long) = this.binaryOperator(other, Int::plus)
operator fun FiniteRandomVariable<Int>.plus(other: Float) = this.binaryOperator(other, Int::plus)
operator fun FiniteRandomVariable<Int>.plus(other: Double) = this.binaryOperator(other, Int::plus)
@JvmName("plusByte") operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Int::plus)
@JvmName("plusShort") operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Int::plus)
@JvmName("plusInt") operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::plus)
@JvmName("plusLong") operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Int::plus)
@JvmName("plusFloat") operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Int::plus)
@JvmName("plusDouble") operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Int::plus)
operator fun FiniteRandomVariable<Int>.minus(other: Byte) = this.binaryOperator(other, Int::minus)
operator fun FiniteRandomVariable<Int>.minus(other: Short) = this.binaryOperator(other, Int::minus)
operator fun FiniteRandomVariable<Int>.minus(other: Int) = this.binaryOperator(other, Int::minus)
operator fun FiniteRandomVariable<Int>.minus(other: Long) = this.binaryOperator(other, Int::minus)
operator fun FiniteRandomVariable<Int>.minus(other: Float) = this.binaryOperator(other, Int::minus)
operator fun FiniteRandomVariable<Int>.minus(other: Double) = this.binaryOperator(other, Int::minus)
@JvmName("minusByte") operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Int::minus)
@JvmName("minusShort") operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Int::minus)
@JvmName("minusInt") operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::minus)
@JvmName("minusLong") operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Int::minus)
@JvmName("minusFloat") operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Int::minus)
@JvmName("minusDouble") operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Int::minus)
operator fun FiniteRandomVariable<Int>.times(other: Byte) = this.binaryOperator(other, Int::times)
operator fun FiniteRandomVariable<Int>.times(other: Short) = this.binaryOperator(other, Int::times)
operator fun FiniteRandomVariable<Int>.times(other: Int) = this.binaryOperator(other, Int::times)
operator fun FiniteRandomVariable<Int>.times(other: Long) = this.binaryOperator(other, Int::times)
operator fun FiniteRandomVariable<Int>.times(other: Float) = this.binaryOperator(other, Int::times)
operator fun FiniteRandomVariable<Int>.times(other: Double) = this.binaryOperator(other, Int::times)
@JvmName("timesByte") operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Int::times)
@JvmName("timesShort") operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Int::times)
@JvmName("timesInt") operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::times)
@JvmName("timesLong") operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Int::times)
@JvmName("timesFloat") operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Int::times)
@JvmName("timesDouble") operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Int::times)
operator fun FiniteRandomVariable<Int>.div(other: Byte) = this.binaryOperator(other, Int::div)
operator fun FiniteRandomVariable<Int>.div(other: Short) = this.binaryOperator(other, Int::div)
operator fun FiniteRandomVariable<Int>.div(other: Int) = this.binaryOperator(other, Int::div)
operator fun FiniteRandomVariable<Int>.div(other: Long) = this.binaryOperator(other, Int::div)
operator fun FiniteRandomVariable<Int>.div(other: Float) = this.binaryOperator(other, Int::div)
operator fun FiniteRandomVariable<Int>.div(other: Double) = this.binaryOperator(other, Int::div)
@JvmName("divByte") operator fun FiniteRandomVariable<Int>.div(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Int::div)
@JvmName("divShort") operator fun FiniteRandomVariable<Int>.div(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Int::div)
@JvmName("divInt") operator fun FiniteRandomVariable<Int>.div(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::div)
@JvmName("divLong") operator fun FiniteRandomVariable<Int>.div(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Int::div)
@JvmName("divFloat") operator fun FiniteRandomVariable<Int>.div(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Int::div)
@JvmName("divDouble") operator fun FiniteRandomVariable<Int>.div(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Int::div)
operator fun FiniteRandomVariable<Int>.rem(other: Byte) = this.binaryOperator(other, Int::rem)
operator fun FiniteRandomVariable<Int>.rem(other: Short) = this.binaryOperator(other, Int::rem)
operator fun FiniteRandomVariable<Int>.rem(other: Int) = this.binaryOperator(other, Int::rem)
operator fun FiniteRandomVariable<Int>.rem(other: Long) = this.binaryOperator(other, Int::rem)
operator fun FiniteRandomVariable<Int>.rem(other: Float) = this.binaryOperator(other, Int::rem)
operator fun FiniteRandomVariable<Int>.rem(other: Double) = this.binaryOperator(other, Int::rem)
@JvmName("remByte") operator fun FiniteRandomVariable<Int>.rem(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Int::rem)
@JvmName("remShort") operator fun FiniteRandomVariable<Int>.rem(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Int::rem)
@JvmName("remInt") operator fun FiniteRandomVariable<Int>.rem(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::rem)
@JvmName("remLong") operator fun FiniteRandomVariable<Int>.rem(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Int::rem)
@JvmName("remFloat") operator fun FiniteRandomVariable<Int>.rem(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Int::rem)
@JvmName("remDouble") operator fun FiniteRandomVariable<Int>.rem(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Int::rem)
operator fun FiniteRandomVariable<Int>.rangeTo(other: Byte) = this.binaryOperator(other, Int::rangeTo)
operator fun FiniteRandomVariable<Int>.rangeTo(other: Short) = this.binaryOperator(other, Int::rangeTo)
operator fun FiniteRandomVariable<Int>.rangeTo(other: Int) = this.binaryOperator(other, Int::rangeTo)
operator fun FiniteRandomVariable<Int>.rangeTo(other: Long) = this.binaryOperator(other, Int::rangeTo)
infix fun FiniteRandomVariable<Int>.gt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
infix fun FiniteRandomVariable<Int>.gt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
infix fun FiniteRandomVariable<Int>.gt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
infix fun FiniteRandomVariable<Int>.gt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
infix fun FiniteRandomVariable<Int>.gt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
infix fun FiniteRandomVariable<Int>.gt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("gtByte") infix fun FiniteRandomVariable<Int>.gt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("gtShort") infix fun FiniteRandomVariable<Int>.gt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("gtInt") infix fun FiniteRandomVariable<Int>.gt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("gtLong") infix fun FiniteRandomVariable<Int>.gt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("gtFloat") infix fun FiniteRandomVariable<Int>.gt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("gtDouble") infix fun FiniteRandomVariable<Int>.gt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
infix fun FiniteRandomVariable<Int>.ge(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
infix fun FiniteRandomVariable<Int>.ge(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
infix fun FiniteRandomVariable<Int>.ge(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
infix fun FiniteRandomVariable<Int>.ge(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
infix fun FiniteRandomVariable<Int>.ge(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
infix fun FiniteRandomVariable<Int>.ge(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("geByte") infix fun FiniteRandomVariable<Int>.ge(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("geShort") infix fun FiniteRandomVariable<Int>.ge(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("geInt") infix fun FiniteRandomVariable<Int>.ge(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("geLong") infix fun FiniteRandomVariable<Int>.ge(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("geFloat") infix fun FiniteRandomVariable<Int>.ge(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("geDouble") infix fun FiniteRandomVariable<Int>.ge(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
infix fun FiniteRandomVariable<Int>.eq(other: Any?) = this.binaryOperator(other, Int::equals)
infix fun FiniteRandomVariable<Int>.eq(other: FiniteRandomVariable<Any?>) = this.binaryOperator(other, Int::equals)
infix fun FiniteRandomVariable<Int>.le(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
infix fun FiniteRandomVariable<Int>.le(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
infix fun FiniteRandomVariable<Int>.le(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
infix fun FiniteRandomVariable<Int>.le(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
infix fun FiniteRandomVariable<Int>.le(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
infix fun FiniteRandomVariable<Int>.le(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("leByte") infix fun FiniteRandomVariable<Int>.le(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("leShort") infix fun FiniteRandomVariable<Int>.le(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("leInt") infix fun FiniteRandomVariable<Int>.le(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("leLong") infix fun FiniteRandomVariable<Int>.le(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("leFloat") infix fun FiniteRandomVariable<Int>.le(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("leDouble") infix fun FiniteRandomVariable<Int>.le(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
infix fun FiniteRandomVariable<Int>.lt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
infix fun FiniteRandomVariable<Int>.lt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
infix fun FiniteRandomVariable<Int>.lt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
infix fun FiniteRandomVariable<Int>.lt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
infix fun FiniteRandomVariable<Int>.lt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
infix fun FiniteRandomVariable<Int>.lt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("ltByte") infix fun FiniteRandomVariable<Int>.lt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ltShort") infix fun FiniteRandomVariable<Int>.lt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ltInt") infix fun FiniteRandomVariable<Int>.lt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ltLong") infix fun FiniteRandomVariable<Int>.lt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ltFloat") infix fun FiniteRandomVariable<Int>.lt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ltDouble") infix fun FiniteRandomVariable<Int>.lt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
infix fun FiniteRandomVariable<Int>.and(other: Int) = this.binaryOperator(other, Int::and)
infix fun FiniteRandomVariable<Int>.and(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::and)
infix fun FiniteRandomVariable<Int>.or(other: Int) = this.binaryOperator(other, Int::or)
infix fun FiniteRandomVariable<Int>.or(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::or)
infix fun FiniteRandomVariable<Int>.xor(other: Int) = this.binaryOperator(other, Int::xor)
infix fun FiniteRandomVariable<Int>.xor(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::xor)
infix fun FiniteRandomVariable<Int>.shl(other: Int) = this.binaryOperator(other, Int::shl)
infix fun FiniteRandomVariable<Int>.shl(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::shl)
infix fun FiniteRandomVariable<Int>.shr(other: Int) = this.binaryOperator(other, Int::shr)
infix fun FiniteRandomVariable<Int>.shr(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::shr)
infix fun FiniteRandomVariable<Int>.ushr(other: Int) = this.binaryOperator(other, Int::ushr)
infix fun FiniteRandomVariable<Int>.ushr(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::ushr)

// Boolean
operator fun FiniteRandomVariable<Boolean>.not() = this.map { !it }

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
