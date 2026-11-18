package com.simats.intellifile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

class NativePptxParserTest {

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
    fun testParseAllSixSlides() {
        val file = if (File("sample_kishore.pptx").exists()) File("sample_kishore.pptx") else File("../sample_kishore.pptx")
        assertTrue("sample_kishore.pptx must exist", file.exists())

        ZipFile(file).use { zip ->
            val presEntry = zip.getEntry("ppt/presentation.xml")
            assertNotNull("ppt/presentation.xml must exist", presEntry)

            val slideOrderRIds = mutableListOf<String>()
            zip.getInputStream(presEntry).use { input ->
                val doc = parseXmlDocument(input)
                val sldIds = doc.findDescendants("sldId")
                for (s in sldIds) {
                    val rId = s.getAttribute("r:id").ifBlank { s.getAttribute("id") }
                    if (rId.isNotBlank()) {
                        slideOrderRIds.add(rId)
                    }
                }
            }

            assertEquals(6, slideOrderRIds.size)

            val presRelsMap = mutableMapOf<String, String>()
            val presRelsEntry = zip.getEntry("ppt/_rels/presentation.xml.rels")
            assertNotNull("ppt/_rels/presentation.xml.rels must exist", presRelsEntry)

            zip.getInputStream(presRelsEntry).use { input ->
                val doc = parseXmlDocument(input)
                val rels = doc.findDescendants("Relationship")
                for (r in rels) {
                    val id = r.getAttribute("Id")
                    val target = r.getAttribute("Target").replace('\\', '/').trim()
                    if (id.isNotBlank() && target.isNotBlank()) {
                        presRelsMap[id] = target
                    }
                }
            }

            val orderedSlidePaths = mutableListOf<String>()
            for (rId in slideOrderRIds) {
                val target = presRelsMap[rId]
                if (target != null) {
                    val fullPath = if (target.startsWith("ppt/")) target else "ppt/$target"
                    if (zip.getEntry(fullPath) != null) {
                        orderedSlidePaths.add(fullPath)
                    }
                }
            }

            assertEquals(6, orderedSlidePaths.size)
            println("All 6 slides successfully verified in order: $orderedSlidePaths")
        }
    }
}
