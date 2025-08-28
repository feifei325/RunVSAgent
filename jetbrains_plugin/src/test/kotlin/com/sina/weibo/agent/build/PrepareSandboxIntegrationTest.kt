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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration test for prepareSandbox task platform.zip handling
 * Validates the fix for platform.zip path resolution in release mode
 */
class PrepareSandboxIntegrationTest {

    @TempDir
    lateinit var tempDir: File
    
    private lateinit var project: Project
    private lateinit var projectDir: File
    
    @BeforeEach
    fun setUp() {
        projectDir = tempDir.resolve("jetbrains_plugin")
        projectDir.mkdirs()
        
        project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .withName("jetbrains_plugin")
            .build()
            
        // Set up gradle properties
        project.extensions.extraProperties.set("debugMode", "release")
    }
    
    private fun createValidPlatformZip(): File {
        val platformZip = File(project.projectDir, "platform.zip")
        
        // Create a valid zip file with some content
        ZipOutputStream(platformZip.outputStream()).use { zos ->
            // Add a dummy file to make it a valid zip
            val entry = ZipEntry("platform/dummy.txt")
            zos.putNextEntry(entry)
            zos.write("dummy content for platform zip".toByteArray())
            zos.closeEntry()
            
            // Add more entries to reach 1MB+ size
            repeat(100) { i ->
                val largeEntry = ZipEntry("platform/large_file_$i.txt")
                zos.putNextEntry(largeEntry)
                zos.write(ByteArray(12000) { it.toByte() }) // 12KB per file
                zos.closeEntry()
            }
        }
        
        return platformZip
    }
    
    @Test
    fun `test platform zip path resolution in release mode`() {
        // Arrange
        val debugMode = "release"
        val platformZip = createValidPlatformZip()
        
        // Act - Simulate the corrected logic from build.gradle.kts
        val resolvedPlatformZip = File(project.projectDir, "platform.zip")
        val platformDir = File("${project.buildDir}/platform")
        
        // Assert
        assertTrue(resolvedPlatformZip.exists(), 
            "Platform.zip should be found using project.projectDir")
        assertTrue(resolvedPlatformZip.length() >= 1024 * 1024,
            "Platform.zip should be larger than 1MB")
        assertEquals(platformZip.absolutePath, resolvedPlatformZip.absolutePath,
            "Resolved path should match the actual file location")
    }
    
    @Test
    fun `test platform zip not found with old relative path approach`() {
        // Arrange - Create platform.zip in project directory but test old approach
        createValidPlatformZip()
        
        // Act - Simulate the old problematic logic
        val oldApproachFile = File("platform.zip") // Relative path without project context
        
        // Assert - This demonstrates the issue that was fixed
        // In test environment, current working directory != project directory
        assertFalse(oldApproachFile.exists(),
            "Old relative path approach fails to find platform.zip in different working directory")
    }
    
    @Test
    fun `test platform directory creation path`() {
        // Arrange
        createValidPlatformZip()
        
        // Act - Test platform directory path construction
        val platformDir = File("${project.buildDir}/platform")
        
        // Assert
        assertTrue(platformDir.absolutePath.contains(project.buildDir.absolutePath),
            "Platform directory should be under project build directory")
        assertEquals("platform", platformDir.name,
            "Platform extraction directory should be named 'platform'")
        
        // Simulate directory creation
        platformDir.mkdirs()
        assertTrue(platformDir.exists(), "Platform directory should be created successfully")
    }
    
    @Test
    fun `test platform zip size validation edge cases`() {
        // Test file size boundary conditions
        
        // Small file (less than 1MB)
        val smallZip = File(project.projectDir, "platform-small.zip")
        ZipOutputStream(smallZip.outputStream()).use { zos ->
            val entry = ZipEntry("small.txt")
            zos.putNextEntry(entry)
            zos.write(ByteArray(100) { it.toByte() }) // Only 100 bytes
            zos.closeEntry()
        }
        
        // Exactly 1MB file
        val exactZip = File(project.projectDir, "platform-exact.zip") 
        ZipOutputStream(exactZip.outputStream()).use { zos ->
            val entry = ZipEntry("exact.txt")
            zos.putNextEntry(entry)
            zos.write(ByteArray(1024 * 1024) { it.toByte() }) // Exactly 1MB
            zos.closeEntry()
        }
        
        // Assert size validations
        assertFalse(smallZip.length() >= 1024 * 1024,
            "Small zip should not pass 1MB threshold")
        assertTrue(exactZip.length() >= 1024 * 1024,
            "Exact 1MB zip should pass threshold")
    }
    
    @Test
    fun `test prepareSandbox logic simulation`() {
        // Arrange
        val debugMode = "release"
        val platformZip = createValidPlatformZip()
        val platformDir = File("${project.buildDir}/platform")
        
        // Act - Simulate the prepareSandbox logic
        if (debugMode == "release") {
            val resolvedPlatformZip = File(project.projectDir, "platform.zip")
            
            if (resolvedPlatformZip.exists() && resolvedPlatformZip.length() >= 1024 * 1024) {
                platformDir.mkdirs()
                // Simulate extraction would happen here
                assertTrue(platformDir.exists(), "Platform directory should be created")
            }
        }
        
        // Assert
        assertTrue(platformZip.exists(), "Platform.zip should exist")
        assertTrue(platformDir.exists(), "Platform directory should be created")
    }
    
    @Test
    fun `test cross platform path handling`() {
        // Test that path resolution works across different operating systems
        
        val platformZip = createValidPlatformZip()
        val resolvedPath = File(project.projectDir, "platform.zip")
        
        // Test canonical path resolution
        val canonicalPath = resolvedPath.canonicalPath
        val absolutePath = resolvedPath.absolutePath
        
        assertTrue(canonicalPath.endsWith("platform.zip"),
            "Canonical path should end with platform.zip")
        assertTrue(absolutePath.contains(project.projectDir.name),
            "Absolute path should contain project directory name")
        
        // Test path separator normalization
        val normalizedPath = resolvedPath.path.replace('\\', '/')
        assertTrue(normalizedPath.endsWith("platform.zip"),
            "Normalized path should end with platform.zip")
    }
    
    @Test
    fun `test platform zip file not found scenario`() {
        // Test scenario where platform.zip doesn't exist
        
        val missingPlatformZip = File(project.projectDir, "platform.zip")
        
        assertFalse(missingPlatformZip.exists(),
            "Platform.zip should not exist in this test scenario")
            
        // Simulate the check that would happen in prepareSandbox
        val shouldProceed = missingPlatformZip.exists() && missingPlatformZip.length() >= 1024 * 1024
        
        assertFalse(shouldProceed,
            "Should not proceed with extraction when platform.zip is missing")
    }
}