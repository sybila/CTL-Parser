package com.github.sybila.huctl

/**
 * Specifies the type of the temporal path quantifier used by the HUCTLp [Formula].
 */
enum class PathQuantifier {
    /** All future paths */ A,
    /** Exists future path */ E,
    /** All past paths */ pA,
    /** Exists past path */ pE;
}

/**
 * Specifies the type of the comparison operator used by the [Formula.Numeric] propositions.
 */
enum class CompareOp(private val str: String) {
    /** Greater than (>) */ GT(">"),
    /** Greater than or equal (>=) */ GE(">="),
    /** Equal (==) */ EQ("=="),
    /** Not equal (!=) */ NEQ("!="),
    /** Less than or equal (<=) */ LE("<="),
    /** Less than (<) */ LT("<");

    override fun toString(): String = str
}

/**
 * Specifies the type of transition between two states.
 */
enum class Flow(private val str: String) {
    /** Incoming transition */ IN("in"),
    /** Outgoing transition */ OUT("out");
    override fun toString(): String = str
}

/**
 * Specifies the direction in which one moves in the transition system.
 */
enum class Direction(private val str: String) {
    /** Increasing (n -> n+1) */ POSITIVE("+"),
    /** Decreasing (n -> n-1) */ NEGATIVE("-");
    override fun toString(): String = str
}

/**
 * Common interface for unary operators. Used by [DirFormula] and [Formula].
 */
interface Unary<This, Tree> where This : Unary<This, Tree> {

    /**
     * The singular child element of this object.
     */
    val inner: Tree

    /**
     * Create a copy of the original object but optionally replace the child element.
     */
    fun copy(inner: Tree = this.inner): This

}

/**
 * Common interface for binary operators. Used by [Formula], [DirFormula] and [Expression].
 */
interface Binary<This, Tree> where This : Binary<This, Tree> {

    /**
     * The child of this element which is the root of the left subtree.
     */
    val left: Tree

    /**
     * The child of this element which is the root of the right subtree.
     */
    val right: Tree

    /**
     * Create a copy of the original object but optionally replace the left or right child element.
     */
    fun copy(left: Tree = this.left, right: Tree = this.right): This

}

/**
 * An utility method which transforms a binary formula into a DirFormula assuming both
 * children can be also transformed.
 */
internal fun Binary<*, Formula>.directionFold(fold: (DirFormula, DirFormula) -> DirFormula): DirFormula? {
    val left = this.left.asDirFormula()
    val right = this.right.asDirFormula()
    return if (left != null && right != null)
        fold(left, right)
    else null
}