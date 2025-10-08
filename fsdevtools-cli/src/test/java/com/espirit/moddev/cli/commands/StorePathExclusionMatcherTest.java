/*
 *
 * *********************************************************************
 * fsdevtools
 * %%
 * Copyright (C) 2024 Crownpeak Technology GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * *********************************************************************
 *
 */

package com.espirit.moddev.cli.commands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class StorePathExclusionMatcherTest {

	@TempDir
	Path tempDir;

	private File syncDir;

	@BeforeEach
	public void setUp() throws IOException {
		syncDir = tempDir.toFile();

		// Create test directory structure
		new File(syncDir, "templatestore").mkdirs();
		new File(syncDir, "templatestore/test").mkdirs();
		new File(syncDir, "templatestore/translationstudio").mkdirs();
		new File(syncDir, "pagestore").mkdirs();
		new File(syncDir, "pagestore/folder").mkdirs();

		Files.createFile(new File(syncDir, "templatestore/file.xml").toPath());
		Files.createFile(new File(syncDir, "templatestore/test/test.xml").toPath());
		Files.createFile(new File(syncDir, "templatestore/translationstudio/data.xml").toPath());
		Files.createFile(new File(syncDir, "pagestore/page.xml").toPath());
		Files.createFile(new File(syncDir, "data.log").toPath());
	}

	@Test
	public void testExactPathExclusion() {
		StorePathExclusionMatcher matcher = new StorePathExclusionMatcher(
			Arrays.asList("path:/templatestore/translationstudio")
		);

		File excludedDir = new File(syncDir, "templatestore/translationstudio");
		File excludedFile = new File(syncDir, "templatestore/translationstudio/data.xml");
		File includedFile = new File(syncDir, "templatestore/file.xml");

		assertTrue(matcher.shouldExclude(syncDir, excludedDir));
		assertTrue(matcher.shouldExclude(syncDir, excludedFile));
		assertFalse(matcher.shouldExclude(syncDir, includedFile));
	}

	@Test
	public void testExactPathWithLeadingSlash() {
		StorePathExclusionMatcher matcher = new StorePathExclusionMatcher(
			Arrays.asList("path:templatestore/test")
		);

		File excludedDir = new File(syncDir, "templatestore/test");
		File excludedFile = new File(syncDir, "templatestore/test/test.xml");

		assertTrue(matcher.shouldExclude(syncDir, excludedDir));
		assertTrue(matcher.shouldExclude(syncDir, excludedFile));
	}

	@Test
	public void testGlobPatternWildcard() {
		StorePathExclusionMatcher matcher = new StorePathExclusionMatcher(
			Arrays.asList("*.log")
		);

		File excludedFile = new File(syncDir, "data.log");
		File includedFile = new File(syncDir, "pagestore/page.xml");

		assertTrue(matcher.shouldExclude(syncDir, excludedFile));
		assertFalse(matcher.shouldExclude(syncDir, includedFile));
	}

	@Test
	public void testGlobPatternDirectory() {
		StorePathExclusionMatcher matcher = new StorePathExclusionMatcher(
			Arrays.asList("templatestore/test/**")
		);

		File excludedFile = new File(syncDir, "templatestore/test/test.xml");
		File includedFile = new File(syncDir, "templatestore/file.xml");

		assertTrue(matcher.shouldExclude(syncDir, excludedFile));
		assertFalse(matcher.shouldExclude(syncDir, includedFile));
	}

	@Test
	public void testMultiplePatterns() {
		StorePathExclusionMatcher matcher = new StorePathExclusionMatcher(
			Arrays.asList("path:/templatestore/translationstudio", "*.log", "pagestore/*")
		);

		assertTrue(matcher.shouldExclude(syncDir, new File(syncDir, "templatestore/translationstudio/data.xml")));
		assertTrue(matcher.shouldExclude(syncDir, new File(syncDir, "data.log")));
		assertTrue(matcher.shouldExclude(syncDir, new File(syncDir, "pagestore/page.xml")));
		assertFalse(matcher.shouldExclude(syncDir, new File(syncDir, "templatestore/file.xml")));
	}

	@Test
	public void testEmptyPatterns() {
		StorePathExclusionMatcher matcher = new StorePathExclusionMatcher(Collections.emptyList());

		assertFalse(matcher.shouldExclude(syncDir, new File(syncDir, "templatestore/file.xml")));
		assertEquals(0, matcher.getPatternCount());
	}

	@Test
	public void testInvalidGlobPattern() {
		// Invalid patterns should be logged and skipped, not throw exception
		StorePathExclusionMatcher matcher = new StorePathExclusionMatcher(
			Arrays.asList("**/*.xml", "[invalid")
		);

		// Should still work with valid patterns (**.xml matches any .xml file in subdirectories)
		assertTrue(matcher.shouldExclude(syncDir, new File(syncDir, "templatestore/file.xml")));
		// Invalid pattern should not cause errors
		assertEquals(2, matcher.getPatterns().size());
	}

	@Test
	public void testGetPatterns() {
		StorePathExclusionMatcher matcher = new StorePathExclusionMatcher(
			Arrays.asList("path:/test", "*.log")
		);

		assertEquals(2, matcher.getPatterns().size());
		assertTrue(matcher.getPatterns().contains("path:/test"));
		assertTrue(matcher.getPatterns().contains("*.log"));
	}

}
