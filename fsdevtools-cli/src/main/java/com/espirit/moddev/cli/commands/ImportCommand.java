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

import com.espirit.moddev.cli.CliConstants;
import com.espirit.moddev.cli.api.annotations.ParameterExamples;
import com.espirit.moddev.cli.api.annotations.ParameterType;
import com.espirit.moddev.cli.api.configuration.ImportConfig;
import com.espirit.moddev.cli.common.StringPropertiesMap;
import com.espirit.moddev.cli.results.ImportResult;
import com.espirit.moddev.cli.schema.SchemaUidToNameBasedLayerMapper;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.OptionType;
import com.github.rvesse.airline.annotations.help.Examples;
import de.espirit.firstspirit.agency.OperationAgent;
import de.espirit.firstspirit.agency.StoreAgent;
import de.espirit.firstspirit.io.FileSystem;
import de.espirit.firstspirit.io.FileSystemsAgent;
import de.espirit.firstspirit.store.access.nexport.operations.ImportOperation;
import de.espirit.firstspirit.transport.ImportPermissionTransportOptions;
import de.espirit.firstspirit.transport.LayerMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Command that executes a FirstSpirit ImportOperation. Uses a FirstSpirit context.
 */
@Command(name = "import", description = "Imports a FirstSpirit project into a FirstSpirit Server.")
@Examples(
		examples = {
				"import -lm *:CREATE_NEW",
				"import -lm my_schema:CREATE_NEW",
				"import -lm *:targetLayer",
				"import -lm schema_a:targetLayer_a,schema_b:targetLayer_b",
				"import --exclude 'path:/templatestore/translationstudio'",
				"import --exclude 'templatestore/test/**,*.log'"
		},
		descriptions = {
				"Import project and create for every unknown source schema a new target layer (use if uncertain)",
				"Import project and create for source schema 'my_schema' a new layer",
				"Import project and redirect every unknown source schema into given target layer. The target layer must be attached to the project! (use with caution)",
				"Import project and use specified mapping for source schemas and existing target layers. The target layers must be attached to the project! (use with caution)",
				"Import project while excluding exact path using path: prefix",
				"Import project while excluding paths matching glob patterns"
		}
)
public class ImportCommand extends SimpleCommand<ImportResult> implements ImportConfig {

	/**
	 * The Constant LOGGER.
	 */
	protected static final Logger LOGGER = LoggerFactory.getLogger(ImportCommand.class);

	/**
	 * The import comment.
	 */
	@Option(name = {"-i", "--import-comment"}, description = "Import comment for FirstSpirit revision")
	private String importComment;

	/**
	 * The dont create project if missing.
	 */
	@Option(name = {"--dont-create-project"}, description = "Do not create project in FirstSpirit if it is missing")
	private boolean dontCreateProjectIfMissing;

	/**
	 * The dont create entities.
	 */
	@Option(name = {"--dont-create-entities"}, description = "Do not create entities when importing")
	private boolean dontCreateEntities;

	/**
	 * import the schedule entry active state.
	 */
	@Option(name = {"--import-schedule-entry-active-state"}, description = "Import the active state for schedule entries during import")
	boolean importScheduleEntryActiveState;

	@Option(name = "--permissionMode",
			description = "Set the permission mode for the import (default = ALL). Possible values are [NONE, ALL, STORE_ELEMENT, WORKFLOW]")
	private PermissionsMode _permissionMode = PermissionsMode.ALL;

	@Option(name = "--updateExistingPermissions",
			description = "Overwrite permissions for already existing elements during import (default = false). Setting this will have no effect if the permission mode is set to NONE.")
	private boolean _updateExistingPermissions;

	/**
	 * The layer mapping.
	 */
	@Option(name = {"-lm", "--layerMapping"},
			description = "Defines how unknown layers should be mapped in the target; comma-separated key-value pairs by : or =; key is source schema UID; value is target layer name; see EXAMPLES for more information",
			type = OptionType.COMMAND)
	private String layerMapping;

	@Option(type = OptionType.COMMAND, name = {"-ex", "--exclude"},
			description = "Comma separated list of paths/patterns to exclude from import. Use 'path:/store/folder' for exact paths or glob patterns like '**/*.log'")
	@ParameterExamples(
			examples = {
					"--exclude 'path:/templatestore/translationstudio'",
					"-ex 'templatestore/test/**,*.log'",
					"--exclude 'path:/pagestore/folder,*.tmp'"
			},
			descriptions = {
					"Exclude exact path using path: prefix",
					"Exclude paths matching glob patterns",
					"Combine exact paths and patterns"
			}
	)
	@ParameterType(name = "List<String>")
	private String _excludePatterns;

	public ImportCommand() {
		super();
	}

	@Override
	public boolean isCreatingProjectIfMissing() {
		return !dontCreateProjectIfMissing;
	}

	@Override
	public String getImportComment() {
		if (importComment == null || importComment.isEmpty()) {
			final boolean environmentContainsImportComment = getEnvironment().containsKey(CliConstants.KEY_FS_IMPORT_COMMENT.value());
			if (environmentContainsImportComment) {
				return getEnvironment().get(CliConstants.KEY_FS_IMPORT_COMMENT.value()).trim();
			}
			return CliConstants.DEFAULT_IMPORT_COMMENT.value();
		}
		return importComment;
	}

	@Override
	public ImportResult call() {
		LOGGER.info("Importing...");
		try {
			final OperationAgent operationAgent = getContext().requireSpecialist(OperationAgent.TYPE);
			final ImportOperation importOperation = operationAgent.getOperation(ImportOperation.TYPE);
			final ImportPermissionTransportOptions permissionTransportOptions = importOperation.configurePermissionTransport();
			permissionTransportOptions.setPermissionTransport(_permissionMode.getFirstSpiritPermissionMode());
			permissionTransportOptions.setUpdateExistingPermissions(_updateExistingPermissions);
			importOperation.setIgnoreEntities(dontCreateEntities);
			importOperation.setRevisionComment(getImportComment());
			importOperation.setLayerMapper(configureLayerMapper());
			// We do only override the settings if the 'importScheduleEntryActiveState'-flag is set to 'true'.
			// This is only needed to prevent a hard dependency to the latest FirstSpirit version
			// If the default value of this flag changes in FirstSpirit, we need to change this behaviour as well.
			if (importScheduleEntryActiveState) {
				importOperation.setImportScheduleEntryActiveState(true);
			}
			final String syncDirStr = getSynchronizationDirectoryString();

			// Apply exclusion filtering if patterns are provided
			String dirPathToImport = syncDirStr;
			File tempFilteredDir = null;
			final List<String> excludePatterns = parseExcludePatterns();
			if (!excludePatterns.isEmpty()) {
				final StorePathExclusionMatcher matcher = new StorePathExclusionMatcher(excludePatterns);
				LOGGER.info("Applying exclusion patterns: {}", String.join(", ", excludePatterns));
				final File syncDir = new File(syncDirStr);
				tempFilteredDir = createFilteredSyncDirectory(syncDir, matcher);
				dirPathToImport = tempFilteredDir.getAbsolutePath();
			}

			try {
				LOGGER.info("importing from directory '{}'", dirPathToImport);
				final FileSystemsAgent fileSystemsAgent = getContext().requireSpecialist(FileSystemsAgent.TYPE);
				final FileSystem<?> fileSystem = fileSystemsAgent.getOSFileSystem(dirPathToImport);
				final ImportOperation.Result result = importOperation.perform(fileSystem);
				return new ImportResult(getContext().requireSpecialist(StoreAgent.TYPE), result);
			} finally {
				// Clean up temporary filtered directory if created
				if (tempFilteredDir != null && tempFilteredDir.exists()) {
					deleteDirectory(tempFilteredDir);
				}
			}
		} catch (final Exception e) {
			return new ImportResult(e);
		}
	}

	private LayerMapper configureLayerMapper() {
		final LayerMapper layerMapper;
		if (layerMapping == null || layerMapping.trim().isEmpty()) {
			LOGGER.debug("Layer mapping is empty!");
			layerMapper = SchemaUidToNameBasedLayerMapper.empty();
		} else {
			LOGGER.debug("Layer mapping: {}", layerMapping);
			final StringPropertiesMap mappingParser = new StringPropertiesMap(layerMapping);
			layerMapper = SchemaUidToNameBasedLayerMapper.from(mappingParser);
		}
		return layerMapper;
	}

	/**
	 * Sets the layer mapping.
	 *
	 * @param layerMapping the new layer mapping
	 */
	public void setLayerMapping(final String layerMapping) {
		this.layerMapping = layerMapping;
	}

	/**
	 * Sets the creates the project if missing.
	 *
	 * @param createProjectIfMissing the new creates the project if missing
	 */
	public void setCreateProjectIfMissing(final boolean createProjectIfMissing) {
		this.dontCreateProjectIfMissing = !createProjectIfMissing;
	}

	/**
	 * Parse exclude patterns from comma-separated string.
	 *
	 * @return List of exclude patterns
	 */
	protected List<String> parseExcludePatterns() {
		if (StringUtils.isBlank(_excludePatterns)) {
			return Collections.emptyList();
		}
		return Arrays.stream(StringUtils.split(_excludePatterns, ","))
				.filter(StringUtils::isNotBlank)
				.map(String::trim)
				.collect(Collectors.toList());
	}

	/**
	 * Creates a filtered copy of the sync directory excluding files matching the patterns.
	 *
	 * @param sourceDir The source sync directory
	 * @param matcher The exclusion matcher
	 * @return A temporary directory with filtered content
	 * @throws IOException if file operations fail
	 */
	private File createFilteredSyncDirectory(final File sourceDir, final StorePathExclusionMatcher matcher) throws IOException {
		final File tempDir = Files.createTempDirectory("fs-import-filtered-").toFile();
		int excludedCount = 0;
		int includedCount = 0;

		try (Stream<Path> paths = Files.walk(sourceDir.toPath())) {
			for (Path sourcePath : paths.collect(Collectors.toList())) {
				final File sourceFile = sourcePath.toFile();

				// Check if this file should be excluded
				if (matcher.shouldExclude(sourceDir, sourceFile)) {
					LOGGER.debug("Excluding: {}", sourceFile.getAbsolutePath());
					excludedCount++;
					continue;
				}

				// Calculate target path
				final Path relativePath = sourceDir.toPath().relativize(sourcePath);
				final Path targetPath = tempDir.toPath().resolve(relativePath);

				// Copy file or create directory
				if (sourceFile.isDirectory()) {
					Files.createDirectories(targetPath);
				} else {
					Files.createDirectories(targetPath.getParent());
					Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
					includedCount++;
				}
			}
		}

		LOGGER.info("Excluded {} files/folders matching patterns, included {} files", excludedCount, includedCount);
		return tempDir;
	}

	/**
	 * Recursively delete a directory and its contents.
	 *
	 * @param directory The directory to delete
	 */
	private void deleteDirectory(final File directory) {
		try (Stream<Path> paths = Files.walk(directory.toPath())) {
			paths.sorted((a, b) -> -a.compareTo(b)) // Reverse order to delete files before directories
				.forEach(path -> {
					try {
						Files.delete(path);
					} catch (IOException e) {
						LOGGER.warn("Failed to delete temporary file: {}", path, e);
					}
				});
		} catch (IOException e) {
			LOGGER.warn("Failed to clean up temporary directory: {}", directory.getAbsolutePath(), e);
		}
	}
}
