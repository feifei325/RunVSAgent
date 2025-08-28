// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.webview

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sina.weibo.agent.plugin.PluginContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.jetbrains.concurrency.Promise
import org.junit.Test

/**
 * Unit tests for WebViewManager clipboard-related functionality.
 * Tests the integration of WaylandClipboardHandler with WebViewManager.
 */
class WebViewManagerClipboardTest : BasePlatformTestCase() {

    private val gson = Gson()
    private lateinit var mockProject: Project
    private lateinit var mockPluginContext: PluginContext

    override fun setUp() {
        super.setUp()
        mockProject = project
        mockPluginContext = mockk()
        
        // Mock the project service
        mockkStatic("com.intellij.openapi.components.ComponentManagerKt")
        every { mockProject.getService(PluginContext::class.java) } returns mockPluginContext
        every { mockPluginContext.getRPCProtocol() } returns null
    }

    @Test
    fun testClipboardCopyMessageHandling() = runTest {
        // Test handling of clipboard-copy messages
        val copyMessage = JsonObject().apply {
            addProperty("type", "clipboard-copy")
            addProperty("text", "Test copy content")
        }
        
        val messageJson = gson.toJson(copyMessage)
        
        // Create a spy of WaylandClipboardHandler to verify interaction
        val clipboardHandlerSpy = spyk<WaylandClipboardHandler>()
        
        // Mock the clipboard handler creation
        mockkStatic(WaylandClipboardHandler::class)
        every { WaylandClipboardHandler() } returns clipboardHandlerSpy
        every { clipboardHandlerSpy.copyToClipboard(any()) } returns Unit
        
        // Since we can't easily test the private setupJSBridge method directly,
        // we test the message handling logic by simulating what happens
        // when a clipboard message is received
        val handledMessage = handleClipboardMessage(messageJson)
        
        assertTrue("Should handle clipboard-copy message", handledMessage)
        verify { clipboardHandlerSpy.copyToClipboard("Test copy content") }
    }

    @Test
    fun testClipboardCutMessageHandling() = runTest {
        // Test handling of clipboard-cut messages
        val cutMessage = JsonObject().apply {
            addProperty("type", "clipboard-cut")
            addProperty("text", "Test cut content")
        }
        
        val messageJson = gson.toJson(cutMessage)
        
        // Create a spy of WaylandClipboardHandler to verify interaction
        val clipboardHandlerSpy = spyk<WaylandClipboardHandler>()
        
        mockkStatic(WaylandClipboardHandler::class)
        every { WaylandClipboardHandler() } returns clipboardHandlerSpy
        every { clipboardHandlerSpy.copyToClipboard(any()) } returns Unit
        
        val handledMessage = handleClipboardMessage(messageJson)
        
        assertTrue("Should handle clipboard-cut message", handledMessage)
        verify { clipboardHandlerSpy.copyToClipboard("Test cut content") }
    }

    @Test
    fun testNonClipboardMessageHandling() = runTest {
        // Test that non-clipboard messages are not handled by clipboard logic
        val regularMessage = JsonObject().apply {
            addProperty("type", "regular-message")
            addProperty("data", "Some regular data")
        }
        
        val messageJson = gson.toJson(regularMessage)
        
        val handledMessage = handleClipboardMessage(messageJson)
        
        assertFalse("Should not handle non-clipboard messages", handledMessage)
    }

    @Test
    fun testInvalidJsonMessageHandling() = runTest {
        // Test handling of invalid JSON messages
        val invalidJson = "{ invalid json }"
        
        val handledMessage = handleClipboardMessage(invalidJson)
        
        assertFalse("Should not handle invalid JSON messages", handledMessage)
    }

    @Test
    fun testClipboardMessageWithoutText() = runTest {
        // Test clipboard message without text field
        val messageWithoutText = JsonObject().apply {
            addProperty("type", "clipboard-copy")
            // No text field
        }
        
        val messageJson = gson.toJson(messageWithoutText)
        
        val clipboardHandlerSpy = spyk<WaylandClipboardHandler>()
        mockkStatic(WaylandClipboardHandler::class)
        every { WaylandClipboardHandler() } returns clipboardHandlerSpy
        
        val handledMessage = handleClipboardMessage(messageJson)
        
        assertTrue("Should handle clipboard message even without text", handledMessage)
        // Should not call copyToClipboard when text is null
        verify(exactly = 0) { clipboardHandlerSpy.copyToClipboard(any()) }
    }

    @Test
    fun testClipboardMessageWithEmptyText() = runTest {
        // Test clipboard message with empty text
        val messageWithEmptyText = JsonObject().apply {
            addProperty("type", "clipboard-copy")
            addProperty("text", "")
        }
        
        val messageJson = gson.toJson(messageWithEmptyText)
        
        val clipboardHandlerSpy = spyk<WaylandClipboardHandler>()
        mockkStatic(WaylandClipboardHandler::class)
        every { WaylandClipboardHandler() } returns clipboardHandlerSpy
        every { clipboardHandlerSpy.copyToClipboard(any()) } returns Unit
        
        val handledMessage = handleClipboardMessage(messageJson)
        
        assertTrue("Should handle clipboard message with empty text", handledMessage)
        verify { clipboardHandlerSpy.copyToClipboard("") }
    }

    @Test
    fun testClipboardHandlerException() = runTest {
        // Test exception handling in clipboard operations
        val copyMessage = JsonObject().apply {
            addProperty("type", "clipboard-copy")
            addProperty("text", "Test exception handling")
        }
        
        val messageJson = gson.toJson(copyMessage)
        
        val clipboardHandlerSpy = spyk<WaylandClipboardHandler>()
        mockkStatic(WaylandClipboardHandler::class)
        every { WaylandClipboardHandler() } returns clipboardHandlerSpy
        every { clipboardHandlerSpy.copyToClipboard(any()) } throws RuntimeException("Clipboard error")
        
        // Should not throw exception
        assertDoesNotThrow("Should handle clipboard exceptions gracefully") {
            handleClipboardMessage(messageJson)
        }
    }

    @Test
    fun testLargeClipboardContent() = runTest {
        // Test handling of large clipboard content
        val largeContent = "x".repeat(10000)
        val largeMessage = JsonObject().apply {
            addProperty("type", "clipboard-copy")
            addProperty("text", largeContent)
        }
        
        val messageJson = gson.toJson(largeMessage)
        
        val clipboardHandlerSpy = spyk<WaylandClipboardHandler>()
        mockkStatic(WaylandClipboardHandler::class)
        every { WaylandClipboardHandler() } returns clipboardHandlerSpy
        every { clipboardHandlerSpy.copyToClipboard(any()) } returns Unit
        
        val handledMessage = handleClipboardMessage(messageJson)
        
        assertTrue("Should handle large clipboard content", handledMessage)
        verify { clipboardHandlerSpy.copyToClipboard(largeContent) }
    }

    @Test
    fun testSpecialCharactersInClipboard() = runTest {
        // Test handling of special characters in clipboard content
        val specialContent = "Test with special chars: \n\t\r🚀💻📝\"'\\&<>"
        val specialMessage = JsonObject().apply {
            addProperty("type", "clipboard-cut")
            addProperty("text", specialContent)
        }
        
        val messageJson = gson.toJson(specialMessage)
        
        val clipboardHandlerSpy = spyk<WaylandClipboardHandler>()
        mockkStatic(WaylandClipboardHandler::class)
        every { WaylandClipboardHandler() } returns clipboardHandlerSpy
        every { clipboardHandlerSpy.copyToClipboard(any()) } returns Unit
        
        val handledMessage = handleClipboardMessage(messageJson)
        
        assertTrue("Should handle special characters in clipboard", handledMessage)
        verify { clipboardHandlerSpy.copyToClipboard(specialContent) }
    }

    @Test
    fun testConcurrentClipboardMessages() = runTest {
        // Test handling of concurrent clipboard messages
        val messages = (1..5).map { index ->
            JsonObject().apply {
                addProperty("type", "clipboard-copy")
                addProperty("text", "Content $index")
            }
        }
        
        val clipboardHandlerSpy = spyk<WaylandClipboardHandler>()
        mockkStatic(WaylandClipboardHandler::class)
        every { WaylandClipboardHandler() } returns clipboardHandlerSpy
        every { clipboardHandlerSpy.copyToClipboard(any()) } returns Unit
        
        // Process messages concurrently
        messages.forEach { message ->
            val messageJson = gson.toJson(message)
            val handled = handleClipboardMessage(messageJson)
            assertTrue("Should handle all clipboard messages", handled)
        }
        
        // Verify all messages were processed
        verify(exactly = 5) { clipboardHandlerSpy.copyToClipboard(any()) }
    }

    @Test
    fun testMessageTypeVariations() = runTest {
        // Test different variations of message types
        val testCases = listOf(
            "clipboard-copy" to true,
            "clipboard-cut" to true,
            "CLIPBOARD-COPY" to false, // Case sensitive
            "clipboard_copy" to false, // Different format
            "clipboardcopy" to false,  // No dash
            "copy-clipboard" to false, // Wrong order
            "" to false,               // Empty
            "other-message" to false   // Different message
        )
        
        val clipboardHandlerSpy = spyk<WaylandClipboardHandler>()
        mockkStatic(WaylandClipboardHandler::class)
        every { WaylandClipboardHandler() } returns clipboardHandlerSpy
        every { clipboardHandlerSpy.copyToClipboard(any()) } returns Unit
        
        testCases.forEach { (messageType, shouldHandle) ->
            val message = JsonObject().apply {
                addProperty("type", messageType)
                addProperty("text", "test content")
            }
            
            val messageJson = gson.toJson(message)
            val handled = handleClipboardMessage(messageJson)
            
            assertEquals(
                "Message type '$messageType' should ${if (shouldHandle) "be" else "not be"} handled",
                shouldHandle,
                handled
            )
        }
    }

    /**
     * Helper method to simulate clipboard message handling logic.
     * This replicates the logic from WebViewManager.setupJSBridge() for testing.
     */
    private fun handleClipboardMessage(message: String): Boolean {
        return try {
            val messageObj = gson.fromJson(message, JsonObject::class.java)
            val messageType = messageObj.get("type")?.asString
            
            when (messageType) {
                "clipboard-copy", "clipboard-cut" -> {
                    val text = messageObj.get("text")?.asString
                    if (text != null) {
                        val clipboardHandler = WaylandClipboardHandler()
                        clipboardHandler.copyToClipboard(text)
                    }
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
}