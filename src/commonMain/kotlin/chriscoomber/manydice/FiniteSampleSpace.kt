package chriscoomber.manydice

import kotlin.random.Random
import com.benasher44.uuid.uuid4

/**
 * A [SampleSpace] that contains only finitely many outcomes.
 *
 * To make finite sample spaces easier to code, we make the following
 * simplifying assumptions:
 *
 * - The set of any discrete sample space has a bijection with {1,...,n}
 * for some n. Therefore, when introducing any new sample space that
 * is disjoint to all others, we assume it is of this form. We call these
 * [PrimitiveFiniteSampleSpace]s.
 *
 * - Since we don't know up front what Random Variables we will need to
 * represent, we cannot possibly hope to start with a sample space large
 * enough to house them all and satisfy any potential dependency
 * requirements (as a general rule, for each new RV which is independent to
 * all others, you need to expand the sample space by a factor of m, where
 * m is the number of possible values the RV can take). Therefore, we make
 * heavy use of Product Spaces. Whenever we introduce a new RV that is
 * independent from all others, we create it its own sample space, and as
 * long as it is examined alone this causes no issue. When it's examined
 * with other RVs, we examine them all under the context of their Product
 * Space.
 *
 * Therefore, our general discrete sample space can be thought of (and
 * coded) as a product space of [PrimitiveFiniteSampleSpace]s.
 *
 * Note that independence can be represented by sharing
 * [PrimitiveFiniteSampleSpace]s between RVs. So if one RV is defined on the
 * sample space A x B and another is defined on B x C, they are likely
 * to be dependent in some way.
 */
sealed class FiniteSampleSpace : SampleSpace<FiniteOutcome> {
    /**
     * Since we're now in the world of finite sample spaces,
     * we can ask for a random outcome.
     */
    abstract fun randomOutcome(): FiniteOutcome

    abstract fun measureSingleOutcome(outcome: FiniteOutcome): Probability

    /**
     * Rule for discrete sample spaces is the measure of an event is simply the sum of its outcomes.
     */
    override fun measure(event: FiniteEvent): Probability {
        return event.map { measureSingleOutcome(it) }.sum()
    }

    /**
     * Since outcomes of all [ProductFiniteSampleSpace]s are of the same type, we need
     * a way to check that an outcome is even one from this space!
     */
    abstract fun outcomeIsAnElementOfThisSpace(outcome: FiniteOutcome): Boolean
    fun requireOutcomeIsAnElementOfThisSpace(outcome: FiniteOutcome) = outcomeIsAnElementOfThisSpace(outcome) || error("Outcome was not an element of this space $outcome")
}

/**
 * Since a [FiniteSampleSpace] is a product of [PrimitiveFiniteSampleSpace],
 * an outcome a [FiniteSampleSpace] is a collection of outcomes - one
 * from each [PrimitiveFiniteSampleSpace].
 *
 * A [PrimitiveFiniteSampleSpace] should never occur more than once in a product
 * space, so we simply encode this as a map from [PrimitiveFiniteSampleSpace] to
 * its outcome.
 */
typealias FiniteOutcome = Map<PrimitiveFiniteSampleSpace, Int>
typealias FiniteEvent = Set<FiniteOutcome>

/**
 * See [FiniteSampleSpace] for a description of the simplifying assumptions we make here.
 *
 * There are two implementations of [FiniteSampleSpace], [PrimitiveFiniteSampleSpace] and
 * [ProductFiniteSampleSpace].
 *
 * This class represents a general product of [PrimitiveFiniteSampleSpace]s.
 */
class ProductFiniteSampleSpace(val primitiveSpaces: Set<PrimitiveFiniteSampleSpace>): FiniteSampleSpace() {

    override fun outcomeIsAnElementOfThisSpace(outcome: FiniteOutcome): Boolean = primitiveSpaces == outcome.keys

    /**
     * Definition of product measure: product together the measures of
     * each component. This ensures the product measure is also a
     * probability measure. For proof of this fact... go somewhere else!
     * This is code, not maths!
     */
    override fun measureSingleOutcome(outcome: FiniteOutcome): Float {
        requireOutcomeIsAnElementOfThisSpace(outcome)
        return outcome
            .mapValues { (space, value) -> space.measureSingleOutcome( mapOf(space to value)) }
            .values
            .fold(1f) { acc, value -> acc * value }
    }

    override fun randomOutcome(): FiniteOutcome {
        return primitiveSpaces.associateWith { it.randomOutcome()[it]!! }
    }

    override val space: FiniteEvent by lazy {
        fun getAllOutcomes(spaces: List<PrimitiveFiniteSampleSpace>): FiniteEvent {
            if (spaces.count() == 0) return setOf(emptyMap())

            val spacesMutable = spaces.toMutableList()
            val first = spacesMutable.removeAt(0)
            val outcomesOfOtherSpaces = getAllOutcomes(spacesMutable)
            val singleOutcomesOfFirstSpace = first.allOutcomes()

            // For each outcome in the first space we need a copy of all the other outcomes.
            val resultSet = mutableSetOf<Map<PrimitiveFiniteSampleSpace, Int>>()
            for (singleOutcome in singleOutcomesOfFirstSpace) {
                resultSet.addAll(outcomesOfOtherSpaces.map { it.toMutableMap().apply { set(first, singleOutcome) }})
            }
            return resultSet
        }

        getAllOutcomes(primitiveSpaces.toList())
    }
}

/**
 * Combine two sample spaces together to get the smallest sample space containing both.
 *
 * We have to be careful here not to product a primitive space with itself.
 */
fun FiniteSampleSpace.combine(other: FiniteSampleSpace): FiniteSampleSpace {
    return when (this) {
        other -> this
        is PrimitiveFiniteSampleSpace -> when (other) {
            is PrimitiveFiniteSampleSpace -> ProductFiniteSampleSpace(setOf(this, other))
            is ProductFiniteSampleSpace -> ProductFiniteSampleSpace(other.primitiveSpaces.toMutableSet().apply { add(this@combine) })
        }
        is ProductFiniteSampleSpace -> when (other) {
            is PrimitiveFiniteSampleSpace -> ProductFiniteSampleSpace(this.primitiveSpaces.toMutableSet().apply { add(other) })
            is ProductFiniteSampleSpace -> ProductFiniteSampleSpace(this.primitiveSpaces.union(other.primitiveSpaces))
        }
    }
}


/**
 * See [FiniteSampleSpace] for a description of the simplifying assumptions we make here.
 *
 * There are two implementations of [FiniteSampleSpace], [PrimitiveFiniteSampleSpace] and
 * [ProductFiniteSampleSpace].
 *
 * This class represents the primitive building blocks of a finite sample space, which can be
 * producted together.
 *
 * This can be thought of as a set of the form {1,...,n} for some [size] n.
 *
 * It has an auto-generated id, which is used to distinguish it from other primitive spaces of the
 * same signature.
 */
data class PrimitiveFiniteSampleSpace(val size: Int, val singleOutcomeMeasure: (Int) -> Probability, val id: String = uuid4().toString()) : FiniteSampleSpace() {
    init {
        // Check measure is a probability measure
        singleOutcomeMeasure.requireIsProbabilityMeasure((1..size).toSet())
    }

    fun allOutcomes(): Set<Int> = (1..size).toSet()

    override fun randomOutcome(): FiniteOutcome {
        val rand = Random.nextFloat()
        var culmProb = 0f
        for (x in 1..size) {
            culmProb += singleOutcomeMeasure(x)
            if (rand < culmProb) {
                return mapOf(this to x)
            }
        }
        return mapOf(this to 1)
    }

    override fun measureSingleOutcome(outcome: FiniteOutcome): Probability {
        requireOutcomeIsAnElementOfThisSpace(outcome)
        return singleOutcomeMeasure(outcome[this]!!)
    }

    override fun outcomeIsAnElementOfThisSpace(outcome: FiniteOutcome): Boolean = outcome.count() == 1 && outcome.containsKey(this)

    override val space: Set<FiniteOutcome> by lazy { (1..size).toSet().map { mapOf(this to it) }.toSet() }
}
