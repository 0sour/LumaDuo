package com.sour.lumaduo.nvstream.http

import com.google.gson.JsonSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.StringReader

class NvAppCmdListTest {
    @Test
    fun parsesCommandsWithoutChangingIdsNamesOrExtraFields() {
        val app = NvApp()
        app.setCmdList("""[{"id":"001","name":"启动串流 🎮","extra":{"enabled":true}},{"id":"quit","name":"Quit"}]""")

        val commands = app.cmdList!!
        assertEquals(2, commands.size())
        assertEquals("001", commands[0].asJsonObject["id"].asString)
        assertEquals("启动串流 🎮", commands[0].asJsonObject["name"].asString)
        assertEquals(true, commands[0].asJsonObject["extra"].asJsonObject["enabled"].asBoolean)
        assertEquals("quit", commands[1].asJsonObject["id"].asString)
    }

    @Test
    fun emptyArrayRemainsPresentAndClearsPreviousCommands() {
        val app = NvApp()
        app.setCmdList("""[{"id":"old","name":"Old"}]""")
        app.setCmdList("[]")

        assertNotNull(app.cmdList)
        assertEquals(0, app.cmdList!!.size())
    }

    @Test
    fun blankInputRetainsTheExistingAbsentCommandsBehavior() {
        val app = NvApp()
        for (input in listOf("", " \r\n\t")) {
            app.setCmdList("[]")
            app.setCmdList(input)
            assertNull(app.cmdList)
        }
    }

    @Test
    fun invalidJsonAndNonArrayRootsAreRejectedWithoutReplacingCommands() {
        val app = NvApp()
        app.setCmdList("""[{"id":"old","name":"Old"}]""")
        val previous = app.cmdList

        for (input in listOf("[", "[{\"id\":]", "[] []", "[] trailing", "{}", "42", "true", "\"text\"", "null")) {
            assertThrows("Input: $input", JsonSyntaxException::class.java) {
                app.setCmdList(input)
            }
            assertSame(previous, app.cmdList)
        }
    }

    @Test
    fun appListXmlParsesFoundationFactsWithoutConsumingSuperCommands() {
        val apps = NvHTTP.getAppListByReader(StringReader(
            """<root status_code="200"><App><AppTitle>Slay the Spire 2</AppTitle><ID>2</ID><IsHdrSupported>0</IsHdrSupported><FsKind>game</FsKind><FsSource>steam</FsSource><FsDefault>1</FsDefault><FsLastPlayed>1794390000</FsLastPlayed><FsPlaySeconds>7200</FsPlaySeconds><FsArtVersion>123</FsArtVersion><FsArt>poster,hero,logo,icon</FsArt><SuperCmds>[{"id":"001","name":"Start"}]</SuperCmds></App></root>"""
        ))

        assertEquals(1, apps.size)
        val app = apps[0]
        assertEquals("game", app.fsKind)
        assertEquals("steam", app.fsSource)
        assertEquals(true, app.fsDefault)
        assertEquals(1794390000L, app.fsLastPlayed)
        assertEquals(7200L, app.fsPlaySeconds)
        assertEquals("123", app.fsArtVersion)
        assertEquals(setOf("poster", "hero", "logo", "icon"), app.fsArtTypes)
        assertEquals(true, app.hasFsArt("hero"))
        assertNotNull(app.cmdList)
    }

    @Test
    fun appListXmlParsesCommandsAndKeepsNullOrMissingCommandsAbsent() {
        val apps = NvHTTP.getAppListByReader(StringReader(
            """<root status_code="200"><App><AppTitle>Game</AppTitle><ID>1</ID><IsHdrSupported>1</IsHdrSupported><SuperCmds>[{"id":"001","name":"Start &amp; Play"}]</SuperCmds></App><App><AppTitle>Desktop</AppTitle><ID>2</ID><SuperCmds>null</SuperCmds></App><App><AppTitle>Other</AppTitle><ID>3</ID></App></root>"""
        ))

        assertEquals(3, apps.size)
        assertEquals("Game", apps[0].appName)
        assertEquals(1, apps[0].appId)
        assertEquals(true, apps[0].hdrSupported)
        val command = apps[0].cmdList!![0].asJsonObject
        assertEquals("001", command["id"].asString)
        assertEquals("Start & Play", command["name"].asString)
        assertNull(apps[1].cmdList)
        assertNull(apps[2].cmdList)
    }

}
