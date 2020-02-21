import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ArithmeticTest {
    @Test
    fun test() {
        val parser = ArithmeticParser("2+3".reader())
        parser.AdditiveExpression()
        val rootNode = parser.rootNode()
        val nodes = Nodes.iterator(rootNode).asSequence().joinToString(" ") { it.asStr }
        Assertions.assertEquals("NUMBER(2) PLUS(+) NUMBER(3)", nodes)
    }

    private val Node.asStr: String get() = javaClass.simpleName + "($this)"
}
