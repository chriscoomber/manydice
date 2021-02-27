package chriscoomber.manydice

/**
 * A Random Variable that's directly implemented by some sort of physical object. Like a dice or a
 * coin.
 *
 * This is the only implementation of [FiniteRandomVariable] which you can create from scratch.
 *
 * Works off of a simple sample space consisting of a set of the form {1, ..., n}, known as a
 * [PrimitiveFiniteSampleSpace]. It is up to the caller of this constructor to engineer the [space] and
 * [evaluator] functions to give the right outcome. The simplest solution is usually to enumerate
 * all the possible values of this RV and give each their own outcome in the space, using the
 * space's measure function to give the correct probabilities.
 *
 * Usually, one can assume that a [PhysicalRandomVariable] is independent to all others. This is
 * achieved by giving the [PhysicalRandomVariable] its own [PrimitiveFiniteSampleSpace] that is not used by any
 * others - i.e. a space that's disjoint from all others.
 */
class PhysicalRandomVariable<T>(
    private val space: PrimitiveFiniteSampleSpace,
    private val name: String? = null,
    private val evaluator: (Int) -> T
) : FiniteRandomVariable<T> {
    override val sampleSpace: FiniteSampleSpace = space

    override fun evaluate(outcome: FiniteOutcome): T {
        space.requireOutcomeIsAnElementOfThisSpace(outcome)
        return evaluator(outcome[space]!!)
    }

    override fun copy(mangler: String): FiniteRandomVariable<T> {
        // TODO: do some cleverer mangling than this!
        return PhysicalRandomVariable(space.copy(id = space.id + mangler), name?.plus(" (copy)"), evaluator)
    }

    override fun toString() = name ?: "PhysicalRandomVariable(primitiveSpace=$space, values=$range)"

    private val range: Set<T> = space.allOutcomes().map(evaluator).toSet()
}