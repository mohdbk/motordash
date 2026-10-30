package app.motordash.files

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MoveContentsTest {
    private val base = Files.createTempDirectory("move").toFile()

    @After
    fun tearDown() {
        base.deleteRecursively()
    }

    @Test
    fun `moves files and folders, keeping both on a name clash`() {
        val from = File(base, "Old").apply { mkdirs() }
        val to = File(base, "New").apply { mkdirs() }
        File(from, "song.mp3").writeText("old")
        File(from, "Music/a.mp3").apply { parentFile!!.mkdirs() }.writeText("a")
        File(to, "song.mp3").writeText("already there")

        assertTrue(moveContents(from, to))
        assertEquals(0, from.listFiles()!!.size)
        assertEquals("already there", File(to, "song.mp3").readText())
        assertEquals("old", File(to, "song (1).mp3").readText())
        assertEquals("a", File(to, "Music/a.mp3").readText())
    }

    @Test
    fun `a new folder inside the old one isn't moved into itself`() {
        val from = File(base, "Download").apply { mkdirs() }
        File(from, "x.apk").writeText("x")
        val to = File(from, "MotorDash").apply { mkdirs() }

        assertTrue(moveContents(from, to))
        assertEquals("x", File(to, "x.apk").readText())
        assertTrue(to.isDirectory)
        assertEquals(listOf("MotorDash"), from.list()!!.toList())
    }
}
