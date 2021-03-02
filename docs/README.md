# ManyDice
Kotlin dice rolling library for table top RPGs.

<script src="https://unpkg.com/kotlin-playground@1" data-selector="code" data-server="https://kotlin-compiler.chriscoomber.co.uk">
</script>

## What is ManyDice?

ManyDice is a Kotlin library that allows you to simulate dice-rolling scenarios, and find out the probability distributions of arbitrary outcomes. For example, are you a Dungeon Master? You can use this tool to design fair but random scenarios, and calculate the chance that your players will succeed. For probability enthusiasts: it can simulate all finite Random Variables, and is capable of handling dependent events and conditional probabilities.

## Playground

Use this space to write your dice scenarios. See below for usage information. For now, you will have to copy it to a text file that you save on your computer, to avoid losing it. I plan to make this better in the future.

```
import chriscoomber.manydice.*
fun main() {
//sampleStart
// Write your code here!
val X = fairDice(6)
val result = X.rollAlone()
println("$X rolled a $result")
//sampleEnd
}
```

## How to use ManyDice

The easiest way is to type your code into the box above. However, since it is a Kotlin library you can also import it into your own project. See <a href="https://github.com/chriscoomber/manydice">github</a> for details.

## Syntax and examples

It might be useful to know basic Kotlin syntax, such as variables, functions and closures. I won't be able to do a better job than the [Kotlin docs](https://kotlinlang.org/docs/basic-syntax.html).

All of these examples can be edited and run on the online compiler. Feel free to experiment as you learn the syntax!

### Basics

Fair dice can be defined using some form of the *fairDice()* function. All dice can be rolled alone with the *rollAlone* function, or can be rolled together with *rollTogether*

```
import chriscoomber.manydice.*
fun main() {
//sampleStart
// Here we define two dice
val X = fairDice(6)
val Y = fairDice(20)

// Here we roll the first dice and print the result
val XRoll = X.rollAlone()
println("$X rolled a $XRoll")

// Rolling the dice again may produce a different result.
println("Rolling $X a few more times: ${X.rollAlone()}, ${X.rollAlone()}, ${X.rollAlone()}, ${X.rollAlone()}")

// Here we roll both dice together.
val togetherRoll = rollTogether(listOf(X, Y))
println("When rolled together we get: $togetherRoll")
//sampleEnd
}
```

### TODO: rest of the documentation

```
import chriscoomber.manydice.*
fun main() {
//sampleStart
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
//sampleEnd
}
```

#### Test JS compiling

<pre><code data-target-platform="js" data-js-libs="https://wyvern.jfrog.io/artifactory/maven-public/chriscoomber/manydice-js/0.2.2/manydice-js-0.2.2.jar!/manydice.js">
import chriscoomber.manydice.*

fun main() {
    val x = fairDice(6)
    println(x.rollAlone())
}
</code></pre>
