package chriscoomber.manydice

import kotlin.test.Test

class SampleUsage {
    @Test
    fun twentyD6() {
        var twentyD6 = fairDiceSum(20, 6, "20d6")
        println("20d6 has PMF: ${twentyD6.probabilityMassFunction}")
    }

    @Test
    fun introductionToAllFeatures() {
        // Define simple fair dice. This will be rolled independently to any other dice you define
        // in this way.
        val X = fairDice(6, "X")
        val Y = fairDice(6, "y")
        // Roll them separately or together
        println("Roll X (1d6): ${X.rollAlone()}")
        println("Roll X again: ${X.rollAlone()}")
        println("Roll both X and Y (1d6): ${rollTogether(X, Y)}")

        // Can define dice with arbitrary faces. Simply specify how many occurrances each face has.
        val colourDice = fairDice(mapOf("red" to 1, "blue" to 4, "yellow" to 1), "colourDice")
        println("Roll colourDice: ${colourDice.rollAlone()}")

        // Define random variables based off of dice
        val Z = (X + Y).setName("Z")
        println("Roll Z = X + Y: ${Z.rollAlone()}")
        println("Roll X: ${X.rollAlone()}")
        println("Roll Y: ${Y.rollAlone()}")

        // The roll of X above + the roll of Y above probably didn't add to the roll of Z above.
        // To ensure consistent rolls, it's crucial that they are rolled together.
        println("Roll them all together: ${rollTogether(listOf(X, Y, Z))}")

        // Can define random variables based off of other random variables
        val Ztimes2 = (Z * 2).setName("Z * 2")
        val ZminusX = (Z - X).setName("Z - X")
        println("Include some other random variables: ${rollTogether(listOf(X, Y, Z, Ztimes2, ZminusX))}")

        // The simple operations like +, -, * are defined for the most common cases, but in general
        // you can use the `map` and `combine` functions.
        val coin = fairDice(2).map { if (it == 1) "Heads" else "Tails" }.setName("coin")
        println("Flip coin: ${coin.rollAlone()}")
        val XgreaterThanY = combine(X, Y) { x, y -> x > y }.setName("X > Y")
        println("Roll X, Y and X>Y: ${rollTogether(X, Y, XgreaterThanY)}")

        // Can make copies of dice which are independent to their original.
        val Xcopy = X.copy()
        println("X and Xcopy can take different values: ${rollTogether(X, Xcopy)}")

        // Can even make copies of general random variables, such as X + Y. This makes a copy of
        // all relevant random variables under the hood.
        val Zcopy = Z.copy()
        println("Zcopy is totally independent from X, Y and Z: ${rollTogether(listOf(X, Y, Z, Zcopy))}")

        // You can also find out the distribution of any random variable
        println("PMF of Z: ${Z.probabilityMassFunction}")

        // Even if their values are not integers
        println("PMF of coin: ${coin.probabilityMassFunction}")

        // You can also ask for the distribution of a random variable, given some restraint on
        // another random variable
        println("PMF of X given Z>10: ${X.conditionalProbabilityMassFunction(Z) { it > 10 }}")
        val ZequalsYplus3 = combine(Z, Y) { z, y -> z == y + 3 }.setName("Z = Y + 3")
        println("PMF of X given Z=Y+3: ${X.conditionalProbabilityMassFunction(ZequalsYplus3) { it == true }}")
    }
}