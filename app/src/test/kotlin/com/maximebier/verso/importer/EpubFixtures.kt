package com.maximebier.verso.importer

import java.io.File
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Fabrique d'EPUB de test, générés à la volée avec java.util.zip. */
object EpubFixtures {

    const val CANDIDE = "epub/candide-gutenberg-4650.epub"
    const val ALICE = "epub/alice-gutenberg-11.epub"

    private const val CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

    private const val NAV_XHTML = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Sommaire</title></head>
<body><nav epub:type="toc"><ol><li><a href="chapitre1.xhtml">Chapitre premier</a></li></ol></nav></body>
</html>"""

    private const val CHAPTER_XHTML = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>Chapitre premier</title></head>
<body>
<h1>Chapitre premier</h1>
<p>Il était une fois un petit livre de test qui ne contenait que quelques phrases très simples.</p>
<p>Il servait à vérifier que Verso importe les fichiers les plus modestes sans jamais planter.</p>
</body>
</html>"""

    private const val ADEPT_ENCRYPTION_XML = """<?xml version="1.0" encoding="UTF-8"?>
<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
            xmlns:enc="http://www.w3.org/2001/04/xmlenc#"
            xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
  <enc:EncryptedData>
    <enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/>
    <ds:KeyInfo>
      <resource xmlns="http://ns.adobe.com/adept">urn:uuid:00000000-0000-0000-0000-000000000000</resource>
    </ds:KeyInfo>
    <enc:CipherData><enc:CipherReference URI="OEBPS/chapitre1.xhtml"/></enc:CipherData>
  </enc:EncryptedData>
</encryption>"""

    private const val LCP_LICENSE = """{"id":"00000000-0000-0000-0000-000000000000","provider":"https://exemple.invalid"}"""

    /** PNG 1×1 valide. */
    private val PNG_1X1: ByteArray = Base64.getDecoder()
        .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")

    private fun opf(title: String?, author: String?): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">""").append('\n')
        append("""  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""").append('\n')
        append("""    <dc:identifier id="uid">urn:uuid:11111111-2222-3333-4444-555555555555</dc:identifier>""").append('\n')
        if (title != null) append("    <dc:title>").append(title).append("</dc:title>\n")
        if (author != null) append("    <dc:creator>").append(author).append("</dc:creator>\n")
        append("    <dc:language>fr</dc:language>\n")
        append("""    <meta property="dcterms:modified">2026-09-25T00:00:00Z</meta>""").append('\n')
        append("  </metadata>\n")
        append("  <manifest>\n")
        append("""    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""").append('\n')
        append("""    <item id="c1" href="chapitre1.xhtml" media-type="application/xhtml+xml"/>""").append('\n')
        append("  </manifest>\n")
        append("""  <spine><itemref idref="c1"/></spine>""").append('\n')
        append("</package>\n")
    }

    /** EPUB 3 minimal : un chapitre, sans couverture ; titre et auteur au choix (null = absent). */
    fun epub(
        target: File,
        title: String? = "Un petit livre",
        author: String? = null,
        withMimetype: Boolean = true,
        extraEntries: Map<String, ByteArray> = emptyMap(),
    ): File {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream()).use { zip ->
            if (withMimetype) {
                val bytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
                val entry = ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("META-INF/container.xml", CONTAINER_XML.toByteArray())
            put("OEBPS/content.opf", opf(title, author).toByteArray())
            put("OEBPS/nav.xhtml", NAV_XHTML.toByteArray())
            put("OEBPS/chapitre1.xhtml", CHAPTER_XHTML.toByteArray())
            extraEntries.forEach { (name, bytes) -> put(name, bytes) }
        }
        return target
    }

    fun adobeDrm(target: File): File =
        epub(target, extraEntries = mapOf("META-INF/encryption.xml" to ADEPT_ENCRYPTION_XML.toByteArray()))

    fun lcpDrm(target: File): File =
        epub(target, extraEntries = mapOf("META-INF/license.lcpl" to LCP_LICENSE.toByteArray()))

    /** Fichier texte renommé en .epub. */
    fun textFile(target: File): File {
        target.parentFile?.mkdirs()
        target.writeText("Ceci n’est pas un livre, seulement un fichier texte renommé.\n")
        return target
    }

    /** ZIP ne contenant que des images (ressemble à une BD, pas à un EPUB). */
    fun imagesZip(target: File): File {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream()).use { zip ->
            listOf("001.png", "002.png").forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(PNG_1X1)
                zip.closeEntry()
            }
        }
        return target
    }

    /** ZIP qui annonce un EPUB (container.xml) mais sans OPF : endommagé. */
    fun brokenEpub(target: File): File {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(CONTAINER_XML.toByteArray())
            zip.closeEntry()
        }
        return target
    }

    /** Copie une ressource du corpus (`epub/…`) vers `target`. */
    fun resource(path: String, target: File): File {
        target.parentFile?.mkdirs()
        val stream = requireNotNull(EpubFixtures::class.java.classLoader?.getResourceAsStream(path)) { "Ressource absente : $path" }
        stream.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }
}
