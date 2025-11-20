// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.webview

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandler
import org.cef.handler.CefKeyboardHandler
import org.cef.handler.CefKeyboardHandlerAdapter
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Wayland-compatible clipboard handler for WebView.
 * Handles clipboard operations for JCEF WebView on both X11 and Wayland.
 */
class WaylandClipboardHandler : CefKeyboardHandlerAdapter() {
    private val logger = Logger.getInstance(WaylandClipboardHandler::class.java)
    private val isWayland = System.getenv("WAYLAND_DISPLAY") != null ||
                           System.getenv("XDG_SESSION_TYPE") == "wayland"

    /**
     * Handle keyboard events for clipboard operations.
     */
    override fun onPreKeyEvent(
        browser: CefBrowser?,
        event: org.cef.handler.CefKeyboardHandler.CefKeyEvent?,
        is_keyboard_shortcut: org.cef.misc.BoolRef?
    ): Boolean {
        if (event == null || browser == null) return false
        
        // Check for paste shortcut (Ctrl+V or Cmd+V)
        val isPasteShortcut = when {
            // Windows/Linux: Ctrl+V
            (event.modifiers and EVENTFLAG_CONTROL_DOWN != 0) && event.windows_key_code == 86 -> true
            // macOS: Cmd+V
            (event.modifiers and EVENTFLAG_COMMAND_DOWN != 0) && event.windows_key_code == 86 -> true
            else -> false
        }
        
        // Check for copy shortcut (Ctrl+C or Cmd+C)
        val isCopyShortcut = when {
            // Windows/Linux: Ctrl+C
            (event.modifiers and EVENTFLAG_CONTROL_DOWN != 0) && event.windows_key_code == 67 -> true
            // macOS: Cmd+C
            (event.modifiers and EVENTFLAG_COMMAND_DOWN != 0) && event.windows_key_code == 67 -> true
            else -> false
        }
        
        // Check for cut shortcut (Ctrl+X or Cmd+X)
        val isCutShortcut = when {
            // Windows/Linux: Ctrl+X
            (event.modifiers and EVENTFLAG_CONTROL_DOWN != 0) && event.windows_key_code == 88 -> true
            // macOS: Cmd+X
            (event.modifiers and EVENTFLAG_COMMAND_DOWN != 0) && event.windows_key_code == 88 -> true
            else -> false
        }
        
        if (isPasteShortcut && event.type == EventType.KEYEVENT_RAWKEYDOWN) {
            handlePaste(browser)
            return true // Consume the event
        }
        
        if (isCopyShortcut && event.type == EventType.KEYEVENT_RAWKEYDOWN) {
            handleCopy(browser)
            return true // Consume the event
        }
        
        if (isCutShortcut && event.type == EventType.KEYEVENT_RAWKEYDOWN) {
            handleCut(browser)
            return true // Consume the event
        }
        
        return false
    }
    
    /**
     * Handle paste operation using IntelliJ's CopyPasteManager.
     */
    private fun handlePaste(browser: CefBrowser) {
        try {
            logger.info("Handling paste operation (Wayland: $isWayland)")
            
            // Get clipboard content using IntelliJ's CopyPasteManager
            val contents = CopyPasteManager.getInstance().contents
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                val text = contents.getTransferData(DataFlavor.stringFlavor) as? String
                text?.let {
                    // Execute paste in the browser
                    browser.executeJavaScript(
                        """
                        (function() {
                            const activeElement = document.activeElement;
                            if (activeElement && (activeElement.tagName === 'INPUT' || 
                                activeElement.tagName === 'TEXTAREA' || 
                                activeElement.contentEditable === 'true')) {
                                
                                // For input and textarea elements
                                if (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA') {
                                    const start = activeElement.selectionStart;
                                    const end = activeElement.selectionEnd;
                                    const value = activeElement.value;
                                    const textToInsert = ${text.toJsString()};
                                    activeElement.value = value.substring(0, start) + textToInsert + value.substring(end);
                                    activeElement.selectionStart = activeElement.selectionEnd = start + textToInsert.length;
                                    
                                    // Trigger input event
                                    activeElement.dispatchEvent(new Event('input', { bubbles: true }));
                                    activeElement.dispatchEvent(new Event('change', { bubbles: true }));
                                }
                                // For contentEditable elements
                                else if (activeElement.contentEditable === 'true') {
                                    document.execCommand('insertText', false, ${text.toJsString()});
                                }
                            }
                        })();
                        """.trimIndent(),
                        browser.url,
                        0
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to handle paste operation", e)
        }
    }
    
    /**
     * Handle copy operation.
     */
    private fun handleCopy(browser: CefBrowser) {
        try {
            logger.info("Handling copy operation (Wayland: $isWayland)")
            
            // Get selected text from the browser
            browser.executeJavaScript(
                """
                (function() {
                    const selection = window.getSelection().toString();
                    if (selection) {
                        // Send selected text back to Java
                        window.cefQuery({
                            request: JSON.stringify({
                                type: 'clipboard-copy',
                                text: selection
                            }),
                            onSuccess: function(response) {},
                            onFailure: function(error_code, error_message) {}
                        });
                    }
                })();
                """.trimIndent(),
                browser.url,
                0
            )
        } catch (e: Exception) {
            logger.error("Failed to handle copy operation", e)
        }
    }
    
    /**
     * Handle cut operation.
     */
    private fun handleCut(browser: CefBrowser) {
        try {
            logger.info("Handling cut operation (Wayland: $isWayland)")
            
            // Get selected text and remove it
            browser.executeJavaScript(
                """
                (function() {
                    const selection = window.getSelection().toString();
                    if (selection) {
                        // Send selected text back to Java
                        window.cefQuery({
                            request: JSON.stringify({
                                type: 'clipboard-cut',
                                text: selection
                            }),
                            onSuccess: function(response) {},
                            onFailure: function(error_code, error_message) {}
                        });
                        
                        // Remove selected text
                        document.execCommand('delete', false, null);
                    }
                })();
                """.trimIndent(),
                browser.url,
                0
            )
        } catch (e: Exception) {
            logger.error("Failed to handle cut operation", e)
        }
    }
    
    /**
     * Copy text to clipboard using IntelliJ's CopyPasteManager.
     */
    fun copyToClipboard(text: String) {
        try {
            val selection = StringSelection(text)
            CopyPasteManager.getInstance().setContents(selection)
            logger.info("Text copied to clipboard")
        } catch (e: Exception) {
            logger.error("Failed to copy to clipboard", e)
        }
    }
    
    companion object {
        // CEF keyboard event flags
        private const val EVENTFLAG_CONTROL_DOWN = 1 shl 7
        private const val EVENTFLAG_COMMAND_DOWN = 1 shl 8
        
        // CEF key event types
        private object EventType {
            const val KEYEVENT_RAWKEYDOWN = 0
            const val KEYEVENT_KEYDOWN = 1
            const val KEYEVENT_KEYUP = 2
            const val KEYEVENT_CHAR = 3
        }
    }
}

/**
 * Extension function to escape string for JavaScript.
 */
private fun String.toJsString(): String {
    return "'" + this
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t") + "'"
}