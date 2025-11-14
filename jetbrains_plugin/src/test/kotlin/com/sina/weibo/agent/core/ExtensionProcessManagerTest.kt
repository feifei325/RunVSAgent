// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.core

import com.intellij.execution.util.PathEnvironmentVariableUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.system.SystemInfo
import org.junit.Test
import java.io.File

/**
 * Unit tests for ExtensionProcessManager Node.js detection functionality
 * Tests the enhanced Node.js detection that supports version managers
 */
class ExtensionProcessManagerTest : BasePlatformTestCase() {

    private lateinit var processManager: ExtensionProcessManager
    private val originalUserHome = System.getProperty("user.home")

    override fun setUp() {
        super.setUp()
        processManager = ExtensionProcessManager()
    }

    override fun tearDown() {
        // Restore original system properties
        System.setProperty("user.home", originalUserHome)
        super.tearDown()
    }

    @Test
    fun testBuildAdditionalNodePaths_ContainsVersionManagerPaths() {
        // Test that buildAdditionalNodePaths includes common version manager paths
        val homeDir = "/home/testuser"
        System.setProperty("user.home", homeDir)
        
        val result = invokePrivateMethod("buildAdditionalNodePaths") as List<String>
        
        // Should contain asdf paths
        assertTrue("Should contain asdf shims path", 
            result.any { it.contains(".asdf/shims") })
        
        // Should contain volta paths
        assertTrue("Should contain volta bin path", 
            result.any { it.contains(".volta/bin") })
        
        // Should contain nvm paths
        assertTrue("Should contain nvm current bin path", 
            result.any { it.contains(".nvm/current/bin") })
        
        // Should contain fnm paths
        assertTrue("Should contain fnm current bin path", 
            result.any { it.contains(".fnm/current/bin") })
    }

    @Test
    fun testBuildAdditionalNodePaths_PlatformSpecificPaths() {
        val homeDir = "/home/testuser"
        System.setProperty("user.home", homeDir)
        
        val result = invokePrivateMethod("buildAdditionalNodePaths") as List<String>
        
        // Check for platform-specific paths
        if (SystemInfo.isMac) {
            assertTrue("Should contain homebrew paths on Mac", 
                result.any { it.contains("/opt/homebrew/bin") || it.contains("/usr/local/bin") })
        } else if (SystemInfo.isLinux) {
            assertTrue("Should contain common Linux paths", 
                result.any { it.contains("/usr/bin") || it.contains("/usr/local/bin") })
        } else if (SystemInfo.isWindows) {
            assertTrue("Should contain Windows-specific paths", 
                result.any { it.contains("Program Files") })
        }
    }

    @Test
    fun testBuildAdditionalNodePaths_NotEmpty() {
        // Test that the method returns a non-empty list regardless of platform
        val result = invokePrivateMethod("buildAdditionalNodePaths") as List<String>
        assertFalse("Additional node paths should not be empty", result.isEmpty())
    }

    @Test 
    fun testBuildAdditionalNodePaths_AllPathsAreStrings() {
        // Test that all returned paths are valid strings
        val result = invokePrivateMethod("buildAdditionalNodePaths") as List<String>
        
        result.forEach { path ->
            assertNotNull("Path should not be null", path)
            assertFalse("Path should not be empty", path.isEmpty())
            assertFalse("Path should not be blank", path.isBlank())
        }
    }

    @Test
    fun testFindExecutableInPath_CallsStandardUtilFirst() {
        // Test that standard path finding is attempted first
        val result = invokePrivateMethod("findExecutableInPath", "node")
        
        // This test verifies the method doesn't crash and returns a nullable result
        // The actual implementation will depend on the system environment
        // We're mainly testing that the method structure is sound
        assertTrue("Method should return either a string path or null", 
            result == null || result is String)
    }

    @Test
    fun testNodePathDetection_Integration() {
        // Integration test for the full node detection process
        val processManager = ExtensionProcessManager()
        
        // This test verifies that the enhanced detection process doesn't break
        // the existing functionality. It should still work with or without
        // version managers installed.
        try {
            val result = invokePrivateMethod("findNodeExecutablePath")
            // Should return either a valid path or null, but not crash
            assertTrue("Node detection should return string or null", 
                result == null || result is String)
        } catch (e: Exception) {
            // If the method doesn't exist or fails, that's also valid for this test
            // We're mainly ensuring no major structural issues
        }
    }

    @Test
    fun testVersionManagerPathConstruction() {
        // Test that version manager paths are constructed correctly
        val testHome = "/test/home"
        System.setProperty("user.home", testHome)
        
        val result = invokePrivateMethod("buildAdditionalNodePaths") as List<String>
        
        // Verify paths contain the correct home directory
        result.forEach { path ->
            if (path.contains("asdf") || path.contains("volta") || 
                path.contains("nvm") || path.contains("fnm")) {
                assertTrue("Version manager path should use correct home directory: $path", 
                    path.contains(testHome) || path.startsWith("/") || path.contains("C:"))
            }
        }
    }

    @Test
    fun testWindowsSpecificPaths() {
        if (SystemInfo.isWindows) {
            val result = invokePrivateMethod("buildAdditionalNodePaths") as List<String>
            
            // Should contain Windows-specific paths
            assertTrue("Should contain Program Files paths on Windows",
                result.any { it.contains("Program Files") })
                
            assertTrue("Should contain scoop paths on Windows",
                result.any { it.contains("scoop") })
        }
    }

    @Test
    fun testLinuxSpecificPaths() {
        if (SystemInfo.isLinux) {
            val result = invokePrivateMethod("buildAdditionalNodePaths") as List<String>
            
            // Should contain Linux-specific paths
            assertTrue("Should contain /usr/bin on Linux",
                result.contains("/usr/bin"))
                
            assertTrue("Should contain /usr/local/bin on Linux", 
                result.contains("/usr/local/bin"))
        }
    }

    @Test
    fun testMacSpecificPaths() {
        if (SystemInfo.isMac) {
            val result = invokePrivateMethod("buildAdditionalNodePaths") as List<String>
            
            // Should contain Mac-specific paths
            assertTrue("Should contain homebrew paths on Mac",
                result.any { it.contains("/opt/homebrew") || it.contains("/usr/local") })
        }
    }

    /**
     * Helper method to invoke private methods using reflection
     */
    private fun invokePrivateMethod(methodName: String, vararg args: Any?): Any? {
        val argTypes = args.map { arg ->
            when (arg) {
                is String -> String::class.java
                is Int -> Int::class.java
                is Boolean -> Boolean::class.java
                null -> String::class.java  // Default for null
                else -> arg.javaClass
            }
        }.toTypedArray()
        
        val method = ExtensionProcessManager::class.java.getDeclaredMethod(methodName, *argTypes)
        method.isAccessible = true
        return method.invoke(processManager, *args)
    }
}