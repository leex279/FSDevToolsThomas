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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * Utility class for matching FirstSpirit store paths against exclusion patterns.
 * Supports both exact path matching (path: prefix) and glob pattern matching.
 */
public class StorePathExclusionMatcher {

	private static final Logger LOGGER = LoggerFactory.getLogger(StorePathExclusionMatcher.class);
	private static final String PATH_PREFIX = "path:";

	private final List<String> _exactPaths;
	private final List<PathMatcher> _globMatchers;
	private final List<String> _rawPatterns;

	/**
	 * Creates a new exclusion matcher with the given patterns.
	 *
	 * @param patterns List of exclusion patterns. Each pattern can be:
	 *                 - An exact path with "path:" prefix (e.g., "path:/templatestore/folder")
	 *                 - A glob pattern (e.g., "*.log", "**&#47;test/**", "templatestore/**")
	 */
	public StorePathExclusionMatcher(@NotNull final List<String> patterns) {
		_rawPatterns = new ArrayList<>(patterns);
		_exactPaths = new ArrayList<>();
		_globMatchers = new ArrayList<>();

		for (String pattern : patterns) {
			if (pattern.startsWith(PATH_PREFIX)) {
				// Exact path match - remove the prefix and normalize
				String path = pattern.substring(PATH_PREFIX.length());
				// Normalize: remove leading slash if present
				if (path.startsWith("/")) {
					path = path.substring(1);
				}
				_exactPaths.add(path);
				LOGGER.debug("Added exact path exclusion: {}", path);
			} else {
				// Glob pattern match
				try {
					PathMatcher matcher = FileSystems.getDefault()
						.getPathMatcher("glob:" + pattern);
					_globMatchers.add(matcher);
					LOGGER.debug("Added glob pattern exclusion: {}", pattern);
				} catch (PatternSyntaxException e) {
					LOGGER.warn("Invalid exclude pattern '{}': {}", pattern, e.getMessage());
				}
			}
		}
	}

	/**
	 * Check if a file path should be excluded based on configured patterns.
	 * Converts filesystem paths to FirstSpirit store paths for matching.
	 *
	 * @param syncDir The sync directory root
	 * @param file The file to check
	 * @return true if the file matches any exclude pattern
	 */
	public boolean shouldExclude(@NotNull final File syncDir, @NotNull final File file) {
		// Get relative path from sync directory
		final Path syncDirPath = syncDir.toPath().toAbsolutePath();
		final Path filePath = file.toPath().toAbsolutePath();

		if (!filePath.startsWith(syncDirPath)) {
			return false;
		}

		final Path relativePath = syncDirPath.relativize(filePath);
		final String relativePathStr = relativePath.toString().replace('\\', '/');

		// Check exact path matches
		for (String exactPath : _exactPaths) {
			if (relativePathStr.equals(exactPath) || relativePathStr.startsWith(exactPath + "/")) {
				LOGGER.debug("Excluding '{}' - matches exact path: {}", relativePathStr, exactPath);
				return true;
			}
		}

		// Check glob pattern matches
		for (PathMatcher matcher : _globMatchers) {
			if (matcher.matches(relativePath)) {
				LOGGER.debug("Excluding '{}' - matches glob pattern", relativePathStr);
				return true;
			}
		}

		return false;
	}

	/**
	 * @return List of raw exclusion patterns configured
	 */
	public List<String> getPatterns() {
		return new ArrayList<>(_rawPatterns);
	}

	/**
	 * @return Number of exclusion patterns configured
	 */
	public int getPatternCount() {
		return _exactPaths.size() + _globMatchers.size();
	}

}
