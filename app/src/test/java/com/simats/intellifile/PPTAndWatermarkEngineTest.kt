package com.simats.intellifile

import org.junit.Test
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

class PPTAndWatermarkEngineTest {

    private fun parseXmlDocument(input: InputStream): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        factory.isExpandEntityReferences = false
        return factory.newDocumentBuilder().parse(input)
    }

    private fun Node.isMatchingTag(name: String): Boolean {
        val nn = nodeName.lowercase()
        val target = name.lowercase()
        return nn == target || nn.endsWith(":$target")
    }

    private fun Node.findFirstDescendant(name: String): Element? {
        val queue = ArrayDeque<Node>()
        queue.add(this)
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr is Element && curr != this && curr.isMatchingTag(name)) {
                return curr
            }
            val children = curr.childNodes
            for (i in 0 until children.length) {
                queue.add(children.item(i))
            }
        }
        return null
    }

    private fun Node.findDescendants(name: String): List<Element> {
        val result = mutableListOf<Element>()
        val queue = ArrayDeque<Node>()
        queue.add(this)
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr is Element && curr != this && curr.isMatchingTag(name)) {
                result.add(curr)
            }
            val children = curr.childNodes
            for (i in 0 until children.length) {
                queue.add(children.item(i))
            }
        }
        return result
    }

    @Test
    fun testInspectSlide4Elements() {
        val file = File("../sample_kishore.pptx")
        ZipFile(file).use { zip ->
            // Slide 4 in presentation is ppt/slides/slide5.xml (rId7)
            val entry = zip.getEntry("ppt/slides/slide5.xml")
            val doc = zip.getInputStream(entry).use { parseXmlDocument(it) }

            val pics = doc.findDescendants("pic")
            println("Pics count: ${pics.size}")
            for (p in pics) {
                val blip = p.findFirstDescendant("blip")
                println("  Pic blip embed: ${blip?.getAttribute("r:embed")}")
            }

            val shapes = doc.findDescendants("sp")
            println("Shapes count: ${shapes.size}")
            for (sp in shapes) {
                val cNvPr = sp.findFirstDescendant("cNvPr")
                val name = cNvPr?.getAttribute("name") ?: ""
                val txBody = sp.findFirstDescendant("txBody")
                val textRuns = txBody?.findDescendants("t")?.map { it.textContent } ?: emptyList()
                val spPr = sp.findFirstDescendant("spPr")
                val solidFill = spPr?.findFirstDescendant("solidFill")?.findFirstDescendant("srgbClr")?.getAttribute("val")
                println("  Shape '$name': fill=$solidFill, textRuns=$textRuns")
            }
        }
    }
}
