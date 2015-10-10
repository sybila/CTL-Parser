Simple parser, normalizer and optimizer for CTL temporal logic formulas written in Kotlin.

###How to get it
The repo is jitpack-compatibile, so all you have to do is look up the latest version on jitpack and then integrate it into your favorite build system: [CTL Parser on Jitpack](https://jitpack.io/#sybila/CTL-Parser)

###Features

 - File includes
 - References
 - Full range of boolean and temporal operators
 - Simple formula optimizer
 - Normalizer into until normal form (EU, AU, EX, &&, ||, !)

###Syntax

Each CTL file contains zero or more include statements and zero or more assignment statements. Each statement is separated by a new line. Multi-line formulas are not supported, use references instead.

Input statement has the following form: ```#include "string"``` where ```string``` is a path to a file (can be absolute or relative) that should be included. For now, escaping quotation marks is not allowed, so make sure your path does not contain any. Each file is included at most once, so you don't have to worry about multiple includes.

Assignment statement has the form of ```identifier = formula```. ```identifier``` consists of numbers and upper/lowercase characters (has to start with a character). Formula can be
 - identifier (reference to formula defined elsewhere) 
 - boolean constant (True/False/tt/ff)
 - float proposition: ```id operator number``` where ```id``` is a name of the model property and operator is one of <, >, <=, >=, !=, ==. Numbers can't use scientific notation (1e10).
 - direction proposition: ```id:direction facet```(space is not mandatory)  where ```id``` is a name of the model property, direction is either in or our and facet can be positive(+) or negative(-). Example: ```val:in+```
 - another formula in parentheses
 - Formula with unary operator applied. Unary operators: !, EX, AX, EF, AF, EG, AG
 - Two formulas with binary operator applied (infix notation: ```f1 op f2```). Binary operators: &&, ||, =>, <=>, EU, AU
 
Forward declaration, even accross files is supported, so that you can define your formulas in whatever order you choose. For obvious reasons, cyclic references are not allowed.

Operator priority: In general, unary operators have higher priority than binary operators. Binary operators have following priority: && >> || >> => >> <=> >> EU >> AU

Also note that =>, EU and AU are right associative, so that ```a EU b EU c``` is parsed as ```a EU (b EU c)```, as expected.

Example of usage:

```
  f = (AG (p1 EU a)) || (EG (p2 AU b))
  p1 = val > 3.14
  p2 = val2 < -44
  p2 = var:in+ && var:in-
  a = EF (p1 && p2)
  b = AF foo
  #include "other/foo.ctl"
```

###Normalization and Optimization

Currently you can transform formulas into normal form that supports only operators EX, EU, AU, &&, ||, !. Alternatively, you can provide our own normalization function map that will implement your own normal form (or modify current one). 

You can also automatically perform some simple formula optimizations (proposition negation, double negation removal, boolean propagation and some simplifications). Note that these optimizations expect the formula to be in until normal form. (Other formulas are also supported, but possible optimizations are limited)

###API

There are currently three main classes you can use ```Parser```, ```Normalizer```, ```Optimizer```. ```Parser``` provides methods that will parse a file, string or a single formula string (you don't have to perform an assignment). These methods automatically resolve all includes and references.

For more complex usage example, see the [IntegrationTest](https://github.com/sybila/CTL-Parser/blob/new_parser/src/test/kotlin/cz/muni/fi/ctl/IntegrationTest.kt)

###Building and using

Whole repository is a gradle project, so you can include it into an existing gradle workspace.

CTLParser and CTLLexer classes are autogenerated by antlr and therefore not included in this repository. They are automatically assembled during 'gradle build'. If you need them prior to build operation, you can call 'gradle antlr4' to generate them separately. 

Alternatively, you can build whole project into a jar file (located in build/lib).
- use 'gradle fullJar' to create jar with all required dependencies
- use 'gradle jar' to create jar without dependencies (antlr needs to be included at runtime)
