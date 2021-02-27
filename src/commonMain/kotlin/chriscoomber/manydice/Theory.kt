package chriscoomber.manydice


/**
 * A probability is close enough to another one if it's within this threshold.
 */
const val PROBABILITY_FLOAT_THRESHOLD = 0.0000001f

/**
 * A probability is a real number between 0 and 1 inclusive. Users of this type
 * should check it is within those bounds.
 */
typealias Probability = Float

/**
 * Check whether a probability is "equal" to another one - or as close as is
 * possible with floats.
 */
fun Probability.equalsWithinThreshold(other: Probability): Boolean {
    return this > other - PROBABILITY_FLOAT_THRESHOLD || this < other + PROBABILITY_FLOAT_THRESHOLD
}

/**
 * Check that this function is a probability measure on the given space. I.e., check that
 * it is non-negative and sums to 1.
 *
 * Returns true if and only if it's a probability measure.
 */
fun <Outcome> ((Outcome) -> Probability).isProbabilityMeasure(space: Set<Outcome>): Boolean {
    val sum = space.fold(0f) { totalProb, outcome ->
        val probability = this.invoke(outcome)
        if (probability < 0f || probability > 1f) return false
        totalProb + probability
    }
    return sum.equalsWithinThreshold(1f)
}

fun <Outcome> ((Outcome) -> Probability).requireIsProbabilityMeasure(space: Set<Outcome>) =
    this.isProbabilityMeasure(space) || error("Not a probability measure.")

/**
 * A sample space is a set of "outcomes" equipped with:
 *
 * - A sigma-algebra of "events", i.e. sets of outcomes.
 * This is a bit mathematically technical but in general not all
 * sets are necessarily "measurable". However, we will only be
 * dealing with finite spaces, so the sigma-algebra just contains
 * every possible subset of the space. We don't need to declare
 * this in code.
 *
 * - A probability measure which assigns an event to a
 * "probability", i.e. a real number between 0 and 1 inclusive.
 * The measure must satisfy some sensible properties such as:
 *     - The measure of the empty event (no outcomes) is 0
 *     - The measure of the whole space (all outcomes) is 1
 *     - The measure of a disjoint union of events is equal to
 *     the sum of the measures of the events. (Technical note:
 *     this is only required for countable unions. However, this being
 *     a computer and us being mortals bound by the restrictions
 *     of time, I don't think we'll be calculating any infinite
 *     unions anyway, countable or not.)
 */
interface SampleSpace<Outcome> {
    // TODO iterator instead of set?
    val space: Set<Outcome>
    fun measure(event: Set<Outcome>): Probability
}

/**
 * A random variable is a function from a sample space to some measurable space E
 */
interface RandomVariable<Outcome, E> {
    /**
     * The sample space that this random variable is defined on.
     */
    val sampleSpace: SampleSpace<Outcome>

    /**
     * Evaluate the random variable at a particular outcome.
     */
    fun evaluate(outcome: Outcome): E
}
