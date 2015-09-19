package cz.muni.fi.ctl

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class References {

    val parser = Parser()

    @Test fun cyclicReferenceThroughFiles() {
        val file = File.createTempFile("file", ".ctx")

        file.bufferedWriter().use {
            it.write("k = m")
        }

        assertFailsWith(IllegalStateException::class) {
            parser.parse("""
                m = !k
            """)
        }
        file.delete()
    }

    @Test fun transitiveCyclicReference() {
        assertFailsWith(IllegalStateException::class) {
            parser.parse("""
                k = EX l
                l = AX m
                m = ! k
            """)
        }
    }

    @Test fun simpleCyclicReference() {
        assertFailsWith(IllegalStateException::class) {
            parser.parse("k = !k")
        }
    }

    @Test fun undefinedReference() {
        assertFailsWith(IllegalStateException::class) {
            parser.parse("k = EF m")
        }
    }

    @Test fun declarationOrderIndependence() {

        val result = parser.parse("""
            k = ! m
            m = True
        """)

        assertEquals(2, result.size())
        assertEquals(not(True), result["k"])
        assertEquals(True, result["m"])

    }

    @Test fun duplicateDeclarationInFiles() {

        val i1 = File.createTempFile("include1", ".ctl")

        i1.bufferedWriter().use {
            it.write("k = True")
        }

        assertFailsWith(IllegalStateException::class) {
            parser.parse(
                    "#include \"${ i1.absolutePath }\" \n" +
                            "k = False"
            )
        }

        i1.delete()
    }

    @Test fun duplicateDeclarationInString() {
        assertFailsWith(IllegalStateException::class) {
            parser.parse("""
                k = True
                l = False
                k = False
            """)
        }
    }

    @Test fun transitiveResolveInFiles() {

        val i1 = File.createTempFile("include1", ".ctl")
        val i2 = File.createTempFile("include2", ".ctl")

        i1.bufferedWriter().use {
            it.write("k = True")
        }
        i2.bufferedWriter().use {
            it.write("l = EF k")
        }

        val result = parser.parse(
                "m = !l \n" +
                        "#include \"${ i1.absolutePath }\" \n" +
                        "#include \"${ i2.absolutePath }\" \n"
        )

        assertEquals(3, result.size())
        assertEquals(True, result["k"])
        assertEquals(EF(True), result["l"])
        assertEquals(not(EF(True)), result["m"])

    }

    @Test fun transitiveResolveInString() {

        val result = parser.parse("""
                k = True
                l = EF k
                m = !l
        """)

        assertEquals(3, result.size())
        assertEquals(True, result["k"])
        assertEquals(EF(True), result["l"])
        assertEquals(not(EF(True)), result["m"])

    }

    @Test fun simpleResolveInInclude() {

        val i = File.createTempFile("include", ".ctl")

        i.bufferedWriter().use {
            it.write("val = False")
        }

        val result = parser.parse(
                "k = !val \n " +
                        "#include \"${ i.absolutePath }\" \n"
        )

        assertEquals(2, result.size())
        assertEquals(False, result["val"])
        assertEquals(not(False), result["k"])

        i.delete()

    }

    @Test fun simpleResolveInString() {
        val result = parser.parse("""
            k = True
            l = !k
        """)
        assertEquals(2, result.size())
        assertEquals(True, result["k"])
        assertEquals(not(True), result["l"])
    }

    @Test fun aliasInString() {
        val result = parser.parse("""
            k = True
            l = k
        """)
        assertEquals(2, result.size())
        assertEquals(True, result["k"])
        assertEquals(True, result["l"])
    }

}