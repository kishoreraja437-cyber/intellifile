package com.simats.intellifile

import com.simats.intellifile.data.manager.DocumentContentParser
import com.simats.intellifile.data.manager.ParsedDocumentContent
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentContentParserTest {

    @Test
    fun testParsePptx_extractsSlidesAndBulletPoints() {
        // Construct a synthetic PPTX zip archive in memory
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)

        // Slide 1 XML (Title Slide)
        val slide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                   xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp>
                            <p:nvSpPr>
                                <p:nvPr>
                                    <p:ph type="title"/>
                                </p:nvPr>
                            </p:nvSpPr>
                            <p:txBody>
                                <a:p>
                                    <a:r><a:t>IntelliFile AI Architecture</a:t></a:r>
                                </a:p>
                            </p:txBody>
                        </p:sp>
                        <p:sp>
                            <p:txBody>
                                <a:p>
                                    <a:r><a:t>Enterprise File Management</a:t></a:r>
                                </a:p>
                            </p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        // Slide 2 XML (Content Slide with multiple bullets)
        val slide2Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                   xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp>
                            <p:nvSpPr>
                                <p:nvPr>
                                    <p:ph type="title"/>
                                </p:nvPr>
                            </p:nvSpPr>
                            <p:txBody>
                                <a:p>
                                    <a:r><a:t>Key Features</a:t></a:r>
                                </a:p>
                            </p:txBody>
                        </p:sp>
                        <p:sp>
                            <p:txBody>
                                <a:p>
                                    <a:r><a:t>1. AI Powered Summaries</a:t></a:r>
                                </a:p>
                                <a:p>
                                    <a:r><a:t>2. In-App Presentation Viewer</a:t></a:r>
                                </a:p>
                                <a:p>
                                    <a:r><a:t>3. Priority Categorization</a:t></a:r>
                                </a:p>
                            </p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        // Write slide 1
        zos.putNextEntry(ZipEntry("ppt/slides/slide1.xml"))
        zos.write(slide1Xml.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()

        // Write slide 2 (with backslash to test Windows paths!)
        zos.putNextEntry(ZipEntry("ppt\\slides\\slide2.xml"))
        zos.write(slide2Xml.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()

        zos.close()

        val pptxBytes = baos.toByteArray()
        val result = DocumentContentParser.parsePptx(pptxBytes, "test_presentation.pptx")

        assertNotNull("PPTX parser must return a result", result)
        assertEquals("Must parse 2 slides", 2, result!!.totalSlides)

        // Verify Slide 1
        val slide1 = result.slides[0]
        assertEquals("Slide 1 number", 1, slide1.slideNumber)
        assertTrue("Slide 1 title must match", slide1.title.contains("IntelliFile AI Architecture"))
        assertTrue("Slide 1 points must contain subtitle", slide1.points.any { it.contains("Enterprise File Management") })

        // Verify Slide 2
        val slide2 = result.slides[1]
        assertEquals("Slide 2 number", 2, slide2.slideNumber)
        assertTrue("Slide 2 title must match", slide2.title.contains("Key Features"))
        assertEquals("Slide 2 points count", 3, slide2.points.size)
        assertTrue("Slide 2 first bullet", slide2.points[0].contains("AI Powered Summaries"))
        assertTrue("Slide 2 second bullet", slide2.points[1].contains("In-App Presentation Viewer"))
        assertTrue("Slide 2 third bullet", slide2.points[2].contains("Priority Categorization"))
    }

    @Test
    fun testParsePpt_binaryRecordExtraction() {
        val baos = ByteArrayOutputStream()

        // Write Slide 1 Container (recType = 1006)
        baos.write(byteArrayOf(0x00, 0x00)) // ver/inst
        baos.write(byteArrayOf(0xEE.toByte(), 0x03)) // recType 1006 (0x03EE)
        baos.write(byteArrayOf(0x00, 0x00, 0x00, 0x00)) // len 0

        // Write Slide 1 Title (recType = 4000 UTF-16LE TextCharsAtom)
        val s1Title = "Slide 1: Overview\nIntroduction to IntelliFile".toByteArray(StandardCharsets.UTF_16LE)
        baos.write(byteArrayOf(0x00, 0x00)) // ver/inst
        baos.write(byteArrayOf(0xA0.toByte(), 0x0F)) // recType 4000 (0x0FA0)
        baos.write(byteArrayOf((s1Title.size and 0xFF).toByte(), ((s1Title.size shr 8) and 0xFF).toByte(), 0x00, 0x00))
        baos.write(s1Title)

        // Write Slide 2 Container (recType = 1006)
        baos.write(byteArrayOf(0x00, 0x00))
        baos.write(byteArrayOf(0xEE.toByte(), 0x03))
        baos.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))

        // Write Slide 2 Text (recType = 4008 ANSI TextBytesAtom)
        val s2Text = "Slide 2: Benefits\nFast and Responsive\nOffline Reading".toByteArray(StandardCharsets.ISO_8859_1)
        baos.write(byteArrayOf(0x00, 0x00))
        baos.write(byteArrayOf(0xA8.toByte(), 0x0F)) // recType 4008 (0x0FA8)
        baos.write(byteArrayOf((s2Text.size and 0xFF).toByte(), ((s2Text.size shr 8) and 0xFF).toByte(), 0x00, 0x00))
        baos.write(s2Text)

        val pptBytes = baos.toByteArray()
        val result = DocumentContentParser.parsePpt(pptBytes, "legacy_deck.ppt")

        assertNotNull("Binary PPT parser must return a result", result)
        assertEquals("Must parse 2 slides", 2, result!!.totalSlides)

        val slide1 = result.slides[0]
        assertTrue("Slide 1 title", slide1.title.contains("Slide 1: Overview"))
        assertTrue("Slide 1 points", slide1.points.any { it.contains("Introduction to IntelliFile") })

        val slide2 = result.slides[1]
        assertTrue("Slide 2 title", slide2.title.contains("Slide 2: Benefits"))
        assertTrue("Slide 2 points", slide2.points.any { it.contains("Fast and Responsive") })
    }

    @Test
    fun testScanTextFromBinary_extractsReadableText() {
        val s = "IntelliFile Document Management System"
        val bytes = s.toByteArray(StandardCharsets.UTF_16LE)
        val extracted = DocumentContentParser.scanTextFromBinary(bytes)

        assertTrue("Extracted text must not be empty", extracted.isNotEmpty())
        assertTrue("Extracted text must contain key words", extracted.any { it.contains("IntelliFile") })
    }

    @Test
    fun testParseRealCloudLecturePptx() {
        val file = java.io.File("""C:\xampp\htdocs\intellifile_backend\uploads\documents\dd88b0657cf5d618ac1f5caf_cloud_lecture.pptx""")
        if (file.exists()) {
            val bytes = file.readBytes()
            val parsed = DocumentContentParser.parsePptx(bytes, file.name)
            assertNotNull("Should parse cloud lecture", parsed)
            assertEquals(2, parsed!!.totalSlides)
            assertTrue("Slide 1 title contains Cloud", parsed.slides[0].title.contains("Cloud Computing"))
            assertTrue("Slide 1 points contain Docker", parsed.slides[0].points.any { it.contains("Docker") })
            assertTrue("Slide 2 points contain AWS or EC2", parsed.slides[1].points.any { it.contains("Amazon") || it.contains("EC2") } || parsed.slides[1].title.contains("Amazon"))
        }
    }
}

