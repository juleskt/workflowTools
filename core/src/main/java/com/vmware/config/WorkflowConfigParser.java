package com.vmware.config;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.vmware.config.commandLine.CommandLineArgumentsParser;
import com.vmware.config.section.PerforceClientConfig;
import com.vmware.util.scm.Git;
import com.vmware.util.scm.Perforce;
import com.vmware.util.exception.FatalException;
import com.vmware.util.logging.SimpleLogFormatter;
import com.vmware.http.json.ConfiguredGsonBuilder;
import com.vmware.util.ClasspathResource;
import com.vmware.util.StringUtils;
import com.vmware.util.exception.RuntimeIOException;
import com.vmware.util.logging.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

/**
 * Parses the workflow config from the source config files
 */
public class WorkflowConfigParser {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Git git = new Git();
    private final Gson gson = new ConfiguredGsonBuilder().setPrettyPrinting().build();
    private final CommandLineArgumentsParser argsParser = new CommandLineArgumentsParser();

    public WorkflowConfig parseWorkflowConfig(String[] args) {
        argsParser.generateArgumentMap(args);
        java.util.logging.Logger globalLogger = java.util.logging.Logger.getLogger("com.vmware");
        globalLogger.addHandler(createHandler());

        WorkflowConfig internalConfig = readInternalConfig();

        List<String> loadedConfigFiles = new ArrayList<String>();

        // apply twice so that setting a debug log level can be detected earlier
        applyRuntimeArguments(internalConfig);
        setLogLevel(internalConfig);

        internalConfig.setGitRemoteUrlAsReviewBoardRepo();

        applyRepoConfigFileIfExists(internalConfig, loadedConfigFiles);

        applyUserConfigFileIfExists(internalConfig, loadedConfigFiles);

        applySpecifiedConfigFiles(argsParser, internalConfig, loadedConfigFiles);

        WorkflowFields configurableFields = internalConfig.getConfigurableFields();
        Map<String, String> gitConfigValues = git.configValues();
        configurableFields.applyGitConfigValues("", gitConfigValues);

        if (git.isGitInstalled() && git.workingDirectoryIsInGitRepo()) {
            String trackingBranch = git.getTrackingBranch();
            String remoteName = trackingBranch != null ? trackingBranch.split("/")[0] : null;
            if (StringUtils.isNotBlank(remoteName)) {
                configurableFields.setFieldValue("defaultGitRemote", remoteName, "tracking remote");

                log.debug("Applying remote specific config values for git remote {}", remoteName);
                configurableFields.applyGitConfigValues(remoteName, gitConfigValues);
                String trackingBranchConfigPrefix = trackingBranch.replace('/', '.');
                log.debug("Applying tracking branch specific config values for git tracking branch", trackingBranch);
                configurableFields.applyGitConfigValues(trackingBranchConfigPrefix, gitConfigValues);
            }
        }

        applyRuntimeArguments(internalConfig);

        setLogLevel(internalConfig);

        internalConfig.loadedConfigFiles = loadedConfigFiles.toString();
        log.debug("Loaded config files: {}", internalConfig.loadedConfigFiles);

        if (StringUtils.isBlank(internalConfig.username)) {
            String[] parsedUsernameInfo = new UsernameParser(git).parse();
            if (parsedUsernameInfo != null) {
                internalConfig.setUsernameFromParsedValue(parsedUsernameInfo[0], parsedUsernameInfo[1]);
            }
        }

        log.trace("Workflow Config\n{}", gson.toJson(internalConfig));
        return internalConfig;
    }

    public void updateWithRuntimeArguments(WorkflowConfig config, String[] args) {
        argsParser.generateArgumentMap(args);
        applyRuntimeArguments(config);
        setLogLevel(config);
    }

    public String getRuntimeArgumentsText() {
        return argsParser.getArgumentsText();
    }

    private void applyRuntimeArguments(WorkflowConfig config) {
        try {
            config.applyRuntimeArguments(argsParser);
        } catch (IllegalArgumentException iae) {
            // handle gracefully as they are validation type exceptions
            log.error(iae.getMessage());
            System.exit(1);
        }
    }

    private void setLogLevel(WorkflowConfig internalConfig) {
        java.util.logging.Logger globalLogger = java.util.logging.Logger.getLogger("com.vmware");
        LogLevel logLevelToUse = internalConfig.loggingConfig.determineLogLevel();
        globalLogger.setLevel(logLevelToUse.getLevel());
        log.debug("Using log level {}", logLevelToUse);
    }

    /**
     * Applies values from configuration files explicitly specified either via the git workflow.configFile value or
     * via the command line.
     */
    private void applySpecifiedConfigFiles(CommandLineArgumentsParser argsParser, WorkflowConfig internalConfig, List<String> loadedConfigFiles) {
        String gitConfigFilePath = git.configValue("workflow.configFile");
        if (StringUtils.isBlank(gitConfigFilePath)) {
            gitConfigFilePath = git.configValue("workflow.config"); // backwards compatibility
        }
        String configFilePaths = argsParser.getArgument(gitConfigFilePath, "-c", "-config");
        if (StringUtils.isNotBlank(configFilePaths)) {
            WorkflowFields fields = internalConfig.getConfigurableFields();
            String[] configFiles = configFilePaths.split(",");
            for (String configFilePath : configFiles) {
                File configFile = new File(configFilePath);
                WorkflowConfig overriddenConfig = readExternalWorkflowConfig(configFile);
                fields.overrideValues(overriddenConfig, configFile.getPath());
                loadedConfigFiles.add(configFile.getPath());
            }
        }
    }

    private void applyRepoConfigFileIfExists(WorkflowConfig internalConfig, List<String> loadedConfigFiles) {
        File repoDirectory = git.getRootDirectory();
        if (repoDirectory == null) {
            PerforceClientConfig clientConfig = internalConfig.perforceClientConfig;
            Perforce perforce = new Perforce(clientConfig.perforceClientName, clientConfig.perforceClientDirectory);
            repoDirectory = perforce.getWorkingDirectory();
        }
        if (repoDirectory != null) {
            File repoWorkflowFile = new File(repoDirectory.getAbsolutePath() + File.separator + ".workflow-config.json");
            overrideConfigIfFileExists(internalConfig, repoWorkflowFile, loadedConfigFiles);
        }
    }

    private void applyUserConfigFileIfExists(WorkflowConfig internalConfig, List<String> loadedConfigFiles) {
        String homeFolder = System.getProperty("user.home");
        File userConfigFile = new File(homeFolder + File.separator + ".workflow-config.json");
        overrideConfigIfFileExists(internalConfig, userConfigFile, loadedConfigFiles);
    }

    private void overrideConfigIfFileExists(WorkflowConfig internalConfig, File repoWorkflowFile, List<String> loadedConfigFiles) {
        if (!repoWorkflowFile.exists()) {
            return;
        }
        WorkflowConfig repoConfig = readExternalWorkflowConfig(repoWorkflowFile);
        internalConfig.getConfigurableFields().overrideValues(repoConfig, repoWorkflowFile.getPath());
        loadedConfigFiles.add(repoWorkflowFile.getPath());
    }

    private WorkflowConfig readExternalWorkflowConfig(File configFilePath) {
        if (!configFilePath.exists()) {
            throw new FatalException("Config file {} does not exist", configFilePath.getPath());
        }

        Reader externalConfigReader = null;
        try {
            externalConfigReader = new FileReader(configFilePath);
        } catch (FileNotFoundException e) {
            throw new RuntimeIOException(e);
        }
        try {
            return gson.fromJson(externalConfigReader, WorkflowConfig.class);
        } catch (JsonSyntaxException e) {
            log.error("Syntax error in external config file {}:\n{}", configFilePath.getPath(), e.getMessage());
            System.exit(1);
            return null;
        }
    }

    private WorkflowConfig readInternalConfig() {
        Reader reader = new ClasspathResource("/internalConfig.json").getReader();
        return gson.fromJson(reader, WorkflowConfig.class);
    }

    private ConsoleHandler createHandler() {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleLogFormatter());
        handler.setLevel(Level.FINEST);
        return handler;
    }
}
