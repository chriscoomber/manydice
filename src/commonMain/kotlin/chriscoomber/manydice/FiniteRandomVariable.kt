package chriscoomber.manydice

import com.benasher44.uuid.uuid4
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.experimental.xor
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

// Implement common operator functions for the new class.
// Here's the python script I used to generate all the @JvmName annotations
//with open('src/commonMain/kotlin/chriscoomber/manydice/FiniteRandomVariable.kt') as source:
//    with open('FiniteRandomVariable.kt.out', 'w') as output:
//        for line in source:
//            if line.startswith(""${'"'}operator fun FiniteRandomVariable<""${'"'}) or line.startswith(""${'"'}infix fun FiniteRandomVariable<""${'"'}):
//                firstGeneric = line.split("FiniteRandomVariable<")[1].split(">")[0]
//                functionName = line.split(".")[1].split("(")[0]
//                secondGeneric = None
//                try:
//                    secondGeneric = line.split("FiniteRandomVariable<")[2].split(">")[0].split("?")[0]
//                except:
//                    pass
//                if secondGeneric:
//                    jvmName = firstGeneric+functionName+secondGeneric
//                else:
//                    jvmName = firstGeneric+functionName
//                line = '@JvmName("' + jvmName + '") ' + line
//            output.write(line)

// Numbers: Byte Short Int Long Float Double
// Logic: Boolean
// Text: Char String

// Byte
@JvmName("ByteunaryMinus") operator fun FiniteRandomVariable<Byte>.unaryMinus() = this.unaryOperator(Byte::unaryMinus)
@JvmName("ByteunaryPlus") operator fun FiniteRandomVariable<Byte>.unaryPlus() = this.unaryOperator(Byte::unaryPlus)
@JvmName("Byteinc") operator fun FiniteRandomVariable<Byte>.inc() = this.unaryOperator(Byte::inc)
@JvmName("Bytedec") operator fun FiniteRandomVariable<Byte>.dec() = this.unaryOperator(Byte::inc)
@JvmName("Byteplus") operator fun FiniteRandomVariable<Byte>.plus(other: Byte) = this.binaryOperator(other, Byte::plus)
@JvmName("Byteplus") operator fun FiniteRandomVariable<Byte>.plus(other: Short) = this.binaryOperator(other, Byte::plus)
@JvmName("Byteplus") operator fun FiniteRandomVariable<Byte>.plus(other: Int) = this.binaryOperator(other, Byte::plus)
@JvmName("Byteplus") operator fun FiniteRandomVariable<Byte>.plus(other: Long) = this.binaryOperator(other, Byte::plus)
@JvmName("Byteplus") operator fun FiniteRandomVariable<Byte>.plus(other: Float) = this.binaryOperator(other, Byte::plus)
@JvmName("Byteplus") operator fun FiniteRandomVariable<Byte>.plus(other: Double) = this.binaryOperator(other, Byte::plus)
@JvmName("ByteplusByte") operator fun FiniteRandomVariable<Byte>.plus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Byte::plus)
@JvmName("ByteplusShort") operator fun FiniteRandomVariable<Byte>.plus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Byte::plus)
@JvmName("ByteplusInt") operator fun FiniteRandomVariable<Byte>.plus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Byte::plus)
@JvmName("ByteplusLong") operator fun FiniteRandomVariable<Byte>.plus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Byte::plus)
@JvmName("ByteplusFloat") operator fun FiniteRandomVariable<Byte>.plus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Byte::plus)
@JvmName("ByteplusDouble") operator fun FiniteRandomVariable<Byte>.plus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Byte::plus)
@JvmName("Byteminus") operator fun FiniteRandomVariable<Byte>.minus(other: Byte) = this.binaryOperator(other, Byte::minus)
@JvmName("Byteminus") operator fun FiniteRandomVariable<Byte>.minus(other: Short) = this.binaryOperator(other, Byte::minus)
@JvmName("Byteminus") operator fun FiniteRandomVariable<Byte>.minus(other: Int) = this.binaryOperator(other, Byte::minus)
@JvmName("Byteminus") operator fun FiniteRandomVariable<Byte>.minus(other: Long) = this.binaryOperator(other, Byte::minus)
@JvmName("Byteminus") operator fun FiniteRandomVariable<Byte>.minus(other: Float) = this.binaryOperator(other, Byte::minus)
@JvmName("Byteminus") operator fun FiniteRandomVariable<Byte>.minus(other: Double) = this.binaryOperator(other, Byte::minus)
@JvmName("ByteminusByte") operator fun FiniteRandomVariable<Byte>.minus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Byte::minus)
@JvmName("ByteminusShort") operator fun FiniteRandomVariable<Byte>.minus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Byte::minus)
@JvmName("ByteminusInt") operator fun FiniteRandomVariable<Byte>.minus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Byte::minus)
@JvmName("ByteminusLong") operator fun FiniteRandomVariable<Byte>.minus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Byte::minus)
@JvmName("ByteminusFloat") operator fun FiniteRandomVariable<Byte>.minus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Byte::minus)
@JvmName("ByteminusDouble") operator fun FiniteRandomVariable<Byte>.minus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Byte::minus)
@JvmName("Bytetimes") operator fun FiniteRandomVariable<Byte>.times(other: Byte) = this.binaryOperator(other, Byte::times)
@JvmName("Bytetimes") operator fun FiniteRandomVariable<Byte>.times(other: Short) = this.binaryOperator(other, Byte::times)
@JvmName("Bytetimes") operator fun FiniteRandomVariable<Byte>.times(other: Int) = this.binaryOperator(other, Byte::times)
@JvmName("Bytetimes") operator fun FiniteRandomVariable<Byte>.times(other: Long) = this.binaryOperator(other, Byte::times)
@JvmName("Bytetimes") operator fun FiniteRandomVariable<Byte>.times(other: Float) = this.binaryOperator(other, Byte::times)
@JvmName("Bytetimes") operator fun FiniteRandomVariable<Byte>.times(other: Double) = this.binaryOperator(other, Byte::times)
@JvmName("BytetimesByte") operator fun FiniteRandomVariable<Byte>.times(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Byte::times)
@JvmName("BytetimesShort") operator fun FiniteRandomVariable<Byte>.times(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Byte::times)
@JvmName("BytetimesInt") operator fun FiniteRandomVariable<Byte>.times(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Byte::times)
@JvmName("BytetimesLong") operator fun FiniteRandomVariable<Byte>.times(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Byte::times)
@JvmName("BytetimesFloat") operator fun FiniteRandomVariable<Byte>.times(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Byte::times)
@JvmName("BytetimesDouble") operator fun FiniteRandomVariable<Byte>.times(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Byte::times)
@JvmName("Bytediv") operator fun FiniteRandomVariable<Byte>.div(other: Byte) = this.binaryOperator(other, Byte::div)
@JvmName("Bytediv") operator fun FiniteRandomVariable<Byte>.div(other: Short) = this.binaryOperator(other, Byte::div)
@JvmName("Bytediv") operator fun FiniteRandomVariable<Byte>.div(other: Int) = this.binaryOperator(other, Byte::div)
@JvmName("Bytediv") operator fun FiniteRandomVariable<Byte>.div(other: Long) = this.binaryOperator(other, Byte::div)
@JvmName("Bytediv") operator fun FiniteRandomVariable<Byte>.div(other: Float) = this.binaryOperator(other, Byte::div)
@JvmName("Bytediv") operator fun FiniteRandomVariable<Byte>.div(other: Double) = this.binaryOperator(other, Byte::div)
@JvmName("BytedivByte") operator fun FiniteRandomVariable<Byte>.div(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Byte::div)
@JvmName("BytedivShort") operator fun FiniteRandomVariable<Byte>.div(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Byte::div)
@JvmName("BytedivInt") operator fun FiniteRandomVariable<Byte>.div(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Byte::div)
@JvmName("BytedivLong") operator fun FiniteRandomVariable<Byte>.div(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Byte::div)
@JvmName("BytedivFloat") operator fun FiniteRandomVariable<Byte>.div(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Byte::div)
@JvmName("BytedivDouble") operator fun FiniteRandomVariable<Byte>.div(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Byte::div)
@JvmName("Byterem") operator fun FiniteRandomVariable<Byte>.rem(other: Byte) = this.binaryOperator(other, Byte::rem)
@JvmName("Byterem") operator fun FiniteRandomVariable<Byte>.rem(other: Short) = this.binaryOperator(other, Byte::rem)
@JvmName("Byterem") operator fun FiniteRandomVariable<Byte>.rem(other: Int) = this.binaryOperator(other, Byte::rem)
@JvmName("Byterem") operator fun FiniteRandomVariable<Byte>.rem(other: Long) = this.binaryOperator(other, Byte::rem)
@JvmName("Byterem") operator fun FiniteRandomVariable<Byte>.rem(other: Float) = this.binaryOperator(other, Byte::rem)
@JvmName("Byterem") operator fun FiniteRandomVariable<Byte>.rem(other: Double) = this.binaryOperator(other, Byte::rem)
@JvmName("ByteremByte") operator fun FiniteRandomVariable<Byte>.rem(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Byte::rem)
@JvmName("ByteremShort") operator fun FiniteRandomVariable<Byte>.rem(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Byte::rem)
@JvmName("ByteremInt") operator fun FiniteRandomVariable<Byte>.rem(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Byte::rem)
@JvmName("ByteremLong") operator fun FiniteRandomVariable<Byte>.rem(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Byte::rem)
@JvmName("ByteremFloat") operator fun FiniteRandomVariable<Byte>.rem(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Byte::rem)
@JvmName("ByteremDouble") operator fun FiniteRandomVariable<Byte>.rem(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Byte::rem)
@JvmName("ByterangeTo") operator fun FiniteRandomVariable<Byte>.rangeTo(other: Byte) = this.binaryOperator(other, Byte::rangeTo)
@JvmName("ByterangeTo") operator fun FiniteRandomVariable<Byte>.rangeTo(other: Short) = this.binaryOperator(other, Byte::rangeTo)
@JvmName("ByterangeTo") operator fun FiniteRandomVariable<Byte>.rangeTo(other: Int) = this.binaryOperator(other, Byte::rangeTo)
@JvmName("ByterangeTo") operator fun FiniteRandomVariable<Byte>.rangeTo(other: Long) = this.binaryOperator(other, Byte::rangeTo)
@JvmName("Bytegt") infix fun FiniteRandomVariable<Byte>.gt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Bytegt") infix fun FiniteRandomVariable<Byte>.gt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Bytegt") infix fun FiniteRandomVariable<Byte>.gt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Bytegt") infix fun FiniteRandomVariable<Byte>.gt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Bytegt") infix fun FiniteRandomVariable<Byte>.gt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Bytegt") infix fun FiniteRandomVariable<Byte>.gt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("BytegtByte") infix fun FiniteRandomVariable<Byte>.gt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("BytegtShort") infix fun FiniteRandomVariable<Byte>.gt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("BytegtInt") infix fun FiniteRandomVariable<Byte>.gt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("BytegtLong") infix fun FiniteRandomVariable<Byte>.gt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("BytegtFloat") infix fun FiniteRandomVariable<Byte>.gt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("BytegtDouble") infix fun FiniteRandomVariable<Byte>.gt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("Bytege") infix fun FiniteRandomVariable<Byte>.ge(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Bytege") infix fun FiniteRandomVariable<Byte>.ge(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Bytege") infix fun FiniteRandomVariable<Byte>.ge(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Bytege") infix fun FiniteRandomVariable<Byte>.ge(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Bytege") infix fun FiniteRandomVariable<Byte>.ge(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Bytege") infix fun FiniteRandomVariable<Byte>.ge(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("BytegeByte") infix fun FiniteRandomVariable<Byte>.ge(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("BytegeShort") infix fun FiniteRandomVariable<Byte>.ge(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("BytegeInt") infix fun FiniteRandomVariable<Byte>.ge(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("BytegeLong") infix fun FiniteRandomVariable<Byte>.ge(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("BytegeFloat") infix fun FiniteRandomVariable<Byte>.ge(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("BytegeDouble") infix fun FiniteRandomVariable<Byte>.ge(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("Byteeq") infix fun FiniteRandomVariable<Byte>.eq(other: Any?) = this.binaryOperator(other, Byte::equals)
@JvmName("ByteeqAny") infix fun FiniteRandomVariable<Byte>.eq(other: FiniteRandomVariable<Any?>) = this.binaryOperator(other, Byte::equals)
@JvmName("Bytele") infix fun FiniteRandomVariable<Byte>.le(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Bytele") infix fun FiniteRandomVariable<Byte>.le(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Bytele") infix fun FiniteRandomVariable<Byte>.le(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Bytele") infix fun FiniteRandomVariable<Byte>.le(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Bytele") infix fun FiniteRandomVariable<Byte>.le(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Bytele") infix fun FiniteRandomVariable<Byte>.le(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("ByteleByte") infix fun FiniteRandomVariable<Byte>.le(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("ByteleShort") infix fun FiniteRandomVariable<Byte>.le(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("ByteleInt") infix fun FiniteRandomVariable<Byte>.le(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("ByteleLong") infix fun FiniteRandomVariable<Byte>.le(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("ByteleFloat") infix fun FiniteRandomVariable<Byte>.le(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("ByteleDouble") infix fun FiniteRandomVariable<Byte>.le(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("Bytelt") infix fun FiniteRandomVariable<Byte>.lt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Bytelt") infix fun FiniteRandomVariable<Byte>.lt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Bytelt") infix fun FiniteRandomVariable<Byte>.lt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Bytelt") infix fun FiniteRandomVariable<Byte>.lt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Bytelt") infix fun FiniteRandomVariable<Byte>.lt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Bytelt") infix fun FiniteRandomVariable<Byte>.lt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("ByteltByte") infix fun FiniteRandomVariable<Byte>.lt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ByteltShort") infix fun FiniteRandomVariable<Byte>.lt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ByteltInt") infix fun FiniteRandomVariable<Byte>.lt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ByteltLong") infix fun FiniteRandomVariable<Byte>.lt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ByteltFloat") infix fun FiniteRandomVariable<Byte>.lt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ByteltDouble") infix fun FiniteRandomVariable<Byte>.lt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("Byteand") infix fun FiniteRandomVariable<Byte>.and(other: Byte) = this.binaryOperator(other, Byte::and)
@JvmName("ByteandByte") infix fun FiniteRandomVariable<Byte>.and(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Byte::and)
@JvmName("Byteor") infix fun FiniteRandomVariable<Byte>.or(other: Byte) = this.binaryOperator(other, Byte::or)
@JvmName("ByteorByte") infix fun FiniteRandomVariable<Byte>.or(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Byte::or)
@JvmName("Bytexor") infix fun FiniteRandomVariable<Byte>.xor(other: Byte) = this.binaryOperator(other, Byte::xor)
@JvmName("BytexorByte") infix fun FiniteRandomVariable<Byte>.xor(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Byte::xor)

// Short
@JvmName("ShortunaryMinus") operator fun FiniteRandomVariable<Short>.unaryMinus() = this.unaryOperator(Short::unaryMinus)
@JvmName("ShortunaryPlus") operator fun FiniteRandomVariable<Short>.unaryPlus() = this.unaryOperator(Short::unaryPlus)
@JvmName("Shortinc") operator fun FiniteRandomVariable<Short>.inc() = this.unaryOperator(Short::inc)
@JvmName("Shortdec") operator fun FiniteRandomVariable<Short>.dec() = this.unaryOperator(Short::inc)
@JvmName("Shortplus") operator fun FiniteRandomVariable<Short>.plus(other: Byte) = this.binaryOperator(other, Short::plus)
@JvmName("Shortplus") operator fun FiniteRandomVariable<Short>.plus(other: Short) = this.binaryOperator(other, Short::plus)
@JvmName("Shortplus") operator fun FiniteRandomVariable<Short>.plus(other: Int) = this.binaryOperator(other, Short::plus)
@JvmName("Shortplus") operator fun FiniteRandomVariable<Short>.plus(other: Long) = this.binaryOperator(other, Short::plus)
@JvmName("Shortplus") operator fun FiniteRandomVariable<Short>.plus(other: Float) = this.binaryOperator(other, Short::plus)
@JvmName("Shortplus") operator fun FiniteRandomVariable<Short>.plus(other: Double) = this.binaryOperator(other, Short::plus)
@JvmName("ShortplusByte") operator fun FiniteRandomVariable<Short>.plus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Short::plus)
@JvmName("ShortplusShort") operator fun FiniteRandomVariable<Short>.plus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Short::plus)
@JvmName("ShortplusInt") operator fun FiniteRandomVariable<Short>.plus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Short::plus)
@JvmName("ShortplusLong") operator fun FiniteRandomVariable<Short>.plus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Short::plus)
@JvmName("ShortplusFloat") operator fun FiniteRandomVariable<Short>.plus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Short::plus)
@JvmName("ShortplusDouble") operator fun FiniteRandomVariable<Short>.plus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Short::plus)
@JvmName("Shortminus") operator fun FiniteRandomVariable<Short>.minus(other: Byte) = this.binaryOperator(other, Short::minus)
@JvmName("Shortminus") operator fun FiniteRandomVariable<Short>.minus(other: Short) = this.binaryOperator(other, Short::minus)
@JvmName("Shortminus") operator fun FiniteRandomVariable<Short>.minus(other: Int) = this.binaryOperator(other, Short::minus)
@JvmName("Shortminus") operator fun FiniteRandomVariable<Short>.minus(other: Long) = this.binaryOperator(other, Short::minus)
@JvmName("Shortminus") operator fun FiniteRandomVariable<Short>.minus(other: Float) = this.binaryOperator(other, Short::minus)
@JvmName("Shortminus") operator fun FiniteRandomVariable<Short>.minus(other: Double) = this.binaryOperator(other, Short::minus)
@JvmName("ShortminusByte") operator fun FiniteRandomVariable<Short>.minus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Short::minus)
@JvmName("ShortminusShort") operator fun FiniteRandomVariable<Short>.minus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Short::minus)
@JvmName("ShortminusInt") operator fun FiniteRandomVariable<Short>.minus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Short::minus)
@JvmName("ShortminusLong") operator fun FiniteRandomVariable<Short>.minus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Short::minus)
@JvmName("ShortminusFloat") operator fun FiniteRandomVariable<Short>.minus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Short::minus)
@JvmName("ShortminusDouble") operator fun FiniteRandomVariable<Short>.minus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Short::minus)
@JvmName("Shorttimes") operator fun FiniteRandomVariable<Short>.times(other: Byte) = this.binaryOperator(other, Short::times)
@JvmName("Shorttimes") operator fun FiniteRandomVariable<Short>.times(other: Short) = this.binaryOperator(other, Short::times)
@JvmName("Shorttimes") operator fun FiniteRandomVariable<Short>.times(other: Int) = this.binaryOperator(other, Short::times)
@JvmName("Shorttimes") operator fun FiniteRandomVariable<Short>.times(other: Long) = this.binaryOperator(other, Short::times)
@JvmName("Shorttimes") operator fun FiniteRandomVariable<Short>.times(other: Float) = this.binaryOperator(other, Short::times)
@JvmName("Shorttimes") operator fun FiniteRandomVariable<Short>.times(other: Double) = this.binaryOperator(other, Short::times)
@JvmName("ShorttimesByte") operator fun FiniteRandomVariable<Short>.times(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Short::times)
@JvmName("ShorttimesShort") operator fun FiniteRandomVariable<Short>.times(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Short::times)
@JvmName("ShorttimesInt") operator fun FiniteRandomVariable<Short>.times(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Short::times)
@JvmName("ShorttimesLong") operator fun FiniteRandomVariable<Short>.times(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Short::times)
@JvmName("ShorttimesFloat") operator fun FiniteRandomVariable<Short>.times(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Short::times)
@JvmName("ShorttimesDouble") operator fun FiniteRandomVariable<Short>.times(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Short::times)
@JvmName("Shortdiv") operator fun FiniteRandomVariable<Short>.div(other: Byte) = this.binaryOperator(other, Short::div)
@JvmName("Shortdiv") operator fun FiniteRandomVariable<Short>.div(other: Short) = this.binaryOperator(other, Short::div)
@JvmName("Shortdiv") operator fun FiniteRandomVariable<Short>.div(other: Int) = this.binaryOperator(other, Short::div)
@JvmName("Shortdiv") operator fun FiniteRandomVariable<Short>.div(other: Long) = this.binaryOperator(other, Short::div)
@JvmName("Shortdiv") operator fun FiniteRandomVariable<Short>.div(other: Float) = this.binaryOperator(other, Short::div)
@JvmName("Shortdiv") operator fun FiniteRandomVariable<Short>.div(other: Double) = this.binaryOperator(other, Short::div)
@JvmName("ShortdivByte") operator fun FiniteRandomVariable<Short>.div(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Short::div)
@JvmName("ShortdivShort") operator fun FiniteRandomVariable<Short>.div(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Short::div)
@JvmName("ShortdivInt") operator fun FiniteRandomVariable<Short>.div(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Short::div)
@JvmName("ShortdivLong") operator fun FiniteRandomVariable<Short>.div(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Short::div)
@JvmName("ShortdivFloat") operator fun FiniteRandomVariable<Short>.div(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Short::div)
@JvmName("ShortdivDouble") operator fun FiniteRandomVariable<Short>.div(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Short::div)
@JvmName("Shortrem") operator fun FiniteRandomVariable<Short>.rem(other: Byte) = this.binaryOperator(other, Short::rem)
@JvmName("Shortrem") operator fun FiniteRandomVariable<Short>.rem(other: Short) = this.binaryOperator(other, Short::rem)
@JvmName("Shortrem") operator fun FiniteRandomVariable<Short>.rem(other: Int) = this.binaryOperator(other, Short::rem)
@JvmName("Shortrem") operator fun FiniteRandomVariable<Short>.rem(other: Long) = this.binaryOperator(other, Short::rem)
@JvmName("Shortrem") operator fun FiniteRandomVariable<Short>.rem(other: Float) = this.binaryOperator(other, Short::rem)
@JvmName("Shortrem") operator fun FiniteRandomVariable<Short>.rem(other: Double) = this.binaryOperator(other, Short::rem)
@JvmName("ShortremByte") operator fun FiniteRandomVariable<Short>.rem(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Short::rem)
@JvmName("ShortremShort") operator fun FiniteRandomVariable<Short>.rem(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Short::rem)
@JvmName("ShortremInt") operator fun FiniteRandomVariable<Short>.rem(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Short::rem)
@JvmName("ShortremLong") operator fun FiniteRandomVariable<Short>.rem(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Short::rem)
@JvmName("ShortremFloat") operator fun FiniteRandomVariable<Short>.rem(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Short::rem)
@JvmName("ShortremDouble") operator fun FiniteRandomVariable<Short>.rem(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Short::rem)
@JvmName("ShortrangeTo") operator fun FiniteRandomVariable<Short>.rangeTo(other: Byte) = this.binaryOperator(other, Short::rangeTo)
@JvmName("ShortrangeTo") operator fun FiniteRandomVariable<Short>.rangeTo(other: Short) = this.binaryOperator(other, Short::rangeTo)
@JvmName("ShortrangeTo") operator fun FiniteRandomVariable<Short>.rangeTo(other: Int) = this.binaryOperator(other, Short::rangeTo)
@JvmName("ShortrangeTo") operator fun FiniteRandomVariable<Short>.rangeTo(other: Long) = this.binaryOperator(other, Short::rangeTo)
@JvmName("Shortgt") infix fun FiniteRandomVariable<Short>.gt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Shortgt") infix fun FiniteRandomVariable<Short>.gt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Shortgt") infix fun FiniteRandomVariable<Short>.gt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Shortgt") infix fun FiniteRandomVariable<Short>.gt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Shortgt") infix fun FiniteRandomVariable<Short>.gt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Shortgt") infix fun FiniteRandomVariable<Short>.gt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("ShortgtByte") infix fun FiniteRandomVariable<Short>.gt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("ShortgtShort") infix fun FiniteRandomVariable<Short>.gt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("ShortgtInt") infix fun FiniteRandomVariable<Short>.gt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("ShortgtLong") infix fun FiniteRandomVariable<Short>.gt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("ShortgtFloat") infix fun FiniteRandomVariable<Short>.gt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("ShortgtDouble") infix fun FiniteRandomVariable<Short>.gt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("Shortge") infix fun FiniteRandomVariable<Short>.ge(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Shortge") infix fun FiniteRandomVariable<Short>.ge(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Shortge") infix fun FiniteRandomVariable<Short>.ge(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Shortge") infix fun FiniteRandomVariable<Short>.ge(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Shortge") infix fun FiniteRandomVariable<Short>.ge(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Shortge") infix fun FiniteRandomVariable<Short>.ge(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("ShortgeByte") infix fun FiniteRandomVariable<Short>.ge(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("ShortgeShort") infix fun FiniteRandomVariable<Short>.ge(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("ShortgeInt") infix fun FiniteRandomVariable<Short>.ge(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("ShortgeLong") infix fun FiniteRandomVariable<Short>.ge(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("ShortgeFloat") infix fun FiniteRandomVariable<Short>.ge(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("ShortgeDouble") infix fun FiniteRandomVariable<Short>.ge(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("Shorteq") infix fun FiniteRandomVariable<Short>.eq(other: Any?) = this.binaryOperator(other, Short::equals)
@JvmName("ShorteqAny") infix fun FiniteRandomVariable<Short>.eq(other: FiniteRandomVariable<Any?>) = this.binaryOperator(other, Short::equals)
@JvmName("Shortle") infix fun FiniteRandomVariable<Short>.le(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Shortle") infix fun FiniteRandomVariable<Short>.le(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Shortle") infix fun FiniteRandomVariable<Short>.le(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Shortle") infix fun FiniteRandomVariable<Short>.le(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Shortle") infix fun FiniteRandomVariable<Short>.le(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Shortle") infix fun FiniteRandomVariable<Short>.le(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("ShortleByte") infix fun FiniteRandomVariable<Short>.le(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("ShortleShort") infix fun FiniteRandomVariable<Short>.le(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("ShortleInt") infix fun FiniteRandomVariable<Short>.le(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("ShortleLong") infix fun FiniteRandomVariable<Short>.le(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("ShortleFloat") infix fun FiniteRandomVariable<Short>.le(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("ShortleDouble") infix fun FiniteRandomVariable<Short>.le(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("Shortlt") infix fun FiniteRandomVariable<Short>.lt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Shortlt") infix fun FiniteRandomVariable<Short>.lt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Shortlt") infix fun FiniteRandomVariable<Short>.lt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Shortlt") infix fun FiniteRandomVariable<Short>.lt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Shortlt") infix fun FiniteRandomVariable<Short>.lt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Shortlt") infix fun FiniteRandomVariable<Short>.lt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("ShortltByte") infix fun FiniteRandomVariable<Short>.lt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ShortltShort") infix fun FiniteRandomVariable<Short>.lt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ShortltInt") infix fun FiniteRandomVariable<Short>.lt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ShortltLong") infix fun FiniteRandomVariable<Short>.lt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ShortltFloat") infix fun FiniteRandomVariable<Short>.lt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("ShortltDouble") infix fun FiniteRandomVariable<Short>.lt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("Shortand") infix fun FiniteRandomVariable<Short>.and(other: Short) = this.binaryOperator(other, Short::and)
@JvmName("ShortandShort") infix fun FiniteRandomVariable<Short>.and(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Short::and)
@JvmName("Shortor") infix fun FiniteRandomVariable<Short>.or(other: Short) = this.binaryOperator(other, Short::or)
@JvmName("ShortorShort") infix fun FiniteRandomVariable<Short>.or(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Short::or)
@JvmName("Shortxor") infix fun FiniteRandomVariable<Short>.xor(other: Short) = this.binaryOperator(other, Short::xor)
@JvmName("ShortxorShort") infix fun FiniteRandomVariable<Short>.xor(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Short::xor)

// Int
@JvmName("IntunaryMinus") operator fun FiniteRandomVariable<Int>.unaryMinus() = this.unaryOperator(Int::unaryMinus)
@JvmName("IntunaryPlus") operator fun FiniteRandomVariable<Int>.unaryPlus() = this.unaryOperator(Int::unaryPlus)
@JvmName("Intinc") operator fun FiniteRandomVariable<Int>.inc() = this.unaryOperator(Int::inc)
@JvmName("Intdec") operator fun FiniteRandomVariable<Int>.dec() = this.unaryOperator(Int::inc)
@JvmName("Intplus") operator fun FiniteRandomVariable<Int>.plus(other: Byte) = this.binaryOperator(other, Int::plus)
@JvmName("Intplus") operator fun FiniteRandomVariable<Int>.plus(other: Short) = this.binaryOperator(other, Int::plus)
@JvmName("Intplus") operator fun FiniteRandomVariable<Int>.plus(other: Int) = this.binaryOperator(other, Int::plus)
@JvmName("Intplus") operator fun FiniteRandomVariable<Int>.plus(other: Long) = this.binaryOperator(other, Int::plus)
@JvmName("Intplus") operator fun FiniteRandomVariable<Int>.plus(other: Float) = this.binaryOperator(other, Int::plus)
@JvmName("Intplus") operator fun FiniteRandomVariable<Int>.plus(other: Double) = this.binaryOperator(other, Int::plus)
@JvmName("IntplusByte") operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Int::plus)
@JvmName("IntplusShort") operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Int::plus)
@JvmName("IntplusInt") operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::plus)
@JvmName("IntplusLong") operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Int::plus)
@JvmName("IntplusFloat") operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Int::plus)
@JvmName("IntplusDouble") operator fun FiniteRandomVariable<Int>.plus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Int::plus)
@JvmName("Intminus") operator fun FiniteRandomVariable<Int>.minus(other: Byte) = this.binaryOperator(other, Int::minus)
@JvmName("Intminus") operator fun FiniteRandomVariable<Int>.minus(other: Short) = this.binaryOperator(other, Int::minus)
@JvmName("Intminus") operator fun FiniteRandomVariable<Int>.minus(other: Int) = this.binaryOperator(other, Int::minus)
@JvmName("Intminus") operator fun FiniteRandomVariable<Int>.minus(other: Long) = this.binaryOperator(other, Int::minus)
@JvmName("Intminus") operator fun FiniteRandomVariable<Int>.minus(other: Float) = this.binaryOperator(other, Int::minus)
@JvmName("Intminus") operator fun FiniteRandomVariable<Int>.minus(other: Double) = this.binaryOperator(other, Int::minus)
@JvmName("IntminusByte") operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Int::minus)
@JvmName("IntminusShort") operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Int::minus)
@JvmName("IntminusInt") operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::minus)
@JvmName("IntminusLong") operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Int::minus)
@JvmName("IntminusFloat") operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Int::minus)
@JvmName("IntminusDouble") operator fun FiniteRandomVariable<Int>.minus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Int::minus)
@JvmName("Inttimes") operator fun FiniteRandomVariable<Int>.times(other: Byte) = this.binaryOperator(other, Int::times)
@JvmName("Inttimes") operator fun FiniteRandomVariable<Int>.times(other: Short) = this.binaryOperator(other, Int::times)
@JvmName("Inttimes") operator fun FiniteRandomVariable<Int>.times(other: Int) = this.binaryOperator(other, Int::times)
@JvmName("Inttimes") operator fun FiniteRandomVariable<Int>.times(other: Long) = this.binaryOperator(other, Int::times)
@JvmName("Inttimes") operator fun FiniteRandomVariable<Int>.times(other: Float) = this.binaryOperator(other, Int::times)
@JvmName("Inttimes") operator fun FiniteRandomVariable<Int>.times(other: Double) = this.binaryOperator(other, Int::times)
@JvmName("InttimesByte") operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Int::times)
@JvmName("InttimesShort") operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Int::times)
@JvmName("InttimesInt") operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::times)
@JvmName("InttimesLong") operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Int::times)
@JvmName("InttimesFloat") operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Int::times)
@JvmName("InttimesDouble") operator fun FiniteRandomVariable<Int>.times(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Int::times)
@JvmName("Intdiv") operator fun FiniteRandomVariable<Int>.div(other: Byte) = this.binaryOperator(other, Int::div)
@JvmName("Intdiv") operator fun FiniteRandomVariable<Int>.div(other: Short) = this.binaryOperator(other, Int::div)
@JvmName("Intdiv") operator fun FiniteRandomVariable<Int>.div(other: Int) = this.binaryOperator(other, Int::div)
@JvmName("Intdiv") operator fun FiniteRandomVariable<Int>.div(other: Long) = this.binaryOperator(other, Int::div)
@JvmName("Intdiv") operator fun FiniteRandomVariable<Int>.div(other: Float) = this.binaryOperator(other, Int::div)
@JvmName("Intdiv") operator fun FiniteRandomVariable<Int>.div(other: Double) = this.binaryOperator(other, Int::div)
@JvmName("IntdivByte") operator fun FiniteRandomVariable<Int>.div(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Int::div)
@JvmName("IntdivShort") operator fun FiniteRandomVariable<Int>.div(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Int::div)
@JvmName("IntdivInt") operator fun FiniteRandomVariable<Int>.div(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::div)
@JvmName("IntdivLong") operator fun FiniteRandomVariable<Int>.div(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Int::div)
@JvmName("IntdivFloat") operator fun FiniteRandomVariable<Int>.div(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Int::div)
@JvmName("IntdivDouble") operator fun FiniteRandomVariable<Int>.div(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Int::div)
@JvmName("Intrem") operator fun FiniteRandomVariable<Int>.rem(other: Byte) = this.binaryOperator(other, Int::rem)
@JvmName("Intrem") operator fun FiniteRandomVariable<Int>.rem(other: Short) = this.binaryOperator(other, Int::rem)
@JvmName("Intrem") operator fun FiniteRandomVariable<Int>.rem(other: Int) = this.binaryOperator(other, Int::rem)
@JvmName("Intrem") operator fun FiniteRandomVariable<Int>.rem(other: Long) = this.binaryOperator(other, Int::rem)
@JvmName("Intrem") operator fun FiniteRandomVariable<Int>.rem(other: Float) = this.binaryOperator(other, Int::rem)
@JvmName("Intrem") operator fun FiniteRandomVariable<Int>.rem(other: Double) = this.binaryOperator(other, Int::rem)
@JvmName("IntremByte") operator fun FiniteRandomVariable<Int>.rem(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Int::rem)
@JvmName("IntremShort") operator fun FiniteRandomVariable<Int>.rem(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Int::rem)
@JvmName("IntremInt") operator fun FiniteRandomVariable<Int>.rem(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::rem)
@JvmName("IntremLong") operator fun FiniteRandomVariable<Int>.rem(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Int::rem)
@JvmName("IntremFloat") operator fun FiniteRandomVariable<Int>.rem(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Int::rem)
@JvmName("IntremDouble") operator fun FiniteRandomVariable<Int>.rem(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Int::rem)
@JvmName("IntrangeTo") operator fun FiniteRandomVariable<Int>.rangeTo(other: Byte) = this.binaryOperator(other, Int::rangeTo)
@JvmName("IntrangeTo") operator fun FiniteRandomVariable<Int>.rangeTo(other: Short) = this.binaryOperator(other, Int::rangeTo)
@JvmName("IntrangeTo") operator fun FiniteRandomVariable<Int>.rangeTo(other: Int) = this.binaryOperator(other, Int::rangeTo)
@JvmName("IntrangeTo") operator fun FiniteRandomVariable<Int>.rangeTo(other: Long) = this.binaryOperator(other, Int::rangeTo)
@JvmName("Intgt") infix fun FiniteRandomVariable<Int>.gt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Intgt") infix fun FiniteRandomVariable<Int>.gt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Intgt") infix fun FiniteRandomVariable<Int>.gt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Intgt") infix fun FiniteRandomVariable<Int>.gt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Intgt") infix fun FiniteRandomVariable<Int>.gt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Intgt") infix fun FiniteRandomVariable<Int>.gt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("IntgtByte") infix fun FiniteRandomVariable<Int>.gt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("IntgtShort") infix fun FiniteRandomVariable<Int>.gt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("IntgtInt") infix fun FiniteRandomVariable<Int>.gt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("IntgtLong") infix fun FiniteRandomVariable<Int>.gt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("IntgtFloat") infix fun FiniteRandomVariable<Int>.gt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("IntgtDouble") infix fun FiniteRandomVariable<Int>.gt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("Intge") infix fun FiniteRandomVariable<Int>.ge(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Intge") infix fun FiniteRandomVariable<Int>.ge(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Intge") infix fun FiniteRandomVariable<Int>.ge(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Intge") infix fun FiniteRandomVariable<Int>.ge(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Intge") infix fun FiniteRandomVariable<Int>.ge(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Intge") infix fun FiniteRandomVariable<Int>.ge(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("IntgeByte") infix fun FiniteRandomVariable<Int>.ge(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("IntgeShort") infix fun FiniteRandomVariable<Int>.ge(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("IntgeInt") infix fun FiniteRandomVariable<Int>.ge(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("IntgeLong") infix fun FiniteRandomVariable<Int>.ge(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("IntgeFloat") infix fun FiniteRandomVariable<Int>.ge(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("IntgeDouble") infix fun FiniteRandomVariable<Int>.ge(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("Inteq") infix fun FiniteRandomVariable<Int>.eq(other: Any?) = this.binaryOperator(other, Int::equals)
@JvmName("InteqAny") infix fun FiniteRandomVariable<Int>.eq(other: FiniteRandomVariable<Any?>) = this.binaryOperator(other, Int::equals)
@JvmName("Intle") infix fun FiniteRandomVariable<Int>.le(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Intle") infix fun FiniteRandomVariable<Int>.le(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Intle") infix fun FiniteRandomVariable<Int>.le(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Intle") infix fun FiniteRandomVariable<Int>.le(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Intle") infix fun FiniteRandomVariable<Int>.le(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Intle") infix fun FiniteRandomVariable<Int>.le(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("IntleByte") infix fun FiniteRandomVariable<Int>.le(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("IntleShort") infix fun FiniteRandomVariable<Int>.le(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("IntleInt") infix fun FiniteRandomVariable<Int>.le(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("IntleLong") infix fun FiniteRandomVariable<Int>.le(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("IntleFloat") infix fun FiniteRandomVariable<Int>.le(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("IntleDouble") infix fun FiniteRandomVariable<Int>.le(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("Intlt") infix fun FiniteRandomVariable<Int>.lt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Intlt") infix fun FiniteRandomVariable<Int>.lt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Intlt") infix fun FiniteRandomVariable<Int>.lt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Intlt") infix fun FiniteRandomVariable<Int>.lt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Intlt") infix fun FiniteRandomVariable<Int>.lt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Intlt") infix fun FiniteRandomVariable<Int>.lt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("IntltByte") infix fun FiniteRandomVariable<Int>.lt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("IntltShort") infix fun FiniteRandomVariable<Int>.lt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("IntltInt") infix fun FiniteRandomVariable<Int>.lt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("IntltLong") infix fun FiniteRandomVariable<Int>.lt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("IntltFloat") infix fun FiniteRandomVariable<Int>.lt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("IntltDouble") infix fun FiniteRandomVariable<Int>.lt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("Intand") infix fun FiniteRandomVariable<Int>.and(other: Int) = this.binaryOperator(other, Int::and)
@JvmName("IntandInt") infix fun FiniteRandomVariable<Int>.and(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::and)
@JvmName("Intor") infix fun FiniteRandomVariable<Int>.or(other: Int) = this.binaryOperator(other, Int::or)
@JvmName("IntorInt") infix fun FiniteRandomVariable<Int>.or(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::or)
@JvmName("Intxor") infix fun FiniteRandomVariable<Int>.xor(other: Int) = this.binaryOperator(other, Int::xor)
@JvmName("IntxorInt") infix fun FiniteRandomVariable<Int>.xor(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::xor)
@JvmName("Intshl") infix fun FiniteRandomVariable<Int>.shl(other: Int) = this.binaryOperator(other, Int::shl)
@JvmName("IntshlInt") infix fun FiniteRandomVariable<Int>.shl(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::shl)
@JvmName("Intshr") infix fun FiniteRandomVariable<Int>.shr(other: Int) = this.binaryOperator(other, Int::shr)
@JvmName("IntshrInt") infix fun FiniteRandomVariable<Int>.shr(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::shr)
@JvmName("Intushr") infix fun FiniteRandomVariable<Int>.ushr(other: Int) = this.binaryOperator(other, Int::ushr)
@JvmName("IntushrInt") infix fun FiniteRandomVariable<Int>.ushr(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Int::ushr)

// Long
@JvmName("LongunaryMinus") operator fun FiniteRandomVariable<Long>.unaryMinus() = this.unaryOperator(Long::unaryMinus)
@JvmName("LongunaryPlus") operator fun FiniteRandomVariable<Long>.unaryPlus() = this.unaryOperator(Long::unaryPlus)
@JvmName("Longinc") operator fun FiniteRandomVariable<Long>.inc() = this.unaryOperator(Long::inc)
@JvmName("Longdec") operator fun FiniteRandomVariable<Long>.dec() = this.unaryOperator(Long::inc)
@JvmName("Longplus") operator fun FiniteRandomVariable<Long>.plus(other: Byte) = this.binaryOperator(other, Long::plus)
@JvmName("Longplus") operator fun FiniteRandomVariable<Long>.plus(other: Short) = this.binaryOperator(other, Long::plus)
@JvmName("Longplus") operator fun FiniteRandomVariable<Long>.plus(other: Int) = this.binaryOperator(other, Long::plus)
@JvmName("Longplus") operator fun FiniteRandomVariable<Long>.plus(other: Long) = this.binaryOperator(other, Long::plus)
@JvmName("Longplus") operator fun FiniteRandomVariable<Long>.plus(other: Float) = this.binaryOperator(other, Long::plus)
@JvmName("Longplus") operator fun FiniteRandomVariable<Long>.plus(other: Double) = this.binaryOperator(other, Long::plus)
@JvmName("LongplusByte") operator fun FiniteRandomVariable<Long>.plus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Long::plus)
@JvmName("LongplusShort") operator fun FiniteRandomVariable<Long>.plus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Long::plus)
@JvmName("LongplusInt") operator fun FiniteRandomVariable<Long>.plus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Long::plus)
@JvmName("LongplusLong") operator fun FiniteRandomVariable<Long>.plus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Long::plus)
@JvmName("LongplusFloat") operator fun FiniteRandomVariable<Long>.plus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Long::plus)
@JvmName("LongplusDouble") operator fun FiniteRandomVariable<Long>.plus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Long::plus)
@JvmName("Longminus") operator fun FiniteRandomVariable<Long>.minus(other: Byte) = this.binaryOperator(other, Long::minus)
@JvmName("Longminus") operator fun FiniteRandomVariable<Long>.minus(other: Short) = this.binaryOperator(other, Long::minus)
@JvmName("Longminus") operator fun FiniteRandomVariable<Long>.minus(other: Int) = this.binaryOperator(other, Long::minus)
@JvmName("Longminus") operator fun FiniteRandomVariable<Long>.minus(other: Long) = this.binaryOperator(other, Long::minus)
@JvmName("Longminus") operator fun FiniteRandomVariable<Long>.minus(other: Float) = this.binaryOperator(other, Long::minus)
@JvmName("Longminus") operator fun FiniteRandomVariable<Long>.minus(other: Double) = this.binaryOperator(other, Long::minus)
@JvmName("LongminusByte") operator fun FiniteRandomVariable<Long>.minus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Long::minus)
@JvmName("LongminusShort") operator fun FiniteRandomVariable<Long>.minus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Long::minus)
@JvmName("LongminusInt") operator fun FiniteRandomVariable<Long>.minus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Long::minus)
@JvmName("LongminusLong") operator fun FiniteRandomVariable<Long>.minus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Long::minus)
@JvmName("LongminusFloat") operator fun FiniteRandomVariable<Long>.minus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Long::minus)
@JvmName("LongminusDouble") operator fun FiniteRandomVariable<Long>.minus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Long::minus)
@JvmName("Longtimes") operator fun FiniteRandomVariable<Long>.times(other: Byte) = this.binaryOperator(other, Long::times)
@JvmName("Longtimes") operator fun FiniteRandomVariable<Long>.times(other: Short) = this.binaryOperator(other, Long::times)
@JvmName("Longtimes") operator fun FiniteRandomVariable<Long>.times(other: Int) = this.binaryOperator(other, Long::times)
@JvmName("Longtimes") operator fun FiniteRandomVariable<Long>.times(other: Long) = this.binaryOperator(other, Long::times)
@JvmName("Longtimes") operator fun FiniteRandomVariable<Long>.times(other: Float) = this.binaryOperator(other, Long::times)
@JvmName("Longtimes") operator fun FiniteRandomVariable<Long>.times(other: Double) = this.binaryOperator(other, Long::times)
@JvmName("LongtimesByte") operator fun FiniteRandomVariable<Long>.times(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Long::times)
@JvmName("LongtimesShort") operator fun FiniteRandomVariable<Long>.times(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Long::times)
@JvmName("LongtimesInt") operator fun FiniteRandomVariable<Long>.times(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Long::times)
@JvmName("LongtimesLong") operator fun FiniteRandomVariable<Long>.times(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Long::times)
@JvmName("LongtimesFloat") operator fun FiniteRandomVariable<Long>.times(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Long::times)
@JvmName("LongtimesDouble") operator fun FiniteRandomVariable<Long>.times(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Long::times)
@JvmName("Longdiv") operator fun FiniteRandomVariable<Long>.div(other: Byte) = this.binaryOperator(other, Long::div)
@JvmName("Longdiv") operator fun FiniteRandomVariable<Long>.div(other: Short) = this.binaryOperator(other, Long::div)
@JvmName("Longdiv") operator fun FiniteRandomVariable<Long>.div(other: Int) = this.binaryOperator(other, Long::div)
@JvmName("Longdiv") operator fun FiniteRandomVariable<Long>.div(other: Long) = this.binaryOperator(other, Long::div)
@JvmName("Longdiv") operator fun FiniteRandomVariable<Long>.div(other: Float) = this.binaryOperator(other, Long::div)
@JvmName("Longdiv") operator fun FiniteRandomVariable<Long>.div(other: Double) = this.binaryOperator(other, Long::div)
@JvmName("LongdivByte") operator fun FiniteRandomVariable<Long>.div(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Long::div)
@JvmName("LongdivShort") operator fun FiniteRandomVariable<Long>.div(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Long::div)
@JvmName("LongdivInt") operator fun FiniteRandomVariable<Long>.div(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Long::div)
@JvmName("LongdivLong") operator fun FiniteRandomVariable<Long>.div(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Long::div)
@JvmName("LongdivFloat") operator fun FiniteRandomVariable<Long>.div(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Long::div)
@JvmName("LongdivDouble") operator fun FiniteRandomVariable<Long>.div(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Long::div)
@JvmName("Longrem") operator fun FiniteRandomVariable<Long>.rem(other: Byte) = this.binaryOperator(other, Long::rem)
@JvmName("Longrem") operator fun FiniteRandomVariable<Long>.rem(other: Short) = this.binaryOperator(other, Long::rem)
@JvmName("Longrem") operator fun FiniteRandomVariable<Long>.rem(other: Int) = this.binaryOperator(other, Long::rem)
@JvmName("Longrem") operator fun FiniteRandomVariable<Long>.rem(other: Long) = this.binaryOperator(other, Long::rem)
@JvmName("Longrem") operator fun FiniteRandomVariable<Long>.rem(other: Float) = this.binaryOperator(other, Long::rem)
@JvmName("Longrem") operator fun FiniteRandomVariable<Long>.rem(other: Double) = this.binaryOperator(other, Long::rem)
@JvmName("LongremByte") operator fun FiniteRandomVariable<Long>.rem(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Long::rem)
@JvmName("LongremShort") operator fun FiniteRandomVariable<Long>.rem(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Long::rem)
@JvmName("LongremInt") operator fun FiniteRandomVariable<Long>.rem(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Long::rem)
@JvmName("LongremLong") operator fun FiniteRandomVariable<Long>.rem(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Long::rem)
@JvmName("LongremFloat") operator fun FiniteRandomVariable<Long>.rem(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Long::rem)
@JvmName("LongremDouble") operator fun FiniteRandomVariable<Long>.rem(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Long::rem)
@JvmName("LongrangeTo") operator fun FiniteRandomVariable<Long>.rangeTo(other: Byte) = this.binaryOperator(other, Long::rangeTo)
@JvmName("LongrangeTo") operator fun FiniteRandomVariable<Long>.rangeTo(other: Short) = this.binaryOperator(other, Long::rangeTo)
@JvmName("LongrangeTo") operator fun FiniteRandomVariable<Long>.rangeTo(other: Int) = this.binaryOperator(other, Long::rangeTo)
@JvmName("LongrangeTo") operator fun FiniteRandomVariable<Long>.rangeTo(other: Long) = this.binaryOperator(other, Long::rangeTo)
@JvmName("Longgt") infix fun FiniteRandomVariable<Long>.gt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Longgt") infix fun FiniteRandomVariable<Long>.gt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Longgt") infix fun FiniteRandomVariable<Long>.gt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Longgt") infix fun FiniteRandomVariable<Long>.gt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Longgt") infix fun FiniteRandomVariable<Long>.gt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Longgt") infix fun FiniteRandomVariable<Long>.gt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("LonggtByte") infix fun FiniteRandomVariable<Long>.gt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("LonggtShort") infix fun FiniteRandomVariable<Long>.gt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("LonggtInt") infix fun FiniteRandomVariable<Long>.gt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("LonggtLong") infix fun FiniteRandomVariable<Long>.gt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("LonggtFloat") infix fun FiniteRandomVariable<Long>.gt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("LonggtDouble") infix fun FiniteRandomVariable<Long>.gt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("Longge") infix fun FiniteRandomVariable<Long>.ge(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Longge") infix fun FiniteRandomVariable<Long>.ge(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Longge") infix fun FiniteRandomVariable<Long>.ge(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Longge") infix fun FiniteRandomVariable<Long>.ge(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Longge") infix fun FiniteRandomVariable<Long>.ge(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Longge") infix fun FiniteRandomVariable<Long>.ge(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("LonggeByte") infix fun FiniteRandomVariable<Long>.ge(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("LonggeShort") infix fun FiniteRandomVariable<Long>.ge(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("LonggeInt") infix fun FiniteRandomVariable<Long>.ge(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("LonggeLong") infix fun FiniteRandomVariable<Long>.ge(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("LonggeFloat") infix fun FiniteRandomVariable<Long>.ge(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("LonggeDouble") infix fun FiniteRandomVariable<Long>.ge(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("Longeq") infix fun FiniteRandomVariable<Long>.eq(other: Any?) = this.binaryOperator(other, Long::equals)
@JvmName("LongeqAny") infix fun FiniteRandomVariable<Long>.eq(other: FiniteRandomVariable<Any?>) = this.binaryOperator(other, Long::equals)
@JvmName("Longle") infix fun FiniteRandomVariable<Long>.le(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Longle") infix fun FiniteRandomVariable<Long>.le(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Longle") infix fun FiniteRandomVariable<Long>.le(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Longle") infix fun FiniteRandomVariable<Long>.le(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Longle") infix fun FiniteRandomVariable<Long>.le(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Longle") infix fun FiniteRandomVariable<Long>.le(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("LongleByte") infix fun FiniteRandomVariable<Long>.le(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("LongleShort") infix fun FiniteRandomVariable<Long>.le(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("LongleInt") infix fun FiniteRandomVariable<Long>.le(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("LongleLong") infix fun FiniteRandomVariable<Long>.le(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("LongleFloat") infix fun FiniteRandomVariable<Long>.le(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("LongleDouble") infix fun FiniteRandomVariable<Long>.le(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("Longlt") infix fun FiniteRandomVariable<Long>.lt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Longlt") infix fun FiniteRandomVariable<Long>.lt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Longlt") infix fun FiniteRandomVariable<Long>.lt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Longlt") infix fun FiniteRandomVariable<Long>.lt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Longlt") infix fun FiniteRandomVariable<Long>.lt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Longlt") infix fun FiniteRandomVariable<Long>.lt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("LongltByte") infix fun FiniteRandomVariable<Long>.lt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("LongltShort") infix fun FiniteRandomVariable<Long>.lt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("LongltInt") infix fun FiniteRandomVariable<Long>.lt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("LongltLong") infix fun FiniteRandomVariable<Long>.lt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("LongltFloat") infix fun FiniteRandomVariable<Long>.lt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("LongltDouble") infix fun FiniteRandomVariable<Long>.lt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("Longand") infix fun FiniteRandomVariable<Long>.and(other: Long) = this.binaryOperator(other, Long::and)
@JvmName("LongandLong") infix fun FiniteRandomVariable<Long>.and(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Long::and)
@JvmName("Longor") infix fun FiniteRandomVariable<Long>.or(other: Long) = this.binaryOperator(other, Long::or)
@JvmName("LongorLong") infix fun FiniteRandomVariable<Long>.or(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Long::or)
@JvmName("Longxor") infix fun FiniteRandomVariable<Long>.xor(other: Long) = this.binaryOperator(other, Long::xor)
@JvmName("LongxorLong") infix fun FiniteRandomVariable<Long>.xor(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Long::xor)
@JvmName("Longshl") infix fun FiniteRandomVariable<Long>.shl(other: Int) = this.binaryOperator(other, Long::shl)
@JvmName("LongshlInt") infix fun FiniteRandomVariable<Long>.shl(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Long::shl)
@JvmName("Longshr") infix fun FiniteRandomVariable<Long>.shr(other: Int) = this.binaryOperator(other, Long::shr)
@JvmName("LongshrInt") infix fun FiniteRandomVariable<Long>.shr(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Long::shr)
@JvmName("Longushr") infix fun FiniteRandomVariable<Long>.ushr(other: Int) = this.binaryOperator(other, Long::ushr)
@JvmName("LongushrInt") infix fun FiniteRandomVariable<Long>.ushr(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Long::ushr)

// Float
@JvmName("FloatunaryMinus") operator fun FiniteRandomVariable<Float>.unaryMinus() = this.unaryOperator(Float::unaryMinus)
@JvmName("FloatunaryPlus") operator fun FiniteRandomVariable<Float>.unaryPlus() = this.unaryOperator(Float::unaryPlus)
@JvmName("Floatinc") operator fun FiniteRandomVariable<Float>.inc() = this.unaryOperator(Float::inc)
@JvmName("Floatdec") operator fun FiniteRandomVariable<Float>.dec() = this.unaryOperator(Float::inc)
@JvmName("Floatplus") operator fun FiniteRandomVariable<Float>.plus(other: Byte) = this.binaryOperator(other, Float::plus)
@JvmName("Floatplus") operator fun FiniteRandomVariable<Float>.plus(other: Short) = this.binaryOperator(other, Float::plus)
@JvmName("Floatplus") operator fun FiniteRandomVariable<Float>.plus(other: Int) = this.binaryOperator(other, Float::plus)
@JvmName("Floatplus") operator fun FiniteRandomVariable<Float>.plus(other: Long) = this.binaryOperator(other, Float::plus)
@JvmName("Floatplus") operator fun FiniteRandomVariable<Float>.plus(other: Float) = this.binaryOperator(other, Float::plus)
@JvmName("Floatplus") operator fun FiniteRandomVariable<Float>.plus(other: Double) = this.binaryOperator(other, Float::plus)
@JvmName("FloatplusByte") operator fun FiniteRandomVariable<Float>.plus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Float::plus)
@JvmName("FloatplusShort") operator fun FiniteRandomVariable<Float>.plus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Float::plus)
@JvmName("FloatplusInt") operator fun FiniteRandomVariable<Float>.plus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Float::plus)
@JvmName("FloatplusLong") operator fun FiniteRandomVariable<Float>.plus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Float::plus)
@JvmName("FloatplusFloat") operator fun FiniteRandomVariable<Float>.plus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Float::plus)
@JvmName("FloatplusDouble") operator fun FiniteRandomVariable<Float>.plus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Float::plus)
@JvmName("Floatminus") operator fun FiniteRandomVariable<Float>.minus(other: Byte) = this.binaryOperator(other, Float::minus)
@JvmName("Floatminus") operator fun FiniteRandomVariable<Float>.minus(other: Short) = this.binaryOperator(other, Float::minus)
@JvmName("Floatminus") operator fun FiniteRandomVariable<Float>.minus(other: Int) = this.binaryOperator(other, Float::minus)
@JvmName("Floatminus") operator fun FiniteRandomVariable<Float>.minus(other: Long) = this.binaryOperator(other, Float::minus)
@JvmName("Floatminus") operator fun FiniteRandomVariable<Float>.minus(other: Float) = this.binaryOperator(other, Float::minus)
@JvmName("Floatminus") operator fun FiniteRandomVariable<Float>.minus(other: Double) = this.binaryOperator(other, Float::minus)
@JvmName("FloatminusByte") operator fun FiniteRandomVariable<Float>.minus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Float::minus)
@JvmName("FloatminusShort") operator fun FiniteRandomVariable<Float>.minus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Float::minus)
@JvmName("FloatminusInt") operator fun FiniteRandomVariable<Float>.minus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Float::minus)
@JvmName("FloatminusLong") operator fun FiniteRandomVariable<Float>.minus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Float::minus)
@JvmName("FloatminusFloat") operator fun FiniteRandomVariable<Float>.minus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Float::minus)
@JvmName("FloatminusDouble") operator fun FiniteRandomVariable<Float>.minus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Float::minus)
@JvmName("Floattimes") operator fun FiniteRandomVariable<Float>.times(other: Byte) = this.binaryOperator(other, Float::times)
@JvmName("Floattimes") operator fun FiniteRandomVariable<Float>.times(other: Short) = this.binaryOperator(other, Float::times)
@JvmName("Floattimes") operator fun FiniteRandomVariable<Float>.times(other: Int) = this.binaryOperator(other, Float::times)
@JvmName("Floattimes") operator fun FiniteRandomVariable<Float>.times(other: Long) = this.binaryOperator(other, Float::times)
@JvmName("Floattimes") operator fun FiniteRandomVariable<Float>.times(other: Float) = this.binaryOperator(other, Float::times)
@JvmName("Floattimes") operator fun FiniteRandomVariable<Float>.times(other: Double) = this.binaryOperator(other, Float::times)
@JvmName("FloattimesByte") operator fun FiniteRandomVariable<Float>.times(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Float::times)
@JvmName("FloattimesShort") operator fun FiniteRandomVariable<Float>.times(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Float::times)
@JvmName("FloattimesInt") operator fun FiniteRandomVariable<Float>.times(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Float::times)
@JvmName("FloattimesLong") operator fun FiniteRandomVariable<Float>.times(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Float::times)
@JvmName("FloattimesFloat") operator fun FiniteRandomVariable<Float>.times(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Float::times)
@JvmName("FloattimesDouble") operator fun FiniteRandomVariable<Float>.times(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Float::times)
@JvmName("Floatdiv") operator fun FiniteRandomVariable<Float>.div(other: Byte) = this.binaryOperator(other, Float::div)
@JvmName("Floatdiv") operator fun FiniteRandomVariable<Float>.div(other: Short) = this.binaryOperator(other, Float::div)
@JvmName("Floatdiv") operator fun FiniteRandomVariable<Float>.div(other: Int) = this.binaryOperator(other, Float::div)
@JvmName("Floatdiv") operator fun FiniteRandomVariable<Float>.div(other: Long) = this.binaryOperator(other, Float::div)
@JvmName("Floatdiv") operator fun FiniteRandomVariable<Float>.div(other: Float) = this.binaryOperator(other, Float::div)
@JvmName("Floatdiv") operator fun FiniteRandomVariable<Float>.div(other: Double) = this.binaryOperator(other, Float::div)
@JvmName("FloatdivByte") operator fun FiniteRandomVariable<Float>.div(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Float::div)
@JvmName("FloatdivShort") operator fun FiniteRandomVariable<Float>.div(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Float::div)
@JvmName("FloatdivInt") operator fun FiniteRandomVariable<Float>.div(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Float::div)
@JvmName("FloatdivLong") operator fun FiniteRandomVariable<Float>.div(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Float::div)
@JvmName("FloatdivFloat") operator fun FiniteRandomVariable<Float>.div(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Float::div)
@JvmName("FloatdivDouble") operator fun FiniteRandomVariable<Float>.div(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Float::div)
@JvmName("Floatrem") operator fun FiniteRandomVariable<Float>.rem(other: Byte) = this.binaryOperator(other, Float::rem)
@JvmName("Floatrem") operator fun FiniteRandomVariable<Float>.rem(other: Short) = this.binaryOperator(other, Float::rem)
@JvmName("Floatrem") operator fun FiniteRandomVariable<Float>.rem(other: Int) = this.binaryOperator(other, Float::rem)
@JvmName("Floatrem") operator fun FiniteRandomVariable<Float>.rem(other: Long) = this.binaryOperator(other, Float::rem)
@JvmName("Floatrem") operator fun FiniteRandomVariable<Float>.rem(other: Float) = this.binaryOperator(other, Float::rem)
@JvmName("Floatrem") operator fun FiniteRandomVariable<Float>.rem(other: Double) = this.binaryOperator(other, Float::rem)
@JvmName("FloatremByte") operator fun FiniteRandomVariable<Float>.rem(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Float::rem)
@JvmName("FloatremShort") operator fun FiniteRandomVariable<Float>.rem(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Float::rem)
@JvmName("FloatremInt") operator fun FiniteRandomVariable<Float>.rem(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Float::rem)
@JvmName("FloatremLong") operator fun FiniteRandomVariable<Float>.rem(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Float::rem)
@JvmName("FloatremFloat") operator fun FiniteRandomVariable<Float>.rem(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Float::rem)
@JvmName("FloatremDouble") operator fun FiniteRandomVariable<Float>.rem(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Float::rem)
@JvmName("Floatgt") infix fun FiniteRandomVariable<Float>.gt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Floatgt") infix fun FiniteRandomVariable<Float>.gt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Floatgt") infix fun FiniteRandomVariable<Float>.gt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Floatgt") infix fun FiniteRandomVariable<Float>.gt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Floatgt") infix fun FiniteRandomVariable<Float>.gt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Floatgt") infix fun FiniteRandomVariable<Float>.gt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("FloatgtByte") infix fun FiniteRandomVariable<Float>.gt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("FloatgtShort") infix fun FiniteRandomVariable<Float>.gt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("FloatgtInt") infix fun FiniteRandomVariable<Float>.gt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("FloatgtLong") infix fun FiniteRandomVariable<Float>.gt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("FloatgtFloat") infix fun FiniteRandomVariable<Float>.gt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("FloatgtDouble") infix fun FiniteRandomVariable<Float>.gt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("Floatge") infix fun FiniteRandomVariable<Float>.ge(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Floatge") infix fun FiniteRandomVariable<Float>.ge(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Floatge") infix fun FiniteRandomVariable<Float>.ge(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Floatge") infix fun FiniteRandomVariable<Float>.ge(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Floatge") infix fun FiniteRandomVariable<Float>.ge(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Floatge") infix fun FiniteRandomVariable<Float>.ge(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("FloatgeByte") infix fun FiniteRandomVariable<Float>.ge(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("FloatgeShort") infix fun FiniteRandomVariable<Float>.ge(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("FloatgeInt") infix fun FiniteRandomVariable<Float>.ge(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("FloatgeLong") infix fun FiniteRandomVariable<Float>.ge(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("FloatgeFloat") infix fun FiniteRandomVariable<Float>.ge(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("FloatgeDouble") infix fun FiniteRandomVariable<Float>.ge(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("Floateq") infix fun FiniteRandomVariable<Float>.eq(other: Any?) = this.binaryOperator(other, Float::equals)
@JvmName("FloateqAny") infix fun FiniteRandomVariable<Float>.eq(other: FiniteRandomVariable<Any?>) = this.binaryOperator(other, Float::equals)
@JvmName("Floatle") infix fun FiniteRandomVariable<Float>.le(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Floatle") infix fun FiniteRandomVariable<Float>.le(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Floatle") infix fun FiniteRandomVariable<Float>.le(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Floatle") infix fun FiniteRandomVariable<Float>.le(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Floatle") infix fun FiniteRandomVariable<Float>.le(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Floatle") infix fun FiniteRandomVariable<Float>.le(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("FloatleByte") infix fun FiniteRandomVariable<Float>.le(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("FloatleShort") infix fun FiniteRandomVariable<Float>.le(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("FloatleInt") infix fun FiniteRandomVariable<Float>.le(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("FloatleLong") infix fun FiniteRandomVariable<Float>.le(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("FloatleFloat") infix fun FiniteRandomVariable<Float>.le(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("FloatleDouble") infix fun FiniteRandomVariable<Float>.le(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("Floatlt") infix fun FiniteRandomVariable<Float>.lt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Floatlt") infix fun FiniteRandomVariable<Float>.lt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Floatlt") infix fun FiniteRandomVariable<Float>.lt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Floatlt") infix fun FiniteRandomVariable<Float>.lt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Floatlt") infix fun FiniteRandomVariable<Float>.lt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Floatlt") infix fun FiniteRandomVariable<Float>.lt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("FloatltByte") infix fun FiniteRandomVariable<Float>.lt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("FloatltShort") infix fun FiniteRandomVariable<Float>.lt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("FloatltInt") infix fun FiniteRandomVariable<Float>.lt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("FloatltLong") infix fun FiniteRandomVariable<Float>.lt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("FloatltFloat") infix fun FiniteRandomVariable<Float>.lt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("FloatltDouble") infix fun FiniteRandomVariable<Float>.lt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }

// Double
@JvmName("DoubleunaryMinus") operator fun FiniteRandomVariable<Double>.unaryMinus() = this.unaryOperator(Double::unaryMinus)
@JvmName("DoubleunaryPlus") operator fun FiniteRandomVariable<Double>.unaryPlus() = this.unaryOperator(Double::unaryPlus)
@JvmName("Doubleinc") operator fun FiniteRandomVariable<Double>.inc() = this.unaryOperator(Double::inc)
@JvmName("Doubledec") operator fun FiniteRandomVariable<Double>.dec() = this.unaryOperator(Double::inc)
@JvmName("Doubleplus") operator fun FiniteRandomVariable<Double>.plus(other: Byte) = this.binaryOperator(other, Double::plus)
@JvmName("Doubleplus") operator fun FiniteRandomVariable<Double>.plus(other: Short) = this.binaryOperator(other, Double::plus)
@JvmName("Doubleplus") operator fun FiniteRandomVariable<Double>.plus(other: Int) = this.binaryOperator(other, Double::plus)
@JvmName("Doubleplus") operator fun FiniteRandomVariable<Double>.plus(other: Long) = this.binaryOperator(other, Double::plus)
@JvmName("Doubleplus") operator fun FiniteRandomVariable<Double>.plus(other: Float) = this.binaryOperator(other, Double::plus)
@JvmName("Doubleplus") operator fun FiniteRandomVariable<Double>.plus(other: Double) = this.binaryOperator(other, Double::plus)
@JvmName("DoubleplusByte") operator fun FiniteRandomVariable<Double>.plus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Double::plus)
@JvmName("DoubleplusShort") operator fun FiniteRandomVariable<Double>.plus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Double::plus)
@JvmName("DoubleplusInt") operator fun FiniteRandomVariable<Double>.plus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Double::plus)
@JvmName("DoubleplusLong") operator fun FiniteRandomVariable<Double>.plus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Double::plus)
@JvmName("DoubleplusFloat") operator fun FiniteRandomVariable<Double>.plus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Double::plus)
@JvmName("DoubleplusDouble") operator fun FiniteRandomVariable<Double>.plus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Double::plus)
@JvmName("Doubleminus") operator fun FiniteRandomVariable<Double>.minus(other: Byte) = this.binaryOperator(other, Double::minus)
@JvmName("Doubleminus") operator fun FiniteRandomVariable<Double>.minus(other: Short) = this.binaryOperator(other, Double::minus)
@JvmName("Doubleminus") operator fun FiniteRandomVariable<Double>.minus(other: Int) = this.binaryOperator(other, Double::minus)
@JvmName("Doubleminus") operator fun FiniteRandomVariable<Double>.minus(other: Long) = this.binaryOperator(other, Double::minus)
@JvmName("Doubleminus") operator fun FiniteRandomVariable<Double>.minus(other: Float) = this.binaryOperator(other, Double::minus)
@JvmName("Doubleminus") operator fun FiniteRandomVariable<Double>.minus(other: Double) = this.binaryOperator(other, Double::minus)
@JvmName("DoubleminusByte") operator fun FiniteRandomVariable<Double>.minus(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Double::minus)
@JvmName("DoubleminusShort") operator fun FiniteRandomVariable<Double>.minus(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Double::minus)
@JvmName("DoubleminusInt") operator fun FiniteRandomVariable<Double>.minus(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Double::minus)
@JvmName("DoubleminusLong") operator fun FiniteRandomVariable<Double>.minus(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Double::minus)
@JvmName("DoubleminusFloat") operator fun FiniteRandomVariable<Double>.minus(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Double::minus)
@JvmName("DoubleminusDouble") operator fun FiniteRandomVariable<Double>.minus(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Double::minus)
@JvmName("Doubletimes") operator fun FiniteRandomVariable<Double>.times(other: Byte) = this.binaryOperator(other, Double::times)
@JvmName("Doubletimes") operator fun FiniteRandomVariable<Double>.times(other: Short) = this.binaryOperator(other, Double::times)
@JvmName("Doubletimes") operator fun FiniteRandomVariable<Double>.times(other: Int) = this.binaryOperator(other, Double::times)
@JvmName("Doubletimes") operator fun FiniteRandomVariable<Double>.times(other: Long) = this.binaryOperator(other, Double::times)
@JvmName("Doubletimes") operator fun FiniteRandomVariable<Double>.times(other: Float) = this.binaryOperator(other, Double::times)
@JvmName("Doubletimes") operator fun FiniteRandomVariable<Double>.times(other: Double) = this.binaryOperator(other, Double::times)
@JvmName("DoubletimesByte") operator fun FiniteRandomVariable<Double>.times(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Double::times)
@JvmName("DoubletimesShort") operator fun FiniteRandomVariable<Double>.times(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Double::times)
@JvmName("DoubletimesInt") operator fun FiniteRandomVariable<Double>.times(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Double::times)
@JvmName("DoubletimesLong") operator fun FiniteRandomVariable<Double>.times(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Double::times)
@JvmName("DoubletimesFloat") operator fun FiniteRandomVariable<Double>.times(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Double::times)
@JvmName("DoubletimesDouble") operator fun FiniteRandomVariable<Double>.times(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Double::times)
@JvmName("Doublediv") operator fun FiniteRandomVariable<Double>.div(other: Byte) = this.binaryOperator(other, Double::div)
@JvmName("Doublediv") operator fun FiniteRandomVariable<Double>.div(other: Short) = this.binaryOperator(other, Double::div)
@JvmName("Doublediv") operator fun FiniteRandomVariable<Double>.div(other: Int) = this.binaryOperator(other, Double::div)
@JvmName("Doublediv") operator fun FiniteRandomVariable<Double>.div(other: Long) = this.binaryOperator(other, Double::div)
@JvmName("Doublediv") operator fun FiniteRandomVariable<Double>.div(other: Float) = this.binaryOperator(other, Double::div)
@JvmName("Doublediv") operator fun FiniteRandomVariable<Double>.div(other: Double) = this.binaryOperator(other, Double::div)
@JvmName("DoubledivByte") operator fun FiniteRandomVariable<Double>.div(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Double::div)
@JvmName("DoubledivShort") operator fun FiniteRandomVariable<Double>.div(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Double::div)
@JvmName("DoubledivInt") operator fun FiniteRandomVariable<Double>.div(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Double::div)
@JvmName("DoubledivLong") operator fun FiniteRandomVariable<Double>.div(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Double::div)
@JvmName("DoubledivFloat") operator fun FiniteRandomVariable<Double>.div(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Double::div)
@JvmName("DoubledivDouble") operator fun FiniteRandomVariable<Double>.div(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Double::div)
@JvmName("Doublerem") operator fun FiniteRandomVariable<Double>.rem(other: Byte) = this.binaryOperator(other, Double::rem)
@JvmName("Doublerem") operator fun FiniteRandomVariable<Double>.rem(other: Short) = this.binaryOperator(other, Double::rem)
@JvmName("Doublerem") operator fun FiniteRandomVariable<Double>.rem(other: Int) = this.binaryOperator(other, Double::rem)
@JvmName("Doublerem") operator fun FiniteRandomVariable<Double>.rem(other: Long) = this.binaryOperator(other, Double::rem)
@JvmName("Doublerem") operator fun FiniteRandomVariable<Double>.rem(other: Float) = this.binaryOperator(other, Double::rem)
@JvmName("Doublerem") operator fun FiniteRandomVariable<Double>.rem(other: Double) = this.binaryOperator(other, Double::rem)
@JvmName("DoubleremByte") operator fun FiniteRandomVariable<Double>.rem(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other, Double::rem)
@JvmName("DoubleremShort") operator fun FiniteRandomVariable<Double>.rem(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other, Double::rem)
@JvmName("DoubleremInt") operator fun FiniteRandomVariable<Double>.rem(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other, Double::rem)
@JvmName("DoubleremLong") operator fun FiniteRandomVariable<Double>.rem(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other, Double::rem)
@JvmName("DoubleremFloat") operator fun FiniteRandomVariable<Double>.rem(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other, Double::rem)
@JvmName("DoubleremDouble") operator fun FiniteRandomVariable<Double>.rem(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other, Double::rem)
@JvmName("Doublegt") infix fun FiniteRandomVariable<Double>.gt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Doublegt") infix fun FiniteRandomVariable<Double>.gt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Doublegt") infix fun FiniteRandomVariable<Double>.gt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Doublegt") infix fun FiniteRandomVariable<Double>.gt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Doublegt") infix fun FiniteRandomVariable<Double>.gt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("Doublegt") infix fun FiniteRandomVariable<Double>.gt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("DoublegtByte") infix fun FiniteRandomVariable<Double>.gt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("DoublegtShort") infix fun FiniteRandomVariable<Double>.gt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("DoublegtInt") infix fun FiniteRandomVariable<Double>.gt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("DoublegtLong") infix fun FiniteRandomVariable<Double>.gt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("DoublegtFloat") infix fun FiniteRandomVariable<Double>.gt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("DoublegtDouble") infix fun FiniteRandomVariable<Double>.gt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("Doublege") infix fun FiniteRandomVariable<Double>.ge(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Doublege") infix fun FiniteRandomVariable<Double>.ge(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Doublege") infix fun FiniteRandomVariable<Double>.ge(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Doublege") infix fun FiniteRandomVariable<Double>.ge(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Doublege") infix fun FiniteRandomVariable<Double>.ge(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("Doublege") infix fun FiniteRandomVariable<Double>.ge(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("DoublegeByte") infix fun FiniteRandomVariable<Double>.ge(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("DoublegeShort") infix fun FiniteRandomVariable<Double>.ge(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("DoublegeInt") infix fun FiniteRandomVariable<Double>.ge(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("DoublegeLong") infix fun FiniteRandomVariable<Double>.ge(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("DoublegeFloat") infix fun FiniteRandomVariable<Double>.ge(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("DoublegeDouble") infix fun FiniteRandomVariable<Double>.ge(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("Doubleeq") infix fun FiniteRandomVariable<Double>.eq(other: Any?) = this.binaryOperator(other, Double::equals)
@JvmName("DoubleeqAny") infix fun FiniteRandomVariable<Double>.eq(other: FiniteRandomVariable<Any?>) = this.binaryOperator(other, Double::equals)
@JvmName("Doublele") infix fun FiniteRandomVariable<Double>.le(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Doublele") infix fun FiniteRandomVariable<Double>.le(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Doublele") infix fun FiniteRandomVariable<Double>.le(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Doublele") infix fun FiniteRandomVariable<Double>.le(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Doublele") infix fun FiniteRandomVariable<Double>.le(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("Doublele") infix fun FiniteRandomVariable<Double>.le(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("DoubleleByte") infix fun FiniteRandomVariable<Double>.le(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("DoubleleShort") infix fun FiniteRandomVariable<Double>.le(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("DoubleleInt") infix fun FiniteRandomVariable<Double>.le(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("DoubleleLong") infix fun FiniteRandomVariable<Double>.le(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("DoubleleFloat") infix fun FiniteRandomVariable<Double>.le(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("DoubleleDouble") infix fun FiniteRandomVariable<Double>.le(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("Doublelt") infix fun FiniteRandomVariable<Double>.lt(other: Byte) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Doublelt") infix fun FiniteRandomVariable<Double>.lt(other: Short) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Doublelt") infix fun FiniteRandomVariable<Double>.lt(other: Int) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Doublelt") infix fun FiniteRandomVariable<Double>.lt(other: Long) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Doublelt") infix fun FiniteRandomVariable<Double>.lt(other: Float) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("Doublelt") infix fun FiniteRandomVariable<Double>.lt(other: Double) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("DoubleltByte") infix fun FiniteRandomVariable<Double>.lt(other: FiniteRandomVariable<Byte>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("DoubleltShort") infix fun FiniteRandomVariable<Double>.lt(other: FiniteRandomVariable<Short>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("DoubleltInt") infix fun FiniteRandomVariable<Double>.lt(other: FiniteRandomVariable<Int>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("DoubleltLong") infix fun FiniteRandomVariable<Double>.lt(other: FiniteRandomVariable<Long>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("DoubleltFloat") infix fun FiniteRandomVariable<Double>.lt(other: FiniteRandomVariable<Float>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }
@JvmName("DoubleltDouble") infix fun FiniteRandomVariable<Double>.lt(other: FiniteRandomVariable<Double>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }

// Boolean
@JvmName("Booleannot") operator fun FiniteRandomVariable<Boolean>.not() = this.unaryOperator(Boolean::not)
@JvmName("Booleanand") infix fun FiniteRandomVariable<Boolean>.and(other: Boolean) = this.binaryOperator(other, Boolean::and)
@JvmName("BooleanandBoolean") infix fun FiniteRandomVariable<Boolean>.and(other: FiniteRandomVariable<Boolean>) = this.binaryOperatorCombine(other, Boolean::and)
@JvmName("Booleanor") infix fun FiniteRandomVariable<Boolean>.or(other: Boolean) = this.binaryOperator(other, Boolean::or)
@JvmName("BooleanorBoolean") infix fun FiniteRandomVariable<Boolean>.or(other: FiniteRandomVariable<Boolean>) = this.binaryOperatorCombine(other, Boolean::or)
@JvmName("Booleanxor") infix fun FiniteRandomVariable<Boolean>.xor(other: Boolean) = this.binaryOperator(other, Boolean::xor)
@JvmName("BooleanxorBoolean") infix fun FiniteRandomVariable<Boolean>.xor(other: FiniteRandomVariable<Boolean>) = this.binaryOperatorCombine(other, Boolean::xor)

// Char
// TODO - who uses Chars?

// String
@JvmName("Stringplus") operator fun FiniteRandomVariable<String>.plus(other: Any?) = this.binaryOperator(other, String::plus)
@JvmName("StringplusAny") operator fun FiniteRandomVariable<String>.plus(other: FiniteRandomVariable<Any?>) = this.binaryOperatorCombine(other, String::plus)
@JvmName("Stringgt") infix fun FiniteRandomVariable<String>.gt(other: String) = this.binaryOperator(other) { v1, v2 -> v1 > v2 }
@JvmName("StringgtString") infix fun FiniteRandomVariable<String>.gt(other: FiniteRandomVariable<String>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 > v2 }
@JvmName("Stringge") infix fun FiniteRandomVariable<String>.ge(other: String) = this.binaryOperator(other) { v1, v2 -> v1 >= v2 }
@JvmName("StringgeString") infix fun FiniteRandomVariable<String>.ge(other: FiniteRandomVariable<String>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 >= v2 }
@JvmName("Stringeq") infix fun FiniteRandomVariable<String>.eq(other: Any?) = this.binaryOperator(other, String::equals)
@JvmName("StringeqAny") infix fun FiniteRandomVariable<String>.eq(other: FiniteRandomVariable<Any?>) = this.binaryOperator(other, String::equals)
@JvmName("Stringle") infix fun FiniteRandomVariable<String>.le(other: String) = this.binaryOperator(other) { v1, v2 -> v1 <= v2 }
@JvmName("StringleString") infix fun FiniteRandomVariable<String>.le(other: FiniteRandomVariable<String>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 <= v2 }
@JvmName("Stringlt") infix fun FiniteRandomVariable<String>.lt(other: String) = this.binaryOperator(other) { v1, v2 -> v1 < v2 }
@JvmName("StringltString") infix fun FiniteRandomVariable<String>.lt(other: FiniteRandomVariable<String>) = this.binaryOperatorCombine(other) { v1, v2 -> v1 < v2 }

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
