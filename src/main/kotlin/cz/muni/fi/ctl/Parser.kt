package cz.muni.fi.ctl

import cz.muni.fi.ctl.antlr.CTLBaseListener
import cz.muni.fi.ctl.antlr.CTLLexer
import cz.muni.fi.ctl.antlr.CTLParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.ParseTreeProperty
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.io.File
import java.util.*

/**
 * Workflow:
 * Antlr constructs a parse tree that is transformed into FileContext.
 * File context is a direct representation of a file.
 * Using FileParser, includes in the file context are resolved and merged into a ParserContext.
 * Duplicate assignments are checked at this stage.
 * Lastly, Parser resolves references in ParserContext (checking for undefined, cyclic and invalid type references)
 * and returns a final map of valid formula assignments
 */

public class Parser {

    public fun formula(input: String): Formula = parse("val = $input")["val"]!!

    public fun parse(input: String): Map<String, Formula> = process(FileParser().process(input))

    public fun parse(input: File): Map<String, Formula> = process(FileParser().process(input))

    fun process(ctx: ParserContext): Map<String, Formula> {

        //Resolve references
        //First resolve all aliases - we need to find out whether they reference formulas or expressions
        //Only formulas are returned.

        val references = HashMap(ctx.toMap())  //mutable copy
        val replaced = Stack<String>()  //processing stack for cycle detection

        fun <R> stacked(name: String, action: () -> R): R {
            replaced.push(name)
            val result = action()
            replaced.pop()
            return result
        }

        fun resolveAlias(a: Assignment): Assignment =
            when {
                a !is AliasAssignment -> a
                a.name in replaced -> throw IllegalStateException("Cyclic reference ${a.name} in ${a.location}")
                a.alias !in references -> ExpressionAssignment(a.name, a.alias.toVariable(), a.location)
                else -> stacked(a.name) { resolveAlias(references[a.alias]!!) }
            }

        fun resolveExpression(e: Expression): Expression = when (e) {
            is Variable -> when {
                e.name in replaced -> throw IllegalStateException("Cyclic reference: ${e.name}")
                e.name in references -> stacked(e.name) {
                    val assignment = references[e.name]!!
                    if (assignment is ExpressionAssignment) resolveExpression(assignment.expression)
                    else throw IllegalStateException("${e.name} is a formula. Expression needed.")
                }
                else -> e   //This is a valid variable, not a reference
            }
            else -> e.treeMap(::resolveExpression)
        }

        fun resolveFormula(f: Formula): Formula = when (f) {
            is Reference -> when {
                f.name in replaced -> throw IllegalStateException("Cyclic reference: ${f.name}")
                f.name !in references -> throw IllegalStateException("Undefined reference: ${f.name}")
                else -> stacked(f.name) {
                    val assignment = references[f.name]!!
                    if (assignment is FormulaAssignment) resolveFormula(assignment.formula)
                    else throw IllegalStateException("${f.name} is an expression. Formula needed.")
                }
            }
            //dive into the expressions
            is FloatProposition -> FloatProposition(resolveExpression(f.left), f.compareOp, resolveExpression(f.right))
            else -> f.treeMap(::resolveFormula)
        }

        for ((name, assignment) in references) {    //resolve aliases
            references[name] = resolveAlias(assignment)
        }

        for ((name, assignment) in references) {    //resolve expressions - this way we catch errors also for unused expressions
            if (assignment is ExpressionAssignment) references[name] = ExpressionAssignment(name, resolveExpression(assignment.expression), assignment.location)
        }

        val results = HashMap<String, Formula>()
        for ((name, assignment) in references) {
            if (assignment is FormulaAssignment) results[name] = resolveFormula(assignment.formula)
        }

        return results
    }

}

class FileParser {

    private val processed = HashSet<File>()

    fun process(input: String): ParserContext {
        val ctx = processString(input)
        return ctx.includes.map { process(it) }.fold(ctx.toParseContext(), ParserContext::plus)
    }

    fun process(input: File): ParserContext {
        val ctx = processFile(input)
        processed.add(input)
        return ctx.includes.
                filter { it !in processed }.
                map { process(it) }.
                fold (ctx.toParseContext(), ParserContext::plus)
    }

    private fun processString(input: String): FileContext =
            processStream(ANTLRInputStream(input.toCharArray(), input.length), "input string")

    private fun processFile(input: File): FileContext =
            input.inputStream().use { processStream(ANTLRInputStream(it), input.absolutePath) }

    private fun processStream(input: ANTLRInputStream, location: String): FileContext {
        val root = CTLParser(CommonTokenStream(CTLLexer(input))).root()
        val context = FileContext(location)
        ParseTreeWalker().walk(context, root)
        return context
    }

}

data class ParserContext(
        val assignments: List<Assignment>
) {

    fun toMap() = assignments.toMap({ it.name }, { it } )

    /**
     * Checks for duplicate assignments received from parser
     */
    init {
        for (one in assignments) {
            for (two in assignments) {
                if (one.name == two.name && one.location != two.location) {
                    throw IllegalStateException(
                            "Duplicate assignment for ${one.name} defined in ${one.location} and ${two.location}"
                    )
                }
            }
        }
    }

    operator fun plus(ctx: ParserContext): ParserContext {
        return ParserContext(assignments + ctx.assignments)
    }

}

class FileContext(val location: String) : CTLBaseListener() {

    val includes = ArrayList<File>()
    val formulas = ArrayList<FormulaAssignment>()
    val expressions = ArrayList<ExpressionAssignment>()
    val aliases = ArrayList<AliasAssignment>()

    private val formulaTree = ParseTreeProperty<Formula>()
    private val expressionTree = ParseTreeProperty<Expression>()

    fun toParseContext() = ParserContext(formulas + expressions + aliases)

    override fun exitInclude(ctx: CTLParser.IncludeContext) {
        val string = ctx.STRING().text!!
        includes.add(File(string.substring(1, string.length - 1)))    //remove quotes
    }

    override fun exitAssignFormula(ctx: CTLParser.AssignFormulaContext) {
        formulas.add(FormulaAssignment(
                ctx.VAR_NAME().text,
                formulaTree[ctx.formula()],
                location+":"+ctx.start.line
        ))
    }

    override fun exitAssignExpression(ctx: CTLParser.AssignExpressionContext) {
        expressions.add(ExpressionAssignment(
                ctx.VAR_NAME().text,
                expressionTree[ctx.expression()],
                location+":"+ctx.start.line
        ))
    }

    override fun exitAssignAlias(ctx: CTLParser.AssignAliasContext) {
        aliases.add(AliasAssignment(
                ctx.VAR_NAME(0).text!!,
                ctx.VAR_NAME(1).text!!,
                location+":"+ctx.start.line
        ))
    }

    /** ------ Expression Parsing ----- **/

    override fun exitIdExpression(ctx: CTLParser.IdExpressionContext) {
        expressionTree[ctx] = Variable(ctx.text)
    }

    override fun exitValue(ctx: CTLParser.ValueContext) {
        expressionTree[ctx] = Constant(ctx.FLOAT_VAL().text.toDouble())
    }

    override fun exitParenthesisExpression(ctx: CTLParser.ParenthesisExpressionContext) {
        expressionTree[ctx] = expressionTree[ctx.expression()]
    }

    override fun exitMultiplication(ctx: CTLParser.MultiplicationContext) {
        expressionTree[ctx] = expressionTree[ctx.expression(0)] times expressionTree[ctx.expression(1)]
    }

    override fun exitDivision(ctx: CTLParser.DivisionContext) {
        expressionTree[ctx] = expressionTree[ctx.expression(0)] over expressionTree[ctx.expression(1)]
    }

    override fun exitAddition(ctx: CTLParser.AdditionContext) {
        expressionTree[ctx] = expressionTree[ctx.expression(0)] plus expressionTree[ctx.expression(1)]
    }

    override fun exitSubtraction(ctx: CTLParser.SubtractionContext) {
        expressionTree[ctx] = expressionTree[ctx.expression(0)] minus expressionTree[ctx.expression(1)]
    }

    /** ------ Formula Parsing ------ **/

    override fun exitId(ctx: CTLParser.IdContext) {
        formulaTree[ctx] = Reference(ctx.text!!)
    }

    override fun exitBool(ctx: CTLParser.BoolContext) {
        formulaTree[ctx] = if (ctx.TRUE() != null) True else False
    }

    override fun exitDirection(ctx: CTLParser.DirectionContext) {
        formulaTree[ctx] = DirectionProposition(
                variable = ctx.VAR_NAME().text!!,
                direction = if (ctx.IN() != null) Direction.IN else Direction.OUT,
                facet = if (ctx.PLUS() != null) Facet.POSITIVE else Facet.NEGATIVE
        )
    }

    override fun exitProposition(ctx: CTLParser.PropositionContext) {
        formulaTree[ctx] = FloatProposition(
                left = expressionTree[ctx.expression(0)],
                compareOp = ctx.compare().toOperator(),
                right = expressionTree[ctx.expression(1)]
        )
    }

    override fun exitParenthesis(ctx: CTLParser.ParenthesisContext) {
        formulaTree[ctx] = formulaTree[ctx.formula()]
    }

    override fun exitUnary(ctx: CTLParser.UnaryContext) {
        formulaTree[ctx] = FormulaImpl(ctx.unaryOp().toOperator(), formulaTree[ctx.formula()])
    }

    override fun exitOr(ctx: CTLParser.OrContext) {
        formulaTree[ctx] = formulaTree[ctx.formula(0)] or formulaTree[ctx.formula(1)]
    }

    override fun exitAnd(ctx: CTLParser.AndContext) {
        formulaTree[ctx] = formulaTree[ctx.formula(0)] and formulaTree[ctx.formula(1)]
    }

    override fun exitImplies(ctx: CTLParser.ImpliesContext) {
        formulaTree[ctx] = formulaTree[ctx.formula(0)] implies formulaTree[ctx.formula(1)]
    }

    override fun exitEqual(ctx: CTLParser.EqualContext) {
        formulaTree[ctx] = formulaTree[ctx.formula(0)] equal  formulaTree[ctx.formula(1)]
    }

    override fun exitEU(ctx: CTLParser.EUContext) {
        formulaTree[ctx] = formulaTree[ctx.formula(0)] EU formulaTree[ctx.formula(1)]
    }

    override fun exitAU(ctx: CTLParser.AUContext) {
        formulaTree[ctx] = formulaTree[ctx.formula(0)] AU formulaTree[ctx.formula(1)]
    }
}

interface Assignment {
    val name: String
    val location: String
}
data class FormulaAssignment(override val name: String, val formula: Formula, override val location: String) : Assignment
data class ExpressionAssignment(override val name: String, val expression: Expression, override val location: String) : Assignment
data class AliasAssignment(override val name: String, val alias: String, override val location: String) : Assignment

data class Reference(val name: String) : Atom {
    final override val operator = Op.ATOM
    final override val subFormulas = listOf<Formula>()
}

//convenience methods

fun CTLParser.UnaryOpContext.toOperator(): Op = when {
    EX() != null -> Op.EXISTS_NEXT
    AX() != null -> Op.ALL_NEXT
    EF() != null -> Op.EXISTS_FUTURE
    AF() != null -> Op.ALL_FUTURE
    EG() != null -> Op.EXISTS_GLOBAL
    AG() != null -> Op.ALL_GLOBAL
    else -> Op.NEGATION
}

fun CTLParser.CompareContext.toOperator(): CompareOp = when {
    NEQ() != null -> CompareOp.NEQ
    LT() != null -> CompareOp.LT
    LTEQ() != null -> CompareOp.LT_EQ
    GT() != null -> CompareOp.GT
    GTEQ() != null -> CompareOp.GT_EQ
    else -> CompareOp.EQ
}

operator fun <T> ParseTreeProperty<T>.set(k: ParseTree, v: T) = this.put(k, v)
operator fun <T> ParseTreeProperty<T>.get(k: ParseTree): T = this.get(k)
