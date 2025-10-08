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

package com.espirit.moddev.cli.commands.export;

import com.espirit.moddev.cli.api.parsing.exceptions.IDProviderNotFoundException;
import com.espirit.moddev.cli.api.parsing.identifier.Identifier;
import com.espirit.moddev.cli.api.parsing.identifier.UidIdentifier;
import com.espirit.moddev.cli.api.parsing.parser.EntitiesIdentifierParser;
import com.espirit.moddev.cli.api.parsing.parser.PathIdentifierParser;
import com.espirit.moddev.cli.api.parsing.parser.ProjectPropertiesParser;
import com.espirit.moddev.cli.api.parsing.parser.RegistryBasedParser;
import com.espirit.moddev.cli.api.parsing.parser.RootNodeIdentifierParser;
import com.espirit.moddev.cli.api.parsing.parser.SchemaIdentifierParser;
import com.espirit.moddev.cli.api.parsing.parser.UidIdentifierParser;
import com.espirit.moddev.cli.api.annotations.ParameterExamples;
import com.espirit.moddev.cli.api.annotations.ParameterType;
import com.espirit.moddev.cli.commands.PermissionsMode;
import com.espirit.moddev.cli.commands.SimpleCommand;
import com.espirit.moddev.cli.commands.StorePathExclusionMatcher;
import com.espirit.moddev.cli.commands.help.HelpCommand;
import com.espirit.moddev.cli.results.ExportResult;
import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.OptionType;
import com.github.rvesse.airline.annotations.restrictions.AllowedRawValues;
import org.apache.commons.lang3.StringUtils;
import de.espirit.firstspirit.access.store.IDProvider;
import de.espirit.firstspirit.access.store.Store;
import de.espirit.firstspirit.agency.OperationAgent;
import de.espirit.firstspirit.agency.StoreAgent;
import de.espirit.firstspirit.store.access.nexport.operations.ExportOperation;
import de.espirit.firstspirit.transport.PropertiesTransportOptions;
import de.espirit.firstspirit.transport.PropertiesTransportOptions.ProjectPropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class gathers shared logic and options for different export commands. It can be extended for custom implementations of uid filtering, or to
 * override configurations.
 */
public abstract class AbstractExportCommand extends SimpleCommand<ExportResult> {

	private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Option(name = "--keepObsoleteFiles",
			description = "Keep obsolete files in sync dir which are deleted in project (default = false).",
			title = "keepObsoleteFiles")
	private boolean _keepObsoleteFiles;

	@Option(name = "--excludeChildElements",
			description = "Exclude child store elements (default = false).",
			title = "excludeChildElements")
	private boolean _excludeChildElements;

	@Option(name = "--excludeParentElements",
			description = "Exclude parent store elements (default = false).",
			title = "excludeParentElements")
	private boolean _excludeParentElements;

	@Option(name = "--useReleaseState",
			description = "Export only the release state of store elements (default = false , export of current state).",
			title = "useReleaseState")
	private boolean _exportReleaseState;

	@Option(name = "--includeProjectProperties",
			description = "DEPRECATED: use '" + ProjectPropertiesParser.CUSTOM_PREFIX_PROJECT_PROPERTIES + ":" + ProjectPropertiesParser.ALL + "' instead. Export with project properties like resolutions or fonts.",
			title = "includeProjectProperties")
	private boolean _includeProjectProperties;

	@Option(name = "--permissionMode",
			description = "Set the permission mode for the export (default = NONE). Possible values are [NONE, ALL, STORE_ELEMENT, WORKFLOW].",
			title = "permissionMode")
	@AllowedRawValues(ignoreCase = true, allowedValues = {"NONE", "ALL", "STORE_ELEMENT", "WORKFLOW"})
	private PermissionsMode _permissionMode = PermissionsMode.NONE;

	@Option(type = OptionType.COMMAND, name = {"-ex", "--exclude"},
			description = "Comma separated list of paths/patterns to exclude from export. Use 'path:/store/folder' for exact paths or glob patterns like '**/*.log'")
	@ParameterExamples(
			examples = {
					"--exclude 'path:templatestore/translation_studio'",
					"-ex 'templatestore/test/**,*.log'",
					"--exclude 'path:pagestore/folder,*.tmp'"
			},
			descriptions = {
					"Exclude exact path using path: prefix",
					"Exclude paths matching glob patterns",
					"Combine exact paths and patterns"
			}
	)
	@ParameterType(name = "List<String>")
	private String _excludePatterns;

	@Arguments(title = "identifiers",
			description = "A list of various parsable identifiers. Please have a look at the command description for further information.")
	private List<String> _identifiers = new ArrayList<>();
	private RegistryBasedParser parser;

	/**
	 * Creates a new AbstractExportCommand and configures a set of default argument parsers.
	 */
	public AbstractExportCommand() {
		parser = new RegistryBasedParser();
		parser.registerParser(new RootNodeIdentifierParser());
		parser.registerParser(new EntitiesIdentifierParser());
		parser.registerParser(new UidIdentifierParser());
		parser.registerParser(new ProjectPropertiesParser());
		parser.registerParser(new SchemaIdentifierParser());
		parser.registerParser(new PathIdentifierParser());
	}

	/**
	 * Gets delete obsolete files.
	 *
	 * @return the delete obsolete files
	 */
	public boolean isDeleteObsoleteFiles() {
		return !_keepObsoleteFiles;
	}

	/**
	 * Gets export child elements.
	 *
	 * @return the export child elements
	 */
	public boolean isExportChildElements() {
		return !_excludeChildElements;
	}

	/**
	 * Gets export parent elements.
	 *
	 * @return the export parent elements
	 */
	public boolean isExportParentElements() {
		return !_excludeParentElements;
	}

	/**
	 * Indicates whether the release state should be used for belonging ExportOperation.
	 *
	 * @return true: operate on release state or false (default): on the current state.
	 */
	public boolean isExportReleaseState() {
		return _exportReleaseState;
	}

	/**
	 * Defines whether this command should export the release state or not (default)
	 *
	 * @param exportReleaseState use {@code true} to export release state, {@code false} otherwise (default)
	 * @see #isExportReleaseState()
	 */
	public void setExportReleaseState(boolean exportReleaseState) {
		this._exportReleaseState = exportReleaseState;
	}

	/**
	 * Log release state.
	 *
	 * @param idProvider the id provider
	 */
	protected void logReleaseState(final IDProvider idProvider) {
		LOGGER.debug(idProvider.getUid() + " is release state? " + idProvider.getStore().isRelease());
	}

	/**
	 * Adds elements to the given export operation. Uses registered parsers to retrieve elements.
	 *
	 * @param storeAgent      the StoreAgent to retrieve IDProviders with
	 * @param identifiers     the identifiers of elements that should be added to the ExportOperation
	 * @param exportOperation the ExportOperation to add the elements to
	 * @throws IllegalArgumentException    if the ExportOperation is null
	 * @throws IDProviderNotFoundException if {@link Identifier#addToExportOperation(StoreAgent, boolean, ExportOperation)} throws it
	 */
	public void addExportElements(final StoreAgent storeAgent, final List<Identifier> identifiers, final ExportOperation exportOperation) {
		if (exportOperation == null) {
			throw new IllegalArgumentException("No null ExportOperation allowed");
		}

		LOGGER.debug("Adding export elements...");
		if (identifiers.isEmpty()) {
			LOGGER.error("no identifiers found - pass at least 1 identifier --> call 'fs-cli help export' for details");
		} else {
			LOGGER.debug("addExportedElements - UIDs {}", identifiers);
			for (Identifier identifier : identifiers) {
				identifier.addToExportOperation(storeAgent, isExportReleaseState(), exportOperation);
			}

			if (isIncludeProjectProperties()) {
				LOGGER.warn("usage of flag '--includeProjectProperties' is deprecated - use {}:{}' instead", ProjectPropertiesParser.CUSTOM_PREFIX_PROJECT_PROPERTIES, ProjectPropertiesParser.ALL);
				addProjectProperties(exportOperation);
			}
		}
	}

	/**
	 * Get a list of {@link UidIdentifier}s that specify the elements that should be synchronized.
	 *
	 * @return a {@link java.util.List} of {@link UidIdentifier}s that specify the elements that should be synchronized
	 */
	public List<Identifier> getIdentifiers() {
		return _identifiers.isEmpty() ? Collections.emptyList() : parser.parse(_identifiers);
	}

	/**
	 * Add project properties.
	 *
	 * @param exportOperation the export operation
	 */
	public static void addProjectProperties(final ExportOperation exportOperation) {
		final PropertiesTransportOptions options = exportOperation.configurePropertiesExport();
		final EnumSet<ProjectPropertyType> propertiesToTransport = EnumSet.allOf(ProjectPropertyType.class);
		options.setProjectPropertiesTransport(propertiesToTransport);
	}

	/**
	 * Adds store root nodes to the given export operation directly. This operation is different from adding a store root node's children
	 * individually. This method can be overridden to add a subset of store roots only.
	 *
	 * @param storeAgent      the StoreAgent to retrieve store roots from
	 * @param exportOperation the ExportOperation to add the store roots to
	 */
	protected void addStoreRoots(final StoreAgent storeAgent, final ExportOperation exportOperation) {
		for (final Store.Type storeType : Store.Type.values()) {
			exportOperation.addElement(storeAgent.getStore(storeType, isExportReleaseState()));
		}
	}

	/**
	 * Sets include project properties.
	 *
	 * @param includeProjectProperties the with project properties
	 */
	public void setIncludeProjectProperties(final boolean includeProjectProperties) {
		this._includeProjectProperties = includeProjectProperties;
	}

	/**
	 * Include project properties.
	 *
	 * @return the boolean
	 */
	public boolean isIncludeProjectProperties() {
		return _includeProjectProperties;
	}

	/**
	 * Creates an {@link de.espirit.firstspirit.store.access.nexport.operations.ExportOperation} based on the current configuration and exports the
	 * elements to the file system.
	 *
	 * @return the export result
	 */
	protected ExportResult exportStoreElements() {
		try {
			// no arguments --> call help-command
			final List<Identifier> identifierList = getIdentifiers();
			if (identifierList.isEmpty()) {
				LOGGER.error("no identifiers for export command found - pass at least 1 identifier --> see 'fs-cli help export' for details\nfs-cli help export");
				final HelpCommand helpCommand = new HelpCommand();
				helpCommand.addArguments("export");
				helpCommand.call();
				// return result with exception to force exit code 1
				final IllegalArgumentException exception = new IllegalArgumentException("no identifiers for export command found - pass at least 1 identifier --> see help message above");
				exception.setStackTrace(new StackTraceElement[0]);
				return new ExportResult(exception);
			}

			// create export operation
			final ExportOperation exportOperation = this.getContext().requireSpecialist(OperationAgent.TYPE).getOperation(ExportOperation.TYPE);
			exportOperation.configurePermissionTransport().setPermissionTransport(_permissionMode.getFirstSpiritPermissionMode());
			exportOperation.setDeleteObsoleteFiles(isDeleteObsoleteFiles());
			exportOperation.setExportChildElements(isExportChildElements());
			exportOperation.setExportParentElements(isExportParentElements());
			exportOperation.setExportRelease(isExportReleaseState());
			addExportElements(this.getContext().requireSpecialist(StoreAgent.TYPE), identifierList, exportOperation);

			// export
			final String syncDirStr = getSynchronizationDirectoryString();
			LOGGER.info("exporting to directory '{}'", syncDirStr);
			final ExportOperation.Result exportResult = exportOperation.perform(getSynchronizationDirectory(syncDirStr));

			// Apply exclusion cleanup if patterns are provided
			final List<String> excludePatterns = parseExcludePatterns();
			if (!excludePatterns.isEmpty()) {
				applyExclusionCleanup(new File(syncDirStr), excludePatterns);
			}

			return new ExportResult(getContext().requireSpecialist(StoreAgent.TYPE), exportResult);
		} catch (final Exception e) {
			return new ExportResult(e);
		}
	}

	/**
	 * Adds the given string based UidIdentifier to this command's argument list. This method doesn't validate the input at all.
	 *
	 * @param identifier the string based UidIdentifier that should be added to this command's argument list
	 */
	public void addIdentifier(final String identifier) {
		_identifiers.add(identifier);
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
	 * Applies exclusion cleanup to the sync directory after export.
	 * Deletes files and folders matching the exclusion patterns.
	 * Also filters the .FirstSpirit metadata files to remove references to excluded elements.
	 *
	 * @param syncDir The sync directory
	 * @param excludePatterns List of exclusion patterns
	 */
	private void applyExclusionCleanup(final File syncDir, final List<String> excludePatterns) {
		final StorePathExclusionMatcher matcher = new StorePathExclusionMatcher(excludePatterns);
		LOGGER.info("Applying exclusion patterns: {}", String.join(", ", excludePatterns));

		// Collect excluded paths for metadata filtering
		final List<String> excludedPaths = new ArrayList<>();

		int excludedCount = 0;
		try (Stream<Path> paths = Files.walk(syncDir.toPath())) {
			// Collect paths to delete (in reverse order to delete children before parents)
			final List<File> filesToDelete = paths
				.map(Path::toFile)
				.filter(file -> matcher.shouldExclude(syncDir, file))
				.sorted((a, b) -> -a.getAbsolutePath().compareTo(b.getAbsolutePath()))
				.collect(Collectors.toList());

			for (File file : filesToDelete) {
				try {
					// Track excluded paths for metadata filtering
					final Path relativePath = syncDir.toPath().relativize(file.toPath());
					excludedPaths.add(relativePath.toString().replace('\\', '/'));

					if (file.isDirectory()) {
						// Only delete if directory is empty (children already deleted)
						if (file.list() != null && file.list().length == 0) {
							Files.delete(file.toPath());
							LOGGER.debug("Deleted excluded directory: {}", file.getAbsolutePath());
							excludedCount++;
						}
					} else {
						Files.delete(file.toPath());
						LOGGER.debug("Deleted excluded file: {}", file.getAbsolutePath());
						excludedCount++;
					}
				} catch (IOException e) {
					LOGGER.warn("Failed to delete excluded file: {}", file.getAbsolutePath(), e);
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Failed to walk sync directory for exclusion cleanup", e);
		}

		LOGGER.info("Excluded {} files/folders from export", excludedCount);

		// Filter .FirstSpirit metadata files
		filterFirstSpiritMetadata(syncDir, excludedPaths);
	}

	/**
	 * Filters .FirstSpirit metadata files to remove references to excluded paths.
	 *
	 * @param syncDir The sync directory
	 * @param excludedPaths List of excluded relative paths
	 */
	private void filterFirstSpiritMetadata(final File syncDir, final List<String> excludedPaths) {
		if (excludedPaths.isEmpty()) {
			return;
		}

		final File firstSpiritDir = new File(syncDir, ".FirstSpirit");
		if (!firstSpiritDir.exists() || !firstSpiritDir.isDirectory()) {
			return;
		}

		// Find Import_*.txt files
		final File[] importFiles = firstSpiritDir.listFiles((dir, name) ->
			name.startsWith("Import_") && name.endsWith(".txt"));

		if (importFiles != null && importFiles.length > 0) {
			for (File importFile : importFiles) {
				try {
					filterImportMetadataFile(importFile, excludedPaths);
				} catch (IOException e) {
					LOGGER.warn("Failed to filter Import metadata file: {}", importFile.getName(), e);
				}
			}
		}

		// Find Files_*.txt files
		final File[] filesFiles = firstSpiritDir.listFiles((dir, name) ->
			name.startsWith("Files_") && name.endsWith(".txt"));

		if (filesFiles != null && filesFiles.length > 0) {
			for (File filesFile : filesFiles) {
				try {
					filterFilesMetadataFile(filesFile, excludedPaths);
				} catch (IOException e) {
					LOGGER.warn("Failed to filter Files metadata file: {}", filesFile.getName(), e);
				}
			}
		}
	}

	/**
	 * Filters an Import metadata file to remove entries for excluded paths.
	 *
	 * @param importFile The Import_*.txt file
	 * @param excludedPaths List of excluded relative paths
	 */
	private void filterImportMetadataFile(final File importFile, final List<String> excludedPaths) throws IOException {
		final List<String> lines = Files.readAllLines(importFile.toPath());
		final List<String> filteredLines = new ArrayList<>();

		int removedEntries = 0;
		boolean inExcludedBlock = false;
		boolean isHeaderSection = true;

		for (int i = 0; i < lines.size(); i++) {
			final String line = lines.get(i);

			// Keep header lines (comments and metadata)
			if (line.startsWith("#") || line.trim().isEmpty()) {
				if (!inExcludedBlock) {
					filteredLines.add(line);
				}
				continue;
			}

			isHeaderSection = false;

			// Check if this is a block start [ID]
			if (line.startsWith("[") && line.endsWith("]")) {
				// Check if the next lines contain a name that should be excluded
				inExcludedBlock = false;

				// Look ahead to find the name field
				for (int j = i + 1; j < lines.size() && j < i + 15; j++) {
					final String nextLine = lines.get(j);
					if (nextLine.startsWith("[")) {
						// Reached next block
						break;
					}
					if (nextLine.startsWith("name=")) {
						final String name = nextLine.substring(5);
						if (isPathExcluded(name, excludedPaths)) {
							inExcludedBlock = true;
							removedEntries++;
							LOGGER.debug("Excluding metadata entry: {}", name);
						}
						break;
					}
				}

				if (!inExcludedBlock) {
					filteredLines.add(line);
				}
			} else if (!inExcludedBlock) {
				filteredLines.add(line);
			}
		}

		if (removedEntries > 0) {
			Files.write(importFile.toPath(), filteredLines);
			LOGGER.info("Removed {} entries from metadata file: {}", removedEntries, importFile.getName());
		}
	}

	/**
	 * Filters a Files metadata file to remove entries for excluded IDs.
	 *
	 * @param filesFile The Files_*.txt file
	 * @param excludedPaths List of excluded relative paths
	 */
	private void filterFilesMetadataFile(final File filesFile, final List<String> excludedPaths) throws IOException {
		// First, collect the excluded IDs from the corresponding Import file
		final String filesFileName = filesFile.getName();
		final String importFileName = filesFileName.replace("Files_", "Import_");
		final File importFile = new File(filesFile.getParent(), importFileName);

		if (!importFile.exists()) {
			LOGGER.debug("No corresponding Import file found for: {}", filesFileName);
			return;
		}

		// Collect excluded IDs from Import file
		final java.util.Set<String> excludedIds = collectExcludedIds(importFile, excludedPaths);

		if (excludedIds.isEmpty()) {
			return;
		}

		// Filter Files_*.txt based on excluded IDs
		final List<String> lines = Files.readAllLines(filesFile.toPath());
		final List<String> filteredLines = new ArrayList<>();

		boolean inExcludedBlock = false;
		int removedBlocks = 0;

		for (String line : lines) {
			// Check if this is a block start [ID]
			if (line.startsWith("[") && line.endsWith("]")) {
				final String id = line.substring(1, line.length() - 1);
				inExcludedBlock = excludedIds.contains(id);
				if (inExcludedBlock) {
					removedBlocks++;
					LOGGER.debug("Excluding Files metadata block: [{}]", id);
				}
			}

			if (!inExcludedBlock) {
				filteredLines.add(line);
			}
		}

		if (removedBlocks > 0) {
			Files.write(filesFile.toPath(), filteredLines);
			LOGGER.info("Removed {} file blocks from metadata: {}", removedBlocks, filesFile.getName());
		}
	}

	/**
	 * Collects IDs of entries that should be excluded from an Import metadata file.
	 *
	 * @param importFile The Import_*.txt file
	 * @param excludedPaths List of excluded relative paths
	 * @return Set of IDs to exclude
	 */
	private java.util.Set<String> collectExcludedIds(final File importFile, final List<String> excludedPaths) throws IOException {
		final java.util.Set<String> excludedIds = new java.util.HashSet<>();
		final List<String> lines = Files.readAllLines(importFile.toPath());

		String currentId = null;

		for (int i = 0; i < lines.size(); i++) {
			final String line = lines.get(i);

			// Skip header lines
			if (line.startsWith("#") || line.trim().isEmpty()) {
				continue;
			}

			// Check if this is a block start [ID]
			if (line.startsWith("[") && line.endsWith("]")) {
				currentId = line.substring(1, line.length() - 1);

				// Look ahead to find the name field
				for (int j = i + 1; j < lines.size() && j < i + 15; j++) {
					final String nextLine = lines.get(j);
					if (nextLine.startsWith("[")) {
						break;
					}
					if (nextLine.startsWith("name=")) {
						final String name = nextLine.substring(5);
						if (isPathExcluded(name, excludedPaths)) {
							excludedIds.add(currentId);
							LOGGER.debug("Collected excluded ID: {}", currentId);
						}
						break;
					}
				}
			}
		}

		return excludedIds;
	}

	/**
	 * Checks if a metadata entry name matches any excluded path.
	 *
	 * @param name The name from the metadata (e.g., "/translation_studio")
	 * @param excludedPaths List of excluded paths
	 * @return true if the path should be excluded
	 */
	private boolean isPathExcluded(final String name, final List<String> excludedPaths) {
		final String normalizedName = name.startsWith("/") ? name.substring(1) : name;

		for (String excludedPath : excludedPaths) {
			// Extract just the folder name from the excluded path
			// e.g., "TemplateStore/PageTemplates/translation_studio" -> "translation_studio"
			final String[] pathParts = excludedPath.split("/");
			final String folderName = pathParts[pathParts.length - 1];

			if (normalizedName.equals(folderName) || normalizedName.endsWith("/" + folderName)) {
				return true;
			}
		}

		return false;
	}

}
