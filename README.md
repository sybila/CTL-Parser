[![Release](https://jitpack.io/v/sybila/ctl-parser.svg)](https://jitpack.io/#sybila/ctl-parser)
[![Build Status](https://travis-ci.org/sybila/ctl-parser.svg?branch=master)](https://travis-ci.org/sybila/ctl-parser)
[![codecov.io](https://codecov.io/github/sybila/ctl-parser/coverage.svg?branch=master)](https://codecov.io/github/sybila/ctl-parser?branch=master)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg?style=flat)](https://github.com/sybila/ctl-parser/blob/master/LICENSE.txt)
[![Kotlin](https://img.shields.io/badge/kotlin-1.0.0-blue.svg)](http://kotlinlang.org)


Simple parser, normalizer and optimizer for CTL temporal logic formulas.

###How to use
This repo is jitpack-compatibile, so all you have to do is look up the latest version on jitpack and then integrate it into your favorite build system: [CTL Parser on Jitpack](https://jitpack.io/#sybila/ctl-parser)

There is a CTLParser class you can use to parse formulas.
Using this class you can parse strings, files or inlined formulas (references and includes will be automatically resolved).

Project also defines a convenient syntax for writing formulas directly in code (See Extensions.kt file).

For more complex usage example, see the [IntegrationTest](src/test/kotlin/com/github/sybila/ctl/IntegrationTest.kt)

###Features

 - File includes
 - References
 - Full range of boolean and temporal operators
 - Simple formula optimizer
 - Normalizer into until normal form (EU, AU, EX, &&, ||, !)

###Syntax

Each CTL file contains zero or more include statements and zero or more assignment statements. Each statement is separated by a new line or semicolon (;). Multi-line formulas are not supported, use references instead.

Input statement has the following form: ```:include "string"``` where ```string``` is a path to a file (can be absolute or relative) that should be included. For now, escaping quotation marks is not allowed, so make sure your path does not contain any. Each file is included at most once, so you don't have to worry about multiple includes.

Assignment statement has the form of ```identifier = formula``` or ```identifier = expression```. ```identifier``` consists of numbers and upper/lowercase characters (has to start with a character). Formula can be
 - identifier (reference to formula defined elsewhere) 
 - boolean constant (True/False/tt/ff)
 - float proposition: two expressions (or expression identifiers) compared using one of these operators: <, >, <=, >=, !=, ==. 
 - direction proposition: ```id:direction facet```(space is not mandatory)  where ```id``` is a name of the model property, direction is either in or our and facet can be positive(+) or negative(-). Example: ```val:in+```
 - another formula in parentheses
 - Formula with unary operator applied. Unary operators: !, EX, AX, EF, AF, EG, AG
 - Two formulas with binary operator applied (infix notation: ```f1 op f2```). Binary operators: &&, ||, =>, <=>, EU, AU
 
Expression can be
 - identifier (reference to expression defined elsewhere or variable you want to reference in the formula). Note that identifiers that can't be resolved are considered as variables and produce no error or warning.
 - numeric constant - You can't use scientific notation (1e10), but everythin else should work fine.
 - expression in parenthesis
 - two expressions joined by +,-,*,/

Forward declaration, even accross files is supported, so that you can define your formulas in whatever order you choose. For obvious reasons, cyclic references are not allowed.

Operator priority: In general, unary operators have higher priority than binary operators. Binary operators have following priority: && >> || >> => >> <=> >> EU >> AU

Also note that =>, EU and AU are right associative, so that ```a EU b EU c``` is parsed as ```a EU (b EU c)```, as expected.

You can use special operator `:?` to mark formulas that are in some way interesting and then use a parsing option to include only these in the results (references and includes are processed normally, it is just result filtering).

Example of usage:

```
  q = 2 - Var2
  f = (AG ((q / Var1) EU 2 * Var2)) || (EG (p2 AU b))
  p1 = val > 3.14
  p2 = val2 < -44
  p2 = var:in+ && var:in-
  :? a = EF (p1 && p2)
  b = AF foo
  #include "other/foo.ctl"
```

###Normalization and Optimization

Currently you can transform formulas into normal form that uses only operators EX, EU, AU, &&, ||, !. Alternatively, you can provide our own normalization function map that will implement your own normal form (or modify current one).

You can also automatically perform some simple formula optimizations (proposition negation, double negation removal, boolean propagation and some simplifications). Note that these optimizations expect the formula to be in until normal form. (Other formulas are also supported, but possible optimizations are limited)

These actions can be performed using extension functions on Formula object (translated to static Java functions).

###Building and using

Whole repository is a gradle project, so you can include it into an existing gradle workspace, if you don't want to use jitpack.

CTLParser and CTLLexer classes are autogenerated by antlr and therefore not included in this repository. They are automatically assembled during 'gradle build'. If you need them prior to build operation, you can call 'gradle generateGrammarSource' to generate them separately. 

Alternatively, you can build the whole project into a jar file (located in build/lib).
- use 'gradle fullJar' to create jar with all dependencies
- use 'gradle jar' to create jar without dependencies (antlr needs to be included at runtime)
