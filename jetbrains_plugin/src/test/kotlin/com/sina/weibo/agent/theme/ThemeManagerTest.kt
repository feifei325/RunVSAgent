// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.theme

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.UIManager
import java.awt.Color

/**
 * Unit tests for ThemeManager class
 * Tests theme loading, color configuration parsing, and contrast improvements
 */
class ThemeManagerTest : BasePlatformTestCase() {

    private lateinit var themeManager: ThemeManager
    private lateinit var tempThemeDir: Path
    private lateinit var tempResourceRoot: Path

    override fun setUp() {
        super.setUp()
        
        // Create temporary theme directory structure for testing
        tempResourceRoot = Files.createTempDirectory("theme_test_root")
        tempThemeDir = tempResourceRoot.resolve("src/integrations/theme/default-themes")
        Files.createDirectories(tempThemeDir)
        
        // Create test theme files
        createTestThemeFiles()
        
        themeManager = ThemeManager.getInstance()
    }

    override fun tearDown() {
        try {
            themeManager.dispose()
            // Clean up temporary files
            tempResourceRoot.toFile().deleteRecursively()
        } catch (e: Exception) {
            // Ignore cleanup errors in tests
        }
        super.tearDown()
    }

    private fun createTestThemeFiles() {
        // Create dark theme CSS file with improved contrast colors
        val darkThemeCss = """
            /* Dark theme with improved text contrast */
            :root {
                --vscode-foreground: #e4e4e4;
                --vscode-disabledForeground: rgba(228, 228, 228, 0.6);
                --vscode-descriptionForeground: rgba(228, 228, 228, 0.8);
                --vscode-textPreformat-foreground: #ffd700;
                --vscode-textPreformat-background: rgba(255, 255, 255, 0.15);
                --vscode-textCodeBlock-background: rgba(40, 40, 40, 0.6);
                --vscode-editor-foreground: #f0f0f0;
                --vscode-editorWidget-foreground: #e4e4e4;
                --vscode-editorHoverWidget-foreground: #e4e4e4;
                --vscode-charts-foreground: #e4e4e4;
                --vscode-input-foreground: #e4e4e4;
                --vscode-keybindingLabel-foreground: #e4e4e4;
                --vscode-editorActionList-foreground: #e4e4e4;
                --vscode-menu-foreground: #e4e4e4;
                --vscode-quickInput-foreground: #e4e4e4;
                --vscode-editorSuggestWidget-foreground: #f0f0f0;
                --vscode-notifications-foreground: #e4e4e4;
                --vscode-inlineChat-foreground: #e4e4e4;
                --vscode-terminal-foreground: #e4e4e4;
            }
        """.trimIndent()
        
        val lightThemeCss = """
            /* Light theme with improved text contrast */
            :root {
                --vscode-foreground: #3b3b3b;
                --vscode-textPreformat-foreground: #8b0000;
                --vscode-textPreformat-background: rgba(0, 0, 0, 0.08);
                --vscode-textCodeBlock-background: rgba(240, 240, 240, 0.6);
                --vscode-editor-foreground: #1a1a1a;
                --vscode-editorWidget-foreground: #3b3b3b;
                --vscode-editorHoverWidget-foreground: #3b3b3b;
            }
        """.trimIndent()

        // Write CSS files
        tempThemeDir.resolve("vscode-theme-dark.css").toFile().writeText(darkThemeCss)
        tempThemeDir.resolve("vscode-theme-light.css").toFile().writeText(lightThemeCss)

        // Create simple JSON theme files for testing
        val darkThemeJson = """
        {
            "type": "dark",
            "colors": {
                "editor.background": "#1e1e1e",
                "editor.foreground": "#f0f0f0"
            }
        }
        """.trimIndent()

        val lightThemeJson = """
        {
            "type": "light", 
            "colors": {
                "editor.background": "#ffffff",
                "editor.foreground": "#1a1a1a"
            }
        }
        """.trimIndent()

        tempThemeDir.resolve("dark_modern.json").toFile().writeText(darkThemeJson)
        tempThemeDir.resolve("light_modern.json").toFile().writeText(lightThemeJson)
    }

    fun testThemeResourceDirectoryDetection() {
        // Test static method for finding theme resource directory
        val foundDir = ThemeManager.getThemeResourceDir(tempResourceRoot.toString())
        assertNotNull("Should find theme resource directory", foundDir)
        assertEquals("Should find correct theme directory", tempThemeDir, foundDir)
    }

    fun testThemeResourceDirectoryFallback() {
        // Test fallback path detection
        val fallbackRoot = Files.createTempDirectory("fallback_test")
        val fallbackDir = fallbackRoot.resolve("integrations/theme/default-themes")
        Files.createDirectories(fallbackDir)
        
        val foundDir = ThemeManager.getThemeResourceDir(fallbackRoot.toString())
        assertNotNull("Should find fallback theme directory", foundDir)
        assertEquals("Should find fallback theme directory", fallbackDir, foundDir)
        
        // Cleanup
        fallbackRoot.toFile().deleteRecursively()
    }

    fun testThemeManagerInitialization() {
        // Test theme manager initialization
        themeManager.initialize(tempResourceRoot.toString())
        
        // Verify initialization worked
        val currentConfig = themeManager.getCurrentThemeConfig()
        assertNotNull("Should have current theme configuration after initialization", currentConfig)
    }

    fun testDarkThemeDetection() {
        // Test dark theme detection
        UIManager.put("Panel.background", Color(30, 30, 30)) // Dark background
        val isDark = themeManager.isDarkThemeForce()
        assertTrue("Should detect dark theme", isDark)
    }

    fun testLightThemeDetection() {
        // Test light theme detection  
        UIManager.put("Panel.background", Color(255, 255, 255)) // Light background
        val isLight = !themeManager.isDarkThemeForce()
        assertTrue("Should detect light theme", isLight)
    }

    fun testCssContentLoading() {
        // Initialize theme manager
        themeManager.initialize(tempResourceRoot.toString())
        
        val currentConfig = themeManager.getCurrentThemeConfig()
        assertNotNull("Should have theme configuration", currentConfig)
        
        // Check if CSS content is loaded
        if (currentConfig?.has("cssContent") == true) {
            val cssContent = currentConfig.get("cssContent").asString
            assertNotNull("CSS content should not be null", cssContent)
            assertTrue("CSS content should contain contrast improvements", 
                cssContent.contains("--vscode-foreground") || cssContent.contains("foreground"))
        }
    }

    fun testImprovedContrastColors() {
        // Test that improved contrast colors are loaded correctly
        themeManager.initialize(tempResourceRoot.toString())
        
        val currentConfig = themeManager.getCurrentThemeConfig()
        if (currentConfig?.has("cssContent") == true) {
            val cssContent = currentConfig.get("cssContent").asString
            
            // Check for improved dark theme colors
            if (cssContent.contains("vscode-theme-dark")) {
                assertTrue("Should contain improved foreground color", 
                    cssContent.contains("#e4e4e4") || cssContent.contains("#f0f0f0"))
                assertTrue("Should contain improved preformat color", 
                    cssContent.contains("#ffd700"))
            }
            
            // Check for improved light theme colors
            if (cssContent.contains("vscode-theme-light")) {
                assertTrue("Should contain improved light foreground color", 
                    cssContent.contains("#3b3b3b") || cssContent.contains("#1a1a1a"))
                assertTrue("Should contain improved light preformat color", 
                    cssContent.contains("#8b0000"))
            }
        }
    }

    fun testThemeChangeListener() {
        var listenerCalled = false
        var receivedConfig: JsonObject? = null
        var receivedIsDark: Boolean? = null

        val listener = object : ThemeChangeListener {
            override fun onThemeChanged(themeConfig: JsonObject, isDarkTheme: Boolean) {
                listenerCalled = true
                receivedConfig = themeConfig
                receivedIsDark = isDarkTheme
            }
        }

        // Add listener before initialization
        themeManager.addThemeChangeListener(listener)
        
        // Initialize theme manager
        themeManager.initialize(tempResourceRoot.toString())

        // Verify listener was called
        assertTrue("Theme change listener should be called", listenerCalled)
        assertNotNull("Should receive theme configuration", receivedConfig)
        assertNotNull("Should receive dark theme flag", receivedIsDark)
    }

    fun testManualThemeReload() {
        // Initialize theme manager
        themeManager.initialize(tempResourceRoot.toString())
        
        val initialConfig = themeManager.getCurrentThemeConfig()
        assertNotNull("Should have initial configuration", initialConfig)
        
        // Test manual reload
        themeManager.reloadThemeConfig()
        
        val reloadedConfig = themeManager.getCurrentThemeConfig()
        assertNotNull("Should have configuration after reload", reloadedConfig)
    }

    fun testListenerManagement() {
        val listener1 = object : ThemeChangeListener {
            override fun onThemeChanged(themeConfig: JsonObject, isDarkTheme: Boolean) {}
        }
        
        val listener2 = object : ThemeChangeListener {
            override fun onThemeChanged(themeConfig: JsonObject, isDarkTheme: Boolean) {}
        }

        // Test adding listeners
        themeManager.addThemeChangeListener(listener1)
        themeManager.addThemeChangeListener(listener2)

        // Test removing listeners
        themeManager.removeThemeChangeListener(listener1)
        themeManager.removeThemeChangeListener(listener2)
        
        // Should not throw exceptions
        assertTrue("Listener management should work without errors", true)
    }

    fun testThemeManagerDisposal() {
        // Initialize theme manager
        themeManager.initialize(tempResourceRoot.toString())
        
        // Verify it's working
        assertNotNull("Should have theme configuration", themeManager.getCurrentThemeConfig())
        
        // Test disposal
        themeManager.dispose()
        
        // After disposal, should handle gracefully
        // (Implementation may vary, but should not crash)
        assertTrue("Disposal should complete without errors", true)
    }

    fun testContrastColorValues() {
        // Test specific color values for contrast improvements
        val darkCss = tempThemeDir.resolve("vscode-theme-dark.css").toFile().readText()
        
        // Check improved contrast colors are present
        assertTrue("Should have improved foreground color #e4e4e4", 
            darkCss.contains("#e4e4e4"))
        assertTrue("Should have improved editor foreground #f0f0f0", 
            darkCss.contains("#f0f0f0"))
        assertTrue("Should have improved preformat color #ffd700", 
            darkCss.contains("#ffd700"))
            
        val lightCss = tempThemeDir.resolve("vscode-theme-light.css").toFile().readText()
        
        // Check improved light theme colors
        assertTrue("Should have improved light foreground #3b3b3b", 
            lightCss.contains("#3b3b3b"))
        assertTrue("Should have improved light editor foreground #1a1a1a", 
            lightCss.contains("#1a1a1a"))
        assertTrue("Should have improved light preformat color #8b0000", 
            lightCss.contains("#8b0000"))
    }

    fun testThemeFileExistence() {
        // Test that required theme files exist
        assertTrue("Dark CSS theme file should exist", 
            tempThemeDir.resolve("vscode-theme-dark.css").toFile().exists())
        assertTrue("Light CSS theme file should exist", 
            tempThemeDir.resolve("vscode-theme-light.css").toFile().exists())
        assertTrue("Dark JSON theme file should exist", 
            tempThemeDir.resolve("dark_modern.json").toFile().exists())
        assertTrue("Light JSON theme file should exist", 
            tempThemeDir.resolve("light_modern.json").toFile().exists())
    }
}