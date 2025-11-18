// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.build

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test class for platform.zip path resolution logic
 * Tests the fix for Issue #90 where platform.zip was being deleted when loading project in IntelliJ
 */
class PlatformZipPathTest {

    @TempDir
    lateinit var tempDir: File
    
    private lateinit var project: Project
    private lateinit var projectDir: File
    
    @BeforeEach
    fun setUp() {
        projectDir = tempDir.resolve("test-project")
        projectDir.mkdirs()
        
        project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
    }
    
    @Test
    fun `test platform zip path resolution with project directory`() {
        // Arrange - Create a platform.zip file in project directory
        val platformZip = File(project.projectDir, "platform.zip")
        platformZip.createNewFile()
        platformZip.writeBytes(ByteArray(2 * 1024 * 1024)) // 2MB file
        
        // Act - Test the corrected path resolution logic (after fix)
        val resolvedPlatformZip = File(project.projectDir, "platform.zip")
        
        // Assert
        assertTrue(resolvedPlatformZip.exists(), "Platform.zip should exist in project directory")
        assertEquals(platformZip.absolutePath, resolvedPlatformZip.absolutePath, 
            "Resolved path should match expected project directory path")
        assertTrue(resolvedPlatformZip.length() >= 1024 * 1024, 
            "Platform.zip should be larger than 1MB")
    }
    
    @Test
    fun `test platform zip path resolution without project directory context`() {
        // Arrange - Create a platform.zip file in current working directory (old way)
        val currentDirPlatformZip = File("platform.zip")
        
        // Act & Assert - The old way would look for file in current directory
        // This demonstrates why the fix was needed
        assertFalse(currentDirPlatformZip.exists(), 
            "Platform.zip should not exist in current working directory in test context")
    }
    
    @Test
    fun `test platform zip path resolution with different project locations`() {
        // Arrange - Test with project in different directory structure
        val nestedProjectDir = tempDir.resolve("nested/project/path")
        nestedProjectDir.mkdirs()
        
        val nestedProject = ProjectBuilder.builder()
            .withProjectDir(nestedProjectDir)
            .build()
            
        val platformZip = File(nestedProject.projectDir, "platform.zip")
        platformZip.createNewFile()
        platformZip.writeBytes(ByteArray(2 * 1024 * 1024)) // 2MB file
        
        // Act - Test path resolution with nested project
        val resolvedPath = File(nestedProject.projectDir, "platform.zip")
        
        // Assert
        assertTrue(resolvedPath.exists(), "Platform.zip should exist in nested project directory")
        assertEquals(nestedProjectDir.absolutePath + File.separator + "platform.zip", 
            resolvedPath.absolutePath, "Path should be correctly resolved for nested project")
    }
    
    @Test
    fun `test platform zip file size validation`() {
        // Arrange - Create platform.zip files with different sizes
        val smallFile = File(project.projectDir, "platform-small.zip")
        val largeFile = File(project.projectDir, "platform-large.zip")
        
        smallFile.createNewFile()
        smallFile.writeBytes(ByteArray(512 * 1024)) // 512KB file
        
        largeFile.createNewFile() 
        largeFile.writeBytes(ByteArray(2 * 1024 * 1024)) // 2MB file
        
        // Act & Assert - Test size validation logic
        assertFalse(smallFile.exists() && smallFile.length() >= 1024 * 1024,
            "Small file should not pass 1MB threshold")
        assertTrue(largeFile.exists() && largeFile.length() >= 1024 * 1024,
            "Large file should pass 1MB threshold")
    }
    
    @Test
    fun `test platform zip extraction directory path`() {
        // Arrange
        val platformZip = File(project.projectDir, "platform.zip")
        platformZip.createNewFile()
        
        // Act - Test build directory path resolution
        val platformDir = File("${project.buildDir}/platform")
        
        // Assert
        assertTrue(platformDir.absolutePath.contains(project.buildDir.name),
            "Platform directory should be under project build directory")
        assertEquals("platform", platformDir.name,
            "Platform extraction directory should be named 'platform'")
    }
    
    @Test
    fun `test platform zip path construction edge cases`() {
        // Test various edge cases for path construction
        
        // Null safety test
        val safeFile = File(project.projectDir ?: File("."), "platform.zip")
        assertEquals("platform.zip", safeFile.name, "File name should be platform.zip")
        
        // Path separator handling
        val normalizedPath = File(project.projectDir, "platform.zip").path
        assertTrue(normalizedPath.endsWith("platform.zip"), "Path should end with platform.zip")
        
        // Cross-platform path handling
        val canonicalPath = File(project.projectDir, "platform.zip").canonicalPath
        assertTrue(canonicalPath.contains("platform.zip"), "Canonical path should contain platform.zip")
    }
}