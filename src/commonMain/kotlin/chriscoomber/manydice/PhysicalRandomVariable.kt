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
class PhysicalRandomVariable<E>(
    private val space: PrimitiveFiniteSampleSpace,
    name: String? = null,
    private val evaluator: (Int) -> E
) : FiniteRandomVariable<E> {

    override val sampleSpace: FiniteSampleSpace = space

    override fun evaluate(outcome: FiniteOutcome): E {
        space.requireOutcomeIsAnElementOfThisSpace(outcome)
        return evaluator(outcome[space]!!)
    }

    private val range: Set<E> = space.allOutcomes().map(evaluator).toSet()
    override val name: String = name ?: "PhysicalRandomVariable(primitiveSpace=$space, values=$range)"

    override fun copy(mangler: String): FiniteRandomVariable<E> {
        // TODO: do some cleverer mangling than this!
        return PhysicalRandomVariable(space.copy(id = space.id + mangler), name?.plus(" (copy)"), evaluator)
    }

    override fun setName(newName: String) = PhysicalRandomVariable(space, newName, evaluator)

    override fun toString() = name

    companion object {
        /**
         * Produce a physical random variable from a probability mass function. The resulting RV will be independent
         * to all other RVs, so you can think of it like a weighted die with the same PMF as the one given.
         */
        fun <E> fromProbabilityMassFunction(pmf: Map<E, Probability>, name: String? = null): PhysicalRandomVariable<E> {
            val values = pmf.keys.toList()
            fun outcomeToValue(outcome: Int) = values[outcome-1]

            return PhysicalRandomVariable(
                space = PrimitiveFiniteSampleSpace(
                    values.count(), { outcome ->
                        // The measure of an outcome is the value given in the PMF
                        pmf[outcomeToValue(outcome)]!!
                    }
                ),
                name = name,
                evaluator = ::outcomeToValue
            )
        }
    }
}
