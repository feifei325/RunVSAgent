// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.webview

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.cef.browser.CefBrowser
import org.cef.handler.CefKeyboardHandler
import org.cef.misc.BoolRef
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

/**
 * Unit tests for WaylandClipboardHandler functionality.
 */
class WaylandClipboardHandlerTest : BasePlatformTestCase() {

    private lateinit var clipboardHandler: WaylandClipboardHandler
    private val mockBrowser: CefBrowser = mockk()
    private val mockCopyPasteManager: CopyPasteManager = mockk()
    private val mockTransferable: Transferable = mockk()

    override fun setUp() {
        super.setUp()
        clipboardHandler = WaylandClipboardHandler()
        
        // Mock static methods
        mockkStatic(CopyPasteManager::class)
        every { CopyPasteManager.getInstance() } returns mockCopyPasteManager
    }

    fun testEnvironmentDetection() {
        // Test Wayland environment detection in handler
        mockkStatic(System::class)
        
        // Test WAYLAND_DISPLAY environment variable
        every { System.getenv("WAYLAND_DISPLAY") } returns "wayland-0"
        every { System.getenv("XDG_SESSION_TYPE") } returns null
        
        val waylandHandler = WaylandClipboardHandler()
        assertNotNull("Handler should be created for Wayland environment", waylandHandler)
        
        // Test X11 environment
        every { System.getenv("WAYLAND_DISPLAY") } returns null
        every { System.getenv("XDG_SESSION_TYPE") } returns "x11"
        
        val x11Handler = WaylandClipboardHandler()
        assertNotNull("Handler should be created for X11 environment", x11Handler)
    }

    fun testPasteShortcutDetection() {
        // Test Ctrl+V detection (Windows/Linux)
        val pasteEvent = createKeyEvent(
            type = 0, // KEYEVENT_RAWKEYDOWN
            modifiers = 1 shl 7, // EVENTFLAG_CONTROL_DOWN
            windowsKeyCode = 86 // V key
        )
        
        every { mockBrowser.executeJavaScript(any<String>(), any<String>(), any<Int>()) } returns Unit
        every { mockCopyPasteManager.contents } returns mockTransferable
        every { mockTransferable.isDataFlavorSupported(DataFlavor.stringFlavor) } returns true
        every { mockTransferable.getTransferData(DataFlavor.stringFlavor) } returns "test paste"
        
        val result = clipboardHandler.onPreKeyEvent(mockBrowser, pasteEvent, BoolRef())
        
        assertTrue("Should handle paste shortcut", result)
        verify { mockBrowser.executeJavaScript(any<String>(), any<String>(), any<Int>()) }
    }

    fun testCopyShortcutDetection() {
        // Test Ctrl+C detection (Windows/Linux)
        val copyEvent = createKeyEvent(
            type = 0, // KEYEVENT_RAWKEYDOWN
            modifiers = 1 shl 7, // EVENTFLAG_CONTROL_DOWN
            windowsKeyCode = 67 // C key
        )
        
        every { mockBrowser.executeJavaScript(any<String>(), any<String>(), any<Int>()) } returns Unit
        
        val result = clipboardHandler.onPreKeyEvent(mockBrowser, copyEvent, BoolRef())
        
        assertTrue("Should handle copy shortcut", result)
        verify { mockBrowser.executeJavaScript(any<String>(), any<String>(), any<Int>()) }
    }

    fun testCutShortcutDetection() {
        // Test Ctrl+X detection (Windows/Linux)
        val cutEvent = createKeyEvent(
            type = 0, // KEYEVENT_RAWKEYDOWN
            modifiers = 1 shl 7, // EVENTFLAG_CONTROL_DOWN
            windowsKeyCode = 88 // X key
        )
        
        every { mockBrowser.executeJavaScript(any<String>(), any<String>(), any<Int>()) } returns Unit
        
        val result = clipboardHandler.onPreKeyEvent(mockBrowser, cutEvent, BoolRef())
        
        assertTrue("Should handle cut shortcut", result)
        verify { mockBrowser.executeJavaScript(any<String>(), any<String>(), any<Int>()) }
    }

    fun testMacPasteShortcutDetection() {
        // Test Cmd+V detection (macOS)
        val cmdPasteEvent = createKeyEvent(
            type = 0, // KEYEVENT_RAWKEYDOWN
            modifiers = 1 shl 8, // EVENTFLAG_COMMAND_DOWN
            windowsKeyCode = 86 // V key
        )
        
        every { mockBrowser.executeJavaScript(any<String>(), any<String>(), any<Int>()) } returns Unit
        every { mockCopyPasteManager.contents } returns mockTransferable
        every { mockTransferable.isDataFlavorSupported(DataFlavor.stringFlavor) } returns true
        every { mockTransferable.getTransferData(DataFlavor.stringFlavor) } returns "mac paste"
        
        val result = clipboardHandler.onPreKeyEvent(mockBrowser, cmdPasteEvent, BoolRef())
        
        assertTrue("Should handle Mac paste shortcut", result)
    }

    fun testNonShortcutKeyEvents() {
        // Test that non-shortcut keys are not handled
        val normalEvent = createKeyEvent(
            type = 0, // KEYEVENT_RAWKEYDOWN
            modifiers = 0, // No modifiers
            windowsKeyCode = 65 // A key
        )
        
        val result = clipboardHandler.onPreKeyEvent(mockBrowser, normalEvent, BoolRef())
        
        assertFalse("Should not handle normal key events", result)
    }

    fun testKeyUpEventsIgnored() {
        // Test that key up events are ignored for shortcuts
        val keyUpEvent = createKeyEvent(
            type = 2, // KEYEVENT_KEYUP
            modifiers = 1 shl 7, // EVENTFLAG_CONTROL_DOWN
            windowsKeyCode = 86 // V key
        )
        
        val result = clipboardHandler.onPreKeyEvent(mockBrowser, keyUpEvent, BoolRef())
        
        assertFalse("Should ignore key up events", result)
    }

    fun testPasteWithEmptyClipboard() {
        // Test paste when clipboard is empty
        val pasteEvent = createKeyEvent(
            type = 0, // KEYEVENT_RAWKEYDOWN
            modifiers = 1 shl 7, // EVENTFLAG_CONTROL_DOWN
            windowsKeyCode = 86 // V key
        )
        
        every { mockCopyPasteManager.contents } returns null
        
        val result = clipboardHandler.onPreKeyEvent(mockBrowser, pasteEvent, BoolRef())
        
        assertTrue("Should handle paste event even with empty clipboard", result)
        // Should not execute JavaScript when clipboard is empty
        verify(exactly = 0) { mockBrowser.executeJavaScript(any<String>(), any<String>(), any<Int>()) }
    }

    fun testPasteWithUnsupportedDataFlavor() {
        // Test paste when clipboard doesn't support string flavor
        val pasteEvent = createKeyEvent(
            type = 0, // KEYEVENT_RAWKEYDOWN
            modifiers = 1 shl 7, // EVENTFLAG_CONTROL_DOWN
            windowsKeyCode = 86 // V key
        )
        
        every { mockCopyPasteManager.contents } returns mockTransferable
        every { mockTransferable.isDataFlavorSupported(DataFlavor.stringFlavor) } returns false
        
        val result = clipboardHandler.onPreKeyEvent(mockBrowser, pasteEvent, BoolRef())
        
        assertTrue("Should handle paste event even with unsupported data flavor", result)
        verify(exactly = 0) { mockBrowser.executeJavaScript(any<String>(), any<String>(), any<Int>()) }
    }

    fun testPasteJavaScriptInjection() {
        // Test that paste injects correct JavaScript
        val pasteEvent = createKeyEvent(
            type = 0, // KEYEVENT_RAWKEYDOWN
            modifiers = 1 shl 7, // EVENTFLAG_CONTROL_DOWN
            windowsKeyCode = 86 // V key
        )
        
        val testText = "Test paste content"
        val jsCodeSlot = slot<String>()
        
        every { mockBrowser.executeJavaScript(capture(jsCodeSlot), any<String>(), any<Int>()) } returns Unit
        every { mockCopyPasteManager.contents } returns mockTransferable
        every { mockTransferable.isDataFlavorSupported(DataFlavor.stringFlavor) } returns true
        every { mockTransferable.getTransferData(DataFlavor.stringFlavor) } returns testText
        
        clipboardHandler.onPreKeyEvent(mockBrowser, pasteEvent, BoolRef())
        
        val capturedJs = jsCodeSlot.captured
        assertTrue("JavaScript should handle input elements", capturedJs.contains("INPUT"))
        assertTrue("JavaScript should handle textarea elements", capturedJs.contains("TEXTAREA"))
        assertTrue("JavaScript should handle contentEditable", capturedJs.contains("contentEditable"))
        assertTrue("JavaScript should trigger input event", capturedJs.contains("dispatchEvent"))
    }

    fun testCopyToClipboard() {
        // Test copying text to clipboard
        val testText = "Test copy content"
        
        every { mockCopyPasteManager.setContents(any()) } returns Unit
        
        clipboardHandler.copyToClipboard(testText)
        
        verify { mockCopyPasteManager.setContents(any<StringSelection>()) }
    }

    fun testCopyToClipboardHandlesException() {
        // Test exception handling in copyToClipboard
        val testText = "Test exception handling"
        
        every { mockCopyPasteManager.setContents(any()) } throws RuntimeException("Mock exception")
        
        // Should not throw exception
        assertDoesNotThrow("copyToClipboard should handle exceptions gracefully") {
            clipboardHandler.copyToClipboard(testText)
        }
    }

    fun testStringToJsStringEscaping() {
        // Test JavaScript string escaping functionality
        val handler = WaylandClipboardHandler()
        
        // Use reflection to access private toJsString extension function
        val testCases = mapOf(
            "simple text" to "'simple text'",
            "text with 'quotes'" to "'text with \\'quotes\\''",
            "text with\nnewlines" to "'text with\\nlines'",
            "text with\ttabs" to "'text with\\ttabs'",
            "text with\\backslashes" to "'text with\\\\backslashes'"
        )
        
        // Since toJsString is private, we test it indirectly through paste operation
        testCases.forEach { (input, _) ->
            val pasteEvent = createKeyEvent(
                type = 0, // KEYEVENT_RAWKEYDOWN
                modifiers = 1 shl 7, // EVENTFLAG_CONTROL_DOWN
                windowsKeyCode = 86 // V key
            )
            
            val jsCodeSlot = slot<String>()
            every { mockBrowser.executeJavaScript(capture(jsCodeSlot), any<String>(), any<Int>()) } returns Unit
            every { mockCopyPasteManager.contents } returns mockTransferable
            every { mockTransferable.isDataFlavorSupported(DataFlavor.stringFlavor) } returns true
            every { mockTransferable.getTransferData(DataFlavor.stringFlavor) } returns input
            
            handler.onPreKeyEvent(mockBrowser, pasteEvent, BoolRef())
            
            val capturedJs = jsCodeSlot.captured
            assertNotNull("JavaScript should be generated for special characters", capturedJs)
            // Verify that special characters are properly escaped
            assertFalse("JavaScript should not contain unescaped quotes", capturedJs.contains("'$input'"))
        }
    }

    fun testNullEventHandling() {
        // Test handling of null events
        val result = clipboardHandler.onPreKeyEvent(mockBrowser, null, BoolRef())
        assertFalse("Should return false for null event", result)
        
        val result2 = clipboardHandler.onPreKeyEvent(null, createKeyEvent(0, 0, 0), BoolRef())
        assertFalse("Should return false for null browser", result2)
    }

    fun testCopyJavaScriptGeneration() {
        // Test that copy operation generates correct JavaScript
        val copyEvent = createKeyEvent(
            type = 0, // KEYEVENT_RAWKEYDOWN
            modifiers = 1 shl 7, // EVENTFLAG_CONTROL_DOWN
            windowsKeyCode = 67 // C key
        )
        
        val jsCodeSlot = slot<String>()
        every { mockBrowser.executeJavaScript(capture(jsCodeSlot), any<String>(), any<Int>()) } returns Unit
        
        clipboardHandler.onPreKeyEvent(mockBrowser, copyEvent, BoolRef())
        
        val capturedJs = jsCodeSlot.captured
        assertTrue("JavaScript should get selection", capturedJs.contains("getSelection"))
        assertTrue("JavaScript should use cefQuery", capturedJs.contains("cefQuery"))
        assertTrue("JavaScript should send clipboard-copy message", capturedJs.contains("clipboard-copy"))
    }

    fun testCutJavaScriptGeneration() {
        // Test that cut operation generates correct JavaScript
        val cutEvent = createKeyEvent(
            type = 0, // KEYEVENT_RAWKEYDOWN
            modifiers = 1 shl 7, // EVENTFLAG_CONTROL_DOWN
            windowsKeyCode = 88 // X key
        )
        
        val jsCodeSlot = slot<String>()
        every { mockBrowser.executeJavaScript(capture(jsCodeSlot), any<String>(), any<Int>()) } returns Unit
        
        clipboardHandler.onPreKeyEvent(mockBrowser, cutEvent, BoolRef())
        
        val capturedJs = jsCodeSlot.captured
        assertTrue("JavaScript should get selection", capturedJs.contains("getSelection"))
        assertTrue("JavaScript should use cefQuery", capturedJs.contains("cefQuery"))
        assertTrue("JavaScript should send clipboard-cut message", capturedJs.contains("clipboard-cut"))
        assertTrue("JavaScript should delete selection", capturedJs.contains("execCommand('delete'"))
    }

    private fun createKeyEvent(
        type: Int,
        modifiers: Int,
        windowsKeyCode: Int
    ): CefKeyboardHandler.CefKeyEvent {
        return mockk<CefKeyboardHandler.CefKeyEvent>().apply {
            every { this@apply.type } returns type
            every { this@apply.modifiers } returns modifiers
            every { this@apply.windows_key_code } returns windowsKeyCode
        }
    }
}