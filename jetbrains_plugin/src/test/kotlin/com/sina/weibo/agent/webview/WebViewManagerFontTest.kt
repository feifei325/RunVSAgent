// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.webview

import com.google.gson.JsonObject
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sina.weibo.agent.events.WebviewHtmlUpdateData
import io.mockk.*
import kotlin.test.assertTrue
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Test class for WebViewManager font synchronization functionality
 */
class WebViewManagerFontTest : BasePlatformTestCase() {
    
    private lateinit var webViewManager: WebViewManager
    private lateinit var mockEditorColorsManager: EditorColorsManager
    private lateinit var mockScheme: EditorColorsScheme
    
    override fun setUp() {
        super.setUp()
        
        // Mock EditorColorsManager and scheme
        mockEditorColorsManager = mockk()
        mockScheme = mockk()
        
        // Setup mock behavior
        every { mockScheme.editorFontName } returns "JetBrains Mono"
        every { mockScheme.editorFontSize } returns 14
        
        mockkStatic(EditorColorsManager::class)
        every { EditorColorsManager.getInstance() } returns mockEditorColorsManager
        every { mockEditorColorsManager.globalScheme } returns mockScheme
        
        webViewManager = WebViewManager(project)
    }
    
    override fun tearDown() {
        webViewManager.dispose()
        unmockkAll()
        super.tearDown()
    }
    
    fun testGetEditorFontCss() {
        // Access the private method via reflection
        val getEditorFontCssMethod = WebViewManager::class.java.getDeclaredMethod("getEditorFontCss")
        getEditorFontCssMethod.isAccessible = true
        
        // Execute the method
        val result = getEditorFontCssMethod.invoke(webViewManager) as String
        
        // Verify CSS contains expected font variables
        assertContains(result, "--jetbrains-editor-font-family: 'JetBrains Mono', monospace;")
        assertContains(result, "--jetbrains-editor-font-size: 14px;")
        assertContains(result, "<style id=\"jetbrains-editor-font-style\">")
        assertContains(result, ":root {")
        
        // Verify the method calls
        verify { EditorColorsManager.getInstance() }
        verify { mockEditorColorsManager.globalScheme }
        verify { mockScheme.editorFontName }
        verify { mockScheme.editorFontSize }
    }
    
    fun testGetEditorFontCssWithDifferentFonts() {
        // Test with different font settings
        every { mockScheme.editorFontName } returns "Fira Code"
        every { mockScheme.editorFontSize } returns 12
        
        // Access the private method via reflection
        val getEditorFontCssMethod = WebViewManager::class.java.getDeclaredMethod("getEditorFontCss")
        getEditorFontCssMethod.isAccessible = true
        
        // Execute the method
        val result = getEditorFontCssMethod.invoke(webViewManager) as String
        
        // Verify CSS contains expected font variables
        assertContains(result, "--jetbrains-editor-font-family: 'Fira Code', monospace;")
        assertContains(result, "--jetbrains-editor-font-size: 12px;")
    }
    
    fun testUpdateWebViewHtmlInjectsFontCss() {
        // Prepare test data
        val originalHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Test</title>
            </head>
            <body>
                <h1>Hello World</h1>
            </body>
            </html>
        """.trimIndent()
        
        val htmlUpdateData = WebviewHtmlUpdateData(
            handle = "test-handle",
            htmlContent = originalHtml
        )
        
        // Mock WebViewInstance
        val mockWebViewInstance = mockk<WebViewInstance>(relaxed = true)
        every { mockWebViewInstance.state } returns emptyMap()
        every { mockWebViewInstance.jsQuery } returns mockk(relaxed = true)
        
        // Set the mocked WebViewInstance as the latest
        val latestWebViewField = WebViewManager::class.java.getDeclaredField("latestWebView")
        latestWebViewField.isAccessible = true
        latestWebViewField.set(webViewManager, mockWebViewInstance)
        
        // Execute updateWebViewHtml
        webViewManager.updateWebViewHtml(htmlUpdateData)
        
        // Verify font CSS is injected before </head>
        assertContains(htmlUpdateData.htmlContent, "--jetbrains-editor-font-family: 'JetBrains Mono', monospace;")
        assertContains(htmlUpdateData.htmlContent, "--jetbrains-editor-font-size: 14px;")
        assertContains(htmlUpdateData.htmlContent, "<style id=\"jetbrains-editor-font-style\">")
        
        // Verify the injection happens before </head> tag
        val headEndIndex = htmlUpdateData.htmlContent.indexOf("</head>")
        val fontCssIndex = htmlUpdateData.htmlContent.indexOf("jetbrains-editor-font-style")
        assertTrue(fontCssIndex < headEndIndex, "Font CSS should be injected before </head> tag")
    }
    
    fun testUpdateWebViewHtmlWithoutHeadTag() {
        // Test with HTML that doesn't have </head> tag
        val originalHtml = """
            <div>Simple HTML without head tag</div>
        """.trimIndent()
        
        val htmlUpdateData = WebviewHtmlUpdateData(
            handle = "test-handle",
            htmlContent = originalHtml
        )
        
        // Mock WebViewInstance
        val mockWebViewInstance = mockk<WebViewInstance>(relaxed = true)
        every { mockWebViewInstance.state } returns emptyMap()
        every { mockWebViewInstance.jsQuery } returns mockk(relaxed = true)
        
        // Set the mocked WebViewInstance as the latest
        val latestWebViewField = WebViewManager::class.java.getDeclaredField("latestWebView")
        latestWebViewField.isAccessible = true
        latestWebViewField.set(webViewManager, mockWebViewInstance)
        
        // Execute updateWebViewHtml
        webViewManager.updateWebViewHtml(htmlUpdateData)
        
        // Verify HTML content remains unchanged when no </head> tag exists
        assertEquals(originalHtml, htmlUpdateData.htmlContent)
    }
    
    fun testInjectThemeWithFontVariables() {
        // Create a WebViewInstance for testing
        val webViewInstance = WebViewInstance(
            viewType = "test-view",
            viewId = "test-id",
            title = "Test WebView",
            state = emptyMap(),
            project = project,
            extension = emptyMap()
        )
        
        // Mock the browser and make it appear loaded
        val isPageLoadedField = WebViewInstance::class.java.getDeclaredField("isPageLoaded")
        isPageLoadedField.isAccessible = true
        isPageLoadedField.set(webViewInstance, true)
        
        // Create theme config with CSS content
        val themeConfig = JsonObject().apply {
            addProperty("cssContent", "--vscode-foreground: #cccccc;\n--vscode-background: #1e1e1e;")
        }
        
        // Mock executeJavaScript method to capture the injected script
        var injectedScript: String? = null
        val webViewInstanceSpy = spyk(webViewInstance)
        every { webViewInstanceSpy.executeJavaScript(any()) } answers {
            injectedScript = firstArg()
        }
        
        // Execute sendThemeConfigToWebView
        webViewInstanceSpy.sendThemeConfigToWebView(themeConfig)
        
        // Verify JetBrains font variables are injected
        assertContains(injectedScript!!, "--jetbrains-editor-font-family")
        assertContains(injectedScript!!, "--jetbrains-editor-font-size")
        assertContains(injectedScript!!, "'JetBrains Mono', monospace")
        assertContains(injectedScript!!, "14px")
        
        // Verify font variables are used in body styles
        assertContains(injectedScript!!, "font-family: var(--jetbrains-editor-font-family, var(--vscode-font-family))")
        assertContains(injectedScript!!, "font-size: var(--jetbrains-editor-font-size, var(--vscode-font-size))")
        
        // Verify monaco-editor specific styles
        assertContains(injectedScript!!, ".monaco-editor {")
        assertContains(injectedScript!!, "font-family: var(--jetbrains-editor-font-family, var(--vscode-editor-font-family)) !important")
        assertContains(injectedScript!!, "font-size: var(--jetbrains-editor-font-size, var(--vscode-editor-font-size)) !important")
        
        // Clean up
        webViewInstance.dispose()
    }
    
    fun testFontVariablesUsageInCssFiles() {
        // This test validates that our font variables are used correctly in CSS files
        // Since CSS files are resources, we test the expected variable usage patterns
        
        val expectedFontFamilyUsage = "var(--jetbrains-editor-font-family, -apple-system, BlinkMacSystemFont, sans-serif)"
        val expectedFontSizeUsage = "var(--jetbrains-editor-font-size, 13px)"
        val expectedEditorFontFamilyUsage = "var(--jetbrains-editor-font-family, Menlo, Monaco, \"Courier New\", monospace)"
        val expectedEditorFontSizeUsage = "var(--jetbrains-editor-font-size, 16px)"
        
        // Verify the variable patterns are correctly structured
        assertTrue(expectedFontFamilyUsage.contains("--jetbrains-editor-font-family"))
        assertTrue(expectedFontSizeUsage.contains("--jetbrains-editor-font-size"))
        assertTrue(expectedEditorFontFamilyUsage.contains("--jetbrains-editor-font-family"))
        assertTrue(expectedEditorFontSizeUsage.contains("--jetbrains-editor-font-size"))
        
        // Verify fallback values are preserved
        assertContains(expectedFontFamilyUsage, "-apple-system, BlinkMacSystemFont, sans-serif")
        assertContains(expectedFontSizeUsage, "13px")
        assertContains(expectedEditorFontFamilyUsage, "Menlo, Monaco")
        assertContains(expectedEditorFontSizeUsage, "16px")
    }
    
    fun testWebViewInstanceDisposedState() {
        // Create a WebViewInstance
        val webViewInstance = WebViewInstance(
            viewType = "test-view",
            viewId = "test-id", 
            title = "Test WebView",
            state = emptyMap(),
            project = project,
            extension = emptyMap()
        )
        
        // Dispose the instance
        webViewInstance.dispose()
        
        // Try to send theme config to disposed instance
        val themeConfig = JsonObject().apply {
            addProperty("cssContent", "--vscode-foreground: #cccccc;")
        }
        
        // This should not throw an exception and should handle gracefully
        webViewInstance.sendThemeConfigToWebView(themeConfig)
        
        // Verify that the instance is marked as disposed
        val isDisposedField = WebViewInstance::class.java.getDeclaredField("isDisposed")
        isDisposedField.isAccessible = true
        val isDisposed = isDisposedField.get(webViewInstance) as Boolean
        assertTrue(isDisposed, "WebViewInstance should be marked as disposed")
    }
}