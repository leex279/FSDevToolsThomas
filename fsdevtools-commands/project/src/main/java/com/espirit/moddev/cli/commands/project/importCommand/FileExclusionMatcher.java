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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * Utility class for matching file paths against glob exclusion patterns.
 * Uses Java NIO PathMatcher for glob pattern matching.
 */
public class FileExclusionMatcher {

	private static final Logger LOGGER = LoggerFactory.getLogger(FileExclusionMatcher.class);
	private final List<PathMatcher> _matchers;
	private final List<String> _rawPatterns;

	public FileExclusionMatcher(@NotNull final List<String> patterns) {
		_rawPatterns = new ArrayList<>(patterns);
		_matchers = new ArrayList<>();

		for (String pattern : patterns) {
			try {
				PathMatcher matcher = FileSystems.getDefault()
					.getPathMatcher("glob:" + pattern);
				_matchers.add(matcher);
			} catch (PatternSyntaxException e) {
				LOGGER.warn("Invalid exclude pattern '{}': {}", pattern, e.getMessage());
			}
		}
	}

	/**
	 * Check if a path should be excluded based on configured patterns.
	 * @param path the path to check (relative to import base)
	 * @return true if path matches any exclude pattern
	 */
	public boolean shouldExclude(@NotNull final Path path) {
		for (PathMatcher matcher : _matchers) {
			if (matcher.matches(path)) {
				return true;
			}
		}
		return false;
	}

	public List<String> getPatterns() {
		return new ArrayList<>(_rawPatterns);
	}

}
