// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

/**
 * Unit tests for MainThreadClipboardShape functionality with Wayland support.
 */
class MainThreadClipboardShapeTest : BasePlatformTestCase() {

    private lateinit var clipboard: MainThreadClipboard
    private val mockCopyPasteManager: CopyPasteManager = mockk()
    private val mockSystemClipboard: Clipboard = mockk()
    private val mockTransferable: Transferable = mockk()

    override fun setUp() {
        super.setUp()
        clipboard = MainThreadClipboard()
        
        // Mock static methods
        mockkStatic(CopyPasteManager::class)
        mockkStatic(Toolkit::class)
        
        every { CopyPasteManager.getInstance() } returns mockCopyPasteManager
        every { Toolkit.getDefaultToolkit().systemClipboard } returns mockSystemClipboard
    }

    fun testEnvironmentDetection() {
        // Test Wayland environment detection
        mockkStatic(System::class)
        
        // Test WAYLAND_DISPLAY environment variable
        every { System.getenv("WAYLAND_DISPLAY") } returns "wayland-0"
        every { System.getenv("XDG_SESSION_TYPE") } returns null
        
        val waylandClipboard = MainThreadClipboard()
        assertNotNull("Clipboard should be created for Wayland environment", waylandClipboard)
        
        // Test XDG_SESSION_TYPE environment variable
        every { System.getenv("WAYLAND_DISPLAY") } returns null
        every { System.getenv("XDG_SESSION_TYPE") } returns "wayland"
        
        val waylandClipboard2 = MainThreadClipboard()
        assertNotNull("Clipboard should be created for Wayland environment via XDG_SESSION_TYPE", waylandClipboard2)
        
        // Test X11 environment (no Wayland variables)
        every { System.getenv("WAYLAND_DISPLAY") } returns null
        every { System.getenv("XDG_SESSION_TYPE") } returns "x11"
        
        val x11Clipboard = MainThreadClipboard()
        assertNotNull("Clipboard should be created for X11 environment", x11Clipboard)
    }

    fun testReadTextWithCopyPasteManager() {
        // Test successful read using CopyPasteManager
        val expectedText = "test clipboard content"
        
        every { mockCopyPasteManager.contents } returns mockTransferable
        every { mockTransferable.isDataFlavorSupported(DataFlavor.stringFlavor) } returns true
        every { mockTransferable.getTransferData(DataFlavor.stringFlavor) } returns expectedText
        
        val result = clipboard.readText()
        
        assertEquals("Should return text from CopyPasteManager", expectedText, result)
        verify { mockCopyPasteManager.contents }
        verify { mockTransferable.getTransferData(DataFlavor.stringFlavor) }
    }

    fun testReadTextWithAWTFallback() {
        // Test fallback to AWT when CopyPasteManager fails
        val expectedText = "awt fallback text"
        
        // Mock CopyPasteManager to throw exception
        every { mockCopyPasteManager.contents } throws RuntimeException("CopyPasteManager failed")
        
        // Mock AWT clipboard success
        every { mockSystemClipboard.getContents(null) } returns mockTransferable
        every { mockTransferable.isDataFlavorSupported(DataFlavor.stringFlavor) } returns true
        every { mockTransferable.getTransferData(DataFlavor.stringFlavor) } returns expectedText
        
        val result = clipboard.readText()
        
        assertEquals("Should return text from AWT fallback", expectedText, result)
        verify { mockSystemClipboard.getContents(null) }
    }

    fun testReadTextReturnsNullWhenNoTextAvailable() {
        // Test when no text is available in clipboard
        every { mockCopyPasteManager.contents } returns mockTransferable
        every { mockTransferable.isDataFlavorSupported(DataFlavor.stringFlavor) } returns false
        
        // Mock AWT fallback also returns no text
        every { mockSystemClipboard.getContents(null) } returns mockTransferable
        
        val result = clipboard.readText()
        
        assertNull("Should return null when no text is available", result)
    }

    fun testReadTextHandlesException() {
        // Test exception handling when both CopyPasteManager and AWT fail
        every { mockCopyPasteManager.contents } throws RuntimeException("CopyPasteManager failed")
        every { mockSystemClipboard.getContents(null) } throws RuntimeException("AWT failed")
        
        val result = clipboard.readText()
        
        assertNull("Should return null when both clipboard methods fail", result)
    }

    fun testWriteTextWithCopyPasteManager() {
        // Test successful write using CopyPasteManager
        val textToWrite = "test write content"
        
        every { mockCopyPasteManager.setContents(any()) } returns Unit
        
        clipboard.writeText(textToWrite)
        
        verify { mockCopyPasteManager.setContents(any<StringSelection>()) }
    }

    fun testWriteTextWithAWTFallback() {
        // Test fallback to AWT when CopyPasteManager fails
        val textToWrite = "awt fallback write"
        
        // Mock CopyPasteManager to throw exception
        every { mockCopyPasteManager.setContents(any()) } throws RuntimeException("CopyPasteManager failed")
        
        // Mock AWT success
        every { mockSystemClipboard.setContents(any(), any()) } returns Unit
        
        clipboard.writeText(textToWrite)
        
        verify { mockSystemClipboard.setContents(any<StringSelection>(), any()) }
    }

    fun testWriteTextHandlesNullValue() {
        // Test writing null value does nothing
        clipboard.writeText(null)
        
        // Verify no interactions with clipboard
        verify(exactly = 0) { mockCopyPasteManager.setContents(any()) }
        verify(exactly = 0) { mockSystemClipboard.setContents(any(), any()) }
    }

    fun testWriteTextHandlesEmptyString() {
        // Test writing empty string
        val emptyText = ""
        
        every { mockCopyPasteManager.setContents(any()) } returns Unit
        
        clipboard.writeText(emptyText)
        
        verify { mockCopyPasteManager.setContents(any<StringSelection>()) }
    }

    fun testWriteTextHandlesException() {
        // Test exception handling when both CopyPasteManager and AWT fail
        val textToWrite = "test exception"
        
        every { mockCopyPasteManager.setContents(any()) } throws RuntimeException("CopyPasteManager failed")
        every { mockSystemClipboard.setContents(any(), any()) } throws RuntimeException("AWT failed")
        
        // Should not throw exception
        clipboard.writeText(textToWrite)
        
        verify { mockCopyPasteManager.setContents(any<StringSelection>()) }
        verify { mockSystemClipboard.setContents(any<StringSelection>(), any()) }
    }

    fun testDispose() {
        // Test disposal of clipboard resources
        assertDoesNotThrow("Dispose should not throw exception") {
            clipboard.dispose()
        }
    }

    fun testClipboardInterface() {
        // Test that MainThreadClipboard implements the interface correctly
        assertTrue("Should implement MainThreadClipboardShape", clipboard is MainThreadClipboardShape)
        assertTrue("Should implement Disposable", clipboard is com.intellij.openapi.Disposable)
    }

    fun testReadWriteRoundTrip() {
        // Test reading back what was written
        val testText = "round trip test"
        
        every { mockCopyPasteManager.setContents(any()) } returns Unit
        every { mockCopyPasteManager.contents } returns mockTransferable
        every { mockTransferable.isDataFlavorSupported(DataFlavor.stringFlavor) } returns true
        every { mockTransferable.getTransferData(DataFlavor.stringFlavor) } returns testText
        
        clipboard.writeText(testText)
        val readText = clipboard.readText()
        
        assertEquals("Read text should match written text", testText, readText)
    }

    fun testLargeTextHandling() {
        // Test handling of large text content
        val largeText = "x".repeat(10000)
        
        every { mockCopyPasteManager.setContents(any()) } returns Unit
        every { mockCopyPasteManager.contents } returns mockTransferable
        every { mockTransferable.isDataFlavorSupported(DataFlavor.stringFlavor) } returns true
        every { mockTransferable.getTransferData(DataFlavor.stringFlavor) } returns largeText
        
        clipboard.writeText(largeText)
        val readText = clipboard.readText()
        
        assertEquals("Should handle large text content", largeText, readText)
    }

    fun testSpecialCharacterHandling() {
        // Test handling of special characters
        val specialText = "Test with special chars: \n\t\r🚀💻📝"
        
        every { mockCopyPasteManager.setContents(any()) } returns Unit
        every { mockCopyPasteManager.contents } returns mockTransferable
        every { mockTransferable.isDataFlavorSupported(DataFlavor.stringFlavor) } returns true
        every { mockTransferable.getTransferData(DataFlavor.stringFlavor) } returns specialText
        
        clipboard.writeText(specialText)
        val readText = clipboard.readText()
        
        assertEquals("Should handle special characters", specialText, readText)
    }
}