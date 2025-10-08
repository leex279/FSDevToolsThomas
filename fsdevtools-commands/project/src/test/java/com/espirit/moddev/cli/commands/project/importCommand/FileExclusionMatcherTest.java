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

package com.espirit.moddev.cli.commands.project.importCommand;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class FileExclusionMatcherTest {

	private FileExclusionMatcher _matcher;

	@Test
	public void testWildcardPattern() {
		_matcher = new FileExclusionMatcher(Arrays.asList("*.log"));
		assertTrue(_matcher.shouldExclude(Paths.get("app.log")));
		assertFalse(_matcher.shouldExclude(Paths.get("app.txt")));
	}

	@Test
	public void testRecursivePattern() {
		_matcher = new FileExclusionMatcher(Arrays.asList("**/*.log"));
		assertTrue(_matcher.shouldExclude(Paths.get("dir/subdir/app.log")));
		// Note: **/*.log doesn't match files at root level in Java PathMatcher
		assertFalse(_matcher.shouldExclude(Paths.get("app.log")));
		assertFalse(_matcher.shouldExclude(Paths.get("app.txt")));
	}

	@Test
	public void testRecursivePatternWithRootLevel() {
		// To match both root and subdirectory files, use two patterns or {*.log,**/*.log}
		_matcher = new FileExclusionMatcher(Arrays.asList("*.log", "**/*.log"));
		assertTrue(_matcher.shouldExclude(Paths.get("dir/subdir/app.log")));
		assertTrue(_matcher.shouldExclude(Paths.get("app.log")));
		assertFalse(_matcher.shouldExclude(Paths.get("app.txt")));
	}

	@Test
	public void testDirectoryPattern() {
		_matcher = new FileExclusionMatcher(Arrays.asList("temp/*"));
		assertTrue(_matcher.shouldExclude(Paths.get("temp/file.txt")));
		assertFalse(_matcher.shouldExclude(Paths.get("data/file.txt")));
	}

	@Test
	public void testMultiplePatterns() {
		_matcher = new FileExclusionMatcher(Arrays.asList("*.log", "*.tmp", "backup/*"));
		assertTrue(_matcher.shouldExclude(Paths.get("app.log")));
		assertTrue(_matcher.shouldExclude(Paths.get("temp.tmp")));
		assertTrue(_matcher.shouldExclude(Paths.get("backup/data.txt")));
		assertFalse(_matcher.shouldExclude(Paths.get("data.txt")));
	}

	@Test
	public void testEmptyPatterns() {
		_matcher = new FileExclusionMatcher(Collections.emptyList());
		assertFalse(_matcher.shouldExclude(Paths.get("any-file.txt")));
	}

	@Test
	public void testInvalidPattern() {
		// Invalid pattern should be logged and skipped, not throw exception
		_matcher = new FileExclusionMatcher(Arrays.asList("valid*.log", "[invalid"));
		assertTrue(_matcher.shouldExclude(Paths.get("valid-file.log")));
		// Invalid pattern is ignored, so shouldn't match anything
	}

	@Test
	public void testCaseSensitivity() {
		_matcher = new FileExclusionMatcher(Arrays.asList("Test.java"));
		assertTrue(_matcher.shouldExclude(Paths.get("Test.java")));
		assertFalse(_matcher.shouldExclude(Paths.get("test.java"))); // Case-sensitive
	}

}
