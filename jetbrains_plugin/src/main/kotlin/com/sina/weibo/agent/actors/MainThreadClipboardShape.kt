// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.ui.EmptyClipboardOwner
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

/**
 * Main thread clipboard interface.
 * Corresponds to the MainThreadClipboardShape interface in VSCode.
 */
interface MainThreadClipboardShape : Disposable {
    /**
     * Reads text from the clipboard.
     * @return The string from the clipboard, or null if no text is available
     */
    fun readText(): String?

    /**
     * Writes text to the clipboard.
     * @param value The string to write to the clipboard
     */
    fun writeText(value: String?)
}

/**
 * Implementation of the MainThreadClipboardShape interface.
 * Provides functionality to read from and write to the system clipboard.
 * Supports both X11 and Wayland display servers.
 */
class MainThreadClipboard : MainThreadClipboardShape {
    private val logger = Logger.getInstance(MainThreadClipboardShape::class.java)
    private val isWayland = System.getenv("WAYLAND_DISPLAY") != null || 
                            System.getenv("XDG_SESSION_TYPE") == "wayland"

    /**
     * Reads text from the system clipboard.
     * Uses IntelliJ's CopyPasteManager for better compatibility with Wayland.
     *
     * @return The string from the clipboard, or null if no text is available or an error occurs
     */
    override fun readText(): String? {
        logger.info("Reading clipboard text (Wayland: $isWayland)")
        
        // Try using IntelliJ's CopyPasteManager first for better Wayland support
        return try {
            val contents = CopyPasteManager.getInstance().contents
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                contents.getTransferData(DataFlavor.stringFlavor) as? String
            } else {
                // Fallback to AWT Toolkit
                readTextWithAWT()
            }
        } catch (e: Exception) {
            logger.warn("Failed to read clipboard with CopyPasteManager, trying AWT", e)
            readTextWithAWT()
        }
    }
    
    /**
     * Fallback method to read text using AWT Toolkit.
     */
    private fun readTextWithAWT(): String? {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val data = clipboard.getContents(null)
            if (data != null && data.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                data.getTransferData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to read clipboard with AWT", e)
            null
        }
    }

    /**
     * Writes text to the system clipboard.
     * Uses IntelliJ's CopyPasteManager for better compatibility with Wayland.
     *
     * @param value The string to write to the clipboard
     */
    override fun writeText(value: String?) {
        value?.let {
            logger.info("Writing clipboard text (Wayland: $isWayland)")
            val selection = StringSelection(value)
            
            // Try using IntelliJ's CopyPasteManager first for better Wayland support
            try {
                CopyPasteManager.getInstance().setContents(selection)
            } catch (e: Exception) {
                logger.warn("Failed to write to clipboard with CopyPasteManager, trying AWT", e)
                // Fallback to AWT Toolkit
                writeTextWithAWT(selection)
            }
        }
    }
    
    /**
     * Fallback method to write text using AWT Toolkit.
     */
    private fun writeTextWithAWT(selection: StringSelection) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(selection, EmptyClipboardOwner.INSTANCE)
        } catch (e: Exception) {
            logger.error("Failed to write to clipboard with AWT", e)
        }
    }

    /**
     * Releases resources used by this clipboard handler.
     */
    override fun dispose() {
        logger.info("Releasing resources: MainThreadClipboard")
    }
} 