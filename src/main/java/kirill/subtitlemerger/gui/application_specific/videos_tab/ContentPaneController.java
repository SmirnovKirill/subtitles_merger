package kirill.subtitlemerger.gui.application_specific.videos_tab;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import kirill.subtitlemerger.gui.GuiConstants;
import kirill.subtitlemerger.gui.GuiContext;
import kirill.subtitlemerger.gui.GuiSettings;
import kirill.subtitlemerger.gui.application_specific.AbstractController;
import kirill.subtitlemerger.gui.application_specific.SubtitlePreviewController;
import kirill.subtitlemerger.gui.application_specific.videos_tab.loaders_and_handlers.*;
import kirill.subtitlemerger.gui.application_specific.videos_tab.table_with_files.GuiExternalSubtitleStream;
import kirill.subtitlemerger.gui.application_specific.videos_tab.table_with_files.GuiFileInfo;
import kirill.subtitlemerger.gui.application_specific.videos_tab.table_with_files.TableFileInfo;
import kirill.subtitlemerger.gui.application_specific.videos_tab.table_with_files.TableSubtitleOption;
import kirill.subtitlemerger.gui.application_specific.videos_tab.table_with_files.TableWithFiles;
import kirill.subtitlemerger.gui.utils.GuiUtils;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunner;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunnerCallback;
import kirill.subtitlemerger.gui.utils.custom_controls.ActionResultLabels;
import kirill.subtitlemerger.gui.utils.entities.NodeAndController;
import kirill.subtitlemerger.logic.core.SubtitleMerger;
import kirill.subtitlemerger.logic.core.SubtitleParser;
import kirill.subtitlemerger.logic.core.entities.Subtitles;
import kirill.subtitlemerger.logic.utils.FileValidator;
import kirill.subtitlemerger.logic.work_with_files.SubtitleInjector;
import kirill.subtitlemerger.logic.work_with_files.entities.*;
import kirill.subtitlemerger.logic.work_with_files.ffmpeg.FfmpegException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

@CommonsLog
public class ContentPaneController extends AbstractController {
    @FXML
    private Pane contentPane;

    @FXML
    private Pane chosenDirectoryPane;

    @FXML
    private TextField chosenDirectoryField;

    @FXML
    private ActionResultLabels generalResult;

    @FXML
    private Label selectedLabel;

    @FXML
    private CheckBox hideUnavailableCheckbox;

    @FXML
    private Pane autoSelectButtonWrapper;

    @FXML
    private Button autoSelectButton;

    @FXML
    private Pane loadAllSubtitlesButtonWrapper;

    @FXML
    private Button loadAllSubtitlesButton;

    @FXML
    private Pane goButtonWrapper;

    @FXML
    private Button goButton;

    @FXML
    private TableWithFiles tableWithFiles;

    @FXML
    private Pane addRemoveFilesPane;

    @FXML
    private Button removeSelectedButton;

    private File directory;

    private List<FileInfo> filesInfo;

    private List<TableFileInfo> allTableFilesInfo;

    /*
     * Before performing a one-file operation it is better to clean the result of the previous one-file operation, it
     * will look better. We can't just clear all files' results because it may take a lot of time if there are many
     * files and it's unacceptable for generally fast one-file operations. So we will keep track of the last processed
     * file to clear its result very fast when starting next one-file operation.
     */
    private TableFileInfo lastProcessedFileInfo;

    private VideosTabController videosTabController;

    private Stage stage;

    private GuiContext context;

    public void initialize(VideosTabController videosTabController, Stage stage, GuiContext context) {
        this.videosTabController = videosTabController;
        this.stage = stage;
        this.context = context;

        tableWithFiles.initialize();

        setSelectedLabelText(tableWithFiles.getSelectedCount());
        setActionButtonsVisibility(tableWithFiles.getSelectedCount());
        tableWithFiles.selectedCountProperty().addListener(this::selectedCountChanged);

        removeSelectedButton.disableProperty().bind(tableWithFiles.selectedCountProperty().isEqualTo(0));
    }

    private void setSelectedLabelText(int selected) {
        GuiUtils.getTextDependingOnTheCount(
                selected,
                "1 file selected",
                "%d files selected"
        );
    }

    private void setActionButtonsVisibility(int selected) {
        if (selected == 0) {
            String tooltipText = "no videos are selected for merge";

            autoSelectButton.setDisable(true);
            Tooltip.install(autoSelectButtonWrapper, GuiUtils.generateTooltip(tooltipText));

            loadAllSubtitlesButton.setDisable(true);
            Tooltip.install(loadAllSubtitlesButtonWrapper, GuiUtils.generateTooltip(tooltipText));

            goButton.setDisable(true);
            Tooltip.install(goButtonWrapper, GuiUtils.generateTooltip(tooltipText));
        } else {
            autoSelectButton.setDisable(false);
            Tooltip.install(autoSelectButtonWrapper, null);

            loadAllSubtitlesButton.setDisable(false);
            Tooltip.install(loadAllSubtitlesButtonWrapper, null);

            goButton.setDisable(false);
            Tooltip.install(goButtonWrapper, null);
        }
    }

    private void selectedCountChanged(Observable observable) {
        setSelectedLabelText(tableWithFiles.getSelectedCount());
        setActionButtonsVisibility(tableWithFiles.getSelectedCount());
    }

    @FXML
    private void autoSelectButtonClicked() {
        generalResult.clear();
        lastProcessedFileInfo = null;

        AutoSelectSubtitlesTask backgroundRunner = new AutoSelectSubtitlesTask(
                filesInfo,
                tableWithFiles.getItems(),
                context.getFfmpeg(),
                context.getSettings()
        );

        BackgroundRunnerCallback<AutoSelectSubtitlesTask.Result> callback =
                result -> generalResult.set(AutoSelectSubtitlesTask.generateMultiPartResult(result));

        runInBackground(backgroundRunner, callback);
    }

    @FXML
    private void loadAllSubtitlesClicked() {
        generalResult.clear();
        lastProcessedFileInfo = null;

        LoadFilesAllSubtitlesTask backgroundRunner = new LoadFilesAllSubtitlesTask(
                filesInfo,
                tableWithFiles.getItems(),
                context.getFfmpeg()
        );

        BackgroundRunnerCallback<LoadFilesAllSubtitlesTask.Result> callback =
                result -> generalResult.set(LoadFilesAllSubtitlesTask.generateMultiPartResult(result));

        runInBackground(backgroundRunner, callback);
    }

    @FXML
    private void goButtonClicked() throws FfmpegException {
        GuiFileInfo guiFileInfo = tableWithFiles.getItems().get(0);
        FileInfo fileInfo = GuiUtils.findMatchingFileInfo(guiFileInfo, filesInfo);

        GuiSubtitleStream guiUpperSubtitles = guiFileInfo.getSubtitleStreams().stream()
                .filter(GuiSubtitleStream::isSelectedAsUpper)
                .findFirst().orElseThrow(IllegalStateException::new);
        SubtitleOption upperSubtitles = SubtitleOption.getById(
                guiUpperSubtitles.getId(),
                fileInfo.getSubtitleStreams()
        );

        GuiSubtitleStream guiLowerSubtitles = guiFileInfo.getSubtitleStreams().stream()
                .filter(GuiSubtitleStream::isSelectedAsLower)
                .findFirst().orElseThrow(IllegalStateException::new);
        SubtitleOption lowerSubtitles = SubtitleOption.getById(
                guiLowerSubtitles.getId(),
                fileInfo.getSubtitleStreams()
        );

        SubtitleInjector.mergeAndInjectSubtitlesToFile(
                upperSubtitles.getSubtitles(),
                lowerSubtitles.getSubtitles(),
                context.getSettings().isMarkMergedStreamAsDefault(),
                fileInfo,
                context.getFfmpeg()
        );
    }

    private void sortByChanged(Observable observable) {
        generalResult.clear();
        clearLastProcessedResult();

        RadioMenuItem radioMenuItem = (RadioMenuItem) sortByGroup.getSelectedToggle();

        try {
            switch (radioMenuItem.getText()) {
                case SORT_BY_NAME_TEXT:
                    context.getSettings().saveSortBy(GuiSettings.SortBy.NAME.toString());
                    break;
                case SORT_BY_MODIFICATION_TIME_TEXT:
                    context.getSettings().saveSortBy(GuiSettings.SortBy.MODIFICATION_TIME.toString());
                    break;
                case SORT_BY_SIZE_TEXT:
                    context.getSettings().saveSortBy(GuiSettings.SortBy.SIZE.toString());
                    break;
                default:
                    throw new IllegalStateException();
            }
        } catch (GuiSettings.ConfigException e) {
            log.error("failed to save sort by, should not happen: " + ExceptionUtils.getStackTrace(e));
        }

        SortOrShowHideUnavailableTask backgroundRunner = new SortOrShowHideUnavailableTask(
                allGuiFilesInfo,
                hideUnavailableCheckbox.isSelected(),
                context.getSettings().getSortBy(),
                context.getSettings().getSortDirection()
        );

        BackgroundRunnerCallback<List<GuiFileInfo>> callback =
                result -> updateTableContent(result, tableWithFiles.getMode(), false);

        runInBackground(backgroundRunner, callback);
    }

    private void clearLastProcessedResult() {
        if (lastProcessedFileInfo != null) {
            lastProcessedFileInfo.clearResult();
        }
    }

    private void updateTableContent(
            List<GuiFileInfo> guiFilesToShowInfo,
            TableWithFiles.Mode mode,
            boolean clearTableCache
    ) {
        setSelected((int) guiFilesToShowInfo.stream().filter(GuiFileInfo::isSelected).count());

        allAvailableCount.setValue(
                (int) guiFilesToShowInfo.stream()
                        .filter(filesInfo -> StringUtils.isBlank(filesInfo.getUnavailabilityReason()))
                        .count()
        );

        if (clearTableCache) {
            tableWithFiles.clearCache();
        }
        tableWithFiles.setMode(mode);
        tableWithFiles.setItems(FXCollections.observableArrayList(guiFilesToShowInfo));
        setAllSelected(allAvailableCount.get() > 0 && getSelected() == allAvailableCount.get());
    }

    private void sortDirectionChanged(Observable observable) {
        generalResult.clear();
        clearLastProcessedResult();

        RadioMenuItem radioMenuItem = (RadioMenuItem) sortDirectionGroup.getSelectedToggle();

        try {
            switch (radioMenuItem.getText()) {
                case SORT_ASCENDING_TEXT:
                    context.getSettings().saveSortDirection(GuiSettings.SortDirection.ASCENDING.toString());
                    break;
                case SORT_DESCENDING_TEXT:
                    context.getSettings().saveSortDirection(GuiSettings.SortDirection.DESCENDING.toString());
                    break;
                default:
                    throw new IllegalStateException();
            }
        } catch (GuiSettings.ConfigException e) {
            log.error("failed to save sort direction, should not happen: " + ExceptionUtils.getStackTrace(e));
        }

        SortOrShowHideUnavailableTask backgroundRunner = new SortOrShowHideUnavailableTask(
                allGuiFilesInfo,
                hideUnavailableCheckbox.isSelected(),
                context.getSettings().getSortBy(),
                context.getSettings().getSortDirection()
        );

        BackgroundRunnerCallback<List<GuiFileInfo>> callback =
                result -> updateTableContent(result, tableWithFiles.getMode(), false);

        runInBackground(backgroundRunner, callback);
    }

    public void show() {
        contentPane.setVisible(true);
    }

    public void hide() {
        contentPane.setVisible(false);
    }

    void handleChosenFiles(List<File> files) {
        //todo check if > 10000

        try {
            context.getSettings().saveLastDirectoryWithVideos(files.get(0).getParent());
        } catch (GuiSettings.ConfigException e) {
            log.error("failed to save last directory with videos, shouldn't happen: " + getStackTrace(e));
        }

        context.setWorkWithVideosInProgress(true);

        directory = null;

        LoadSeparateFilesTask backgroundRunner = new LoadSeparateFilesTask(
                files,
                context.getSettings().getSortBy(),
                context.getSettings().getSortDirection(),
                context
        );

        BackgroundRunnerCallback<LoadSeparateFilesTask.Result> callback = result -> {
            filesInfo = result.getFilesInfo();
            allGuiFilesInfo = result.getAllGuiFilesInfo();
            updateTableContent(result.getGuiFilesToShowInfo(), TableWithFiles.Mode.SEPARATE_FILES, true);
            hideUnavailableCheckbox.setSelected(result.isHideUnavailable());

            chosenDirectoryPane.setVisible(false);
            chosenDirectoryPane.setManaged(false);
            addRemoveFilesPane.setVisible(true);
            addRemoveFilesPane.setManaged(true);
        };

        runInBackground(backgroundRunner, callback);
    }

    void handleChosenDirectory(File directory) {
        //todo check if > 10000

        try {
            context.getSettings().saveLastDirectoryWithVideos(directory.getAbsolutePath());
        } catch (GuiSettings.ConfigException e) {
            log.error("failed to save last directory with videos, that shouldn't happen: " + getStackTrace(e));
        }

        context.setWorkWithVideosInProgress(true);

        this.directory = directory;

        LoadDirectoryFilesTask backgroundRunner = new LoadDirectoryFilesTask(
                this.directory,
                context.getSettings().getSortBy(),
                context.getSettings().getSortDirection(),
                context
        );

        BackgroundRunnerCallback<LoadDirectoryFilesTask.Result> callback = result -> {
            filesInfo = result.getFilesInfo();
            allGuiFilesInfo = result.getAllGuiFilesInfo();
            updateTableContent(result.getGuiFilesToShowInfo(), TableWithFiles.Mode.DIRECTORY, true);
            hideUnavailableCheckbox.setSelected(result.isHideUnavailable());
            chosenDirectoryField.setText(directory.getAbsolutePath());

            chosenDirectoryPane.setVisible(true);
            chosenDirectoryPane.setManaged(true);
            addRemoveFilesPane.setVisible(false);
            addRemoveFilesPane.setManaged(false);
        };

        runInBackground(backgroundRunner, callback);
    }

    private static List<File> getFiles(Stage stage, GuiSettings settings) {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("choose videos");
        fileChooser.setInitialDirectory(settings.getLastDirectoryWithVideos());
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("mkv files (*.mkv)", "*.mkv")
        );

        return fileChooser.showOpenMultipleDialog(stage);
    }

    @FXML
    private void backToSelectionClicked() {
        setSelected(0);

        tableWithFiles.clearCache();
        tableWithFiles.setMode(null);
        tableWithFiles.setItems(FXCollections.emptyObservableList());
        generalResult.clear();
        lastProcessedFileInfo = null;
        allAvailableCount.setValue(0);
        setAllSelected(false);
        context.setWorkWithVideosInProgress(false);

        System.gc();

        videosTabController.setActivePane(VideosTabController.ActivePane.CHOICE);
    }

    @FXML
    private void refreshButtonClicked() {
        //todo check if > 10000

        lastProcessedFileInfo = null;
        generalResult.clear();

        LoadDirectoryFilesTask backgroundRunner = new LoadDirectoryFilesTask(
                this.directory,
                context.getSettings().getSortBy(),
                context.getSettings().getSortDirection(),
                context
        );

        BackgroundRunnerCallback<LoadDirectoryFilesTask.Result> callback = result -> {
            filesInfo = result.getFilesInfo();
            allGuiFilesInfo = result.getAllGuiFilesInfo();
            updateTableContent(result.getGuiFilesToShowInfo(), tableWithFiles.getMode(), true);
            hideUnavailableCheckbox.setSelected(result.isHideUnavailable());

            /* See the huge comment in the hideUnavailableClicked() method. */
            tableWithFiles.scrollTo(0);
        };

        runInBackground(backgroundRunner, callback);
    }

    @FXML
    private void hideUnavailableClicked() {
        generalResult.clear();
        clearLastProcessedResult();

        SortOrShowHideUnavailableTask backgroundRunner = new SortOrShowHideUnavailableTask(
                allGuiFilesInfo,
                hideUnavailableCheckbox.isSelected(),
                context.getSettings().getSortBy(),
                context.getSettings().getSortDirection()
        );

        BackgroundRunnerCallback<List<GuiFileInfo>> callback = result -> {
            updateTableContent(result, tableWithFiles.getMode(), false);

            /*
             * There is a strange bug with TableView - when the list is shrunk in size (because for example
             * "hide unavailable" checkbox is checked but it can also happen when refresh is clicked I suppose) and both
             * big list and shrunk list have vertical scrollbars table isn't shrunk unless you move the scrollbar.
             * I've tried many workaround but this one seems the best so far - just show the beginning of the table.
             * I couldn't find a bug with precise description but these ones fit quite well -
             * https://bugs.openjdk.java.net/browse/JDK-8095384, https://bugs.openjdk.java.net/browse/JDK-8087833.
             */
            tableWithFiles.scrollTo(0);
        };

        runInBackground(backgroundRunner, callback);
    }

    @FXML
    private void removeButtonClicked() {
        generalResult.clear();
        clearLastProcessedResult();

        RemoveFilesTask backgroundRunner = new RemoveFilesTask(
                filesInfo,
                allGuiFilesInfo,
                tableWithFiles.getItems()
        );

        BackgroundRunnerCallback<RemoveFilesTask.Result> callback = result -> {
            filesInfo = result.getFilesInfo();
            allGuiFilesInfo = result.getAllGuiFilesInfo();
            updateTableContent(result.getGuiFilesToShowInfo(), tableWithFiles.getMode(), false);

            if (result.getRemovedCount() == 0) {
                log.error("nothing has been removed, that shouldn't happen");
                throw new IllegalStateException();
            } else if (result.getRemovedCount() == 1) {
                generalResult.setOnlySuccess("File has been removed from the list successfully");
            } else {
                generalResult.setOnlySuccess(result.getRemovedCount() + " files have been removed from the list successfully");
            }
        };

        runInBackground(backgroundRunner, callback);
    }

    @FXML
    private void addButtonClicked() {
        generalResult.clear();
        clearLastProcessedResult();

        List<File> filesToAdd = getFiles(stage, context.getSettings());
        if (CollectionUtils.isEmpty(filesToAdd)) {
            return;
        }

        try {
            context.getSettings().saveLastDirectoryWithVideos(filesToAdd.get(0).getParent());
        } catch (GuiSettings.ConfigException e) {
            log.error("failed to save last directory with videos, that shouldn't happen: " + getStackTrace(e));
        }

        AddFilesTask backgroundRunner = new AddFilesTask(
                filesInfo,
                filesToAdd,
                allGuiFilesInfo,
                hideUnavailableCheckbox.isSelected(),
                context.getSettings().getSortBy(),
                context.getSettings().getSortDirection(),
                context
        );

        BackgroundRunnerCallback<AddFilesTask.Result> callback = result -> {
            filesInfo = result.getFilesInfo();
            allGuiFilesInfo = result.getAllGuiFilesInfo();
            updateTableContent(result.getGuiFilesToShowInfo(), tableWithFiles.getMode(), false);
            generalResult.set(AddFilesTask.generateMultiPartResult(result));
        };

        runInBackground(backgroundRunner, callback);
    }

    public static boolean isExtra(FfmpegSubtitleStream subtitleStream, GuiSettings guiSettings) {
        return subtitleStream.getLanguage() != guiSettings.getUpperLanguage()
                && subtitleStream.getLanguage() != guiSettings.getLowerLanguage();
    }

    public static int getSubtitleCanBeHiddenCount(FileInfo fileInfo, GuiSettings guiSettings) {
        if (CollectionUtils.isEmpty(fileInfo.getFfmpegSubtitleStreams())) {
            return 0;
        }

        boolean hasSubtitlesWithUpperLanguage = fileInfo.getFfmpegSubtitleStreams().stream()
                .filter(stream -> stream.getUnavailabilityReason() == null)
                .anyMatch(stream -> stream.getLanguage() == guiSettings.getUpperLanguage());
        boolean hasSubtitlesWithLowerLanguage = fileInfo.getFfmpegSubtitleStreams().stream()
                .filter(stream -> stream.getUnavailabilityReason() == null)
                .anyMatch(stream -> stream.getLanguage() == guiSettings.getLowerLanguage());
        int subtitlesWithOtherLanguage = (int) fileInfo.getFfmpegSubtitleStreams().stream()
                .filter(stream -> stream.getUnavailabilityReason() == null)
                .filter(stream -> stream.getLanguage() != guiSettings.getUpperLanguage())
                .filter(stream -> stream.getLanguage() != guiSettings.getLowerLanguage())
                .count();

        if (!hasSubtitlesWithUpperLanguage || !hasSubtitlesWithLowerLanguage) {
            return 0;
        }

        return subtitlesWithOtherLanguage;
    }

    private void addExternalSubtitleFileClicked(GuiFileInfo guiFileInfo, Runnable onFinish) {
        generalResult.clear();
        clearLastProcessedResult();
        guiFileInfo.clearResult();
        lastProcessedFileInfo = guiFileInfo;

        File file = getFile(guiFileInfo, stage, context.getSettings()).orElse(null);
        if (file == null) {
            return;
        }

        FileInfo fileInfo = GuiUtils.findMatchingFileInfo(guiFileInfo, filesInfo);

        int externalSubtitleStreamIndex;
        File otherFile;
        if (CollectionUtils.isEmpty(fileInfo.getSubtitlesFromFiles())) {
            otherFile = null;
            externalSubtitleStreamIndex = 0;
        } else if (fileInfo.getSubtitlesFromFiles().size() == 1) {
            otherFile = fileInfo.getSubtitlesFromFiles().get(0).getFile();
            externalSubtitleStreamIndex = 1;
        } else {
            log.error("unexpected amount of subtitle streams: " + fileInfo.getSubtitlesFromFiles().size());
            throw new IllegalStateException();
        }

        BackgroundRunner<ExternalSubtitleFileInfo> backgroundRunner = runnerManager -> {
            runnerManager.updateMessage("processing file " + file.getAbsolutePath() + "...");
            return getInputFileInfo(file, otherFile).orElseThrow(IllegalStateException::new);
        };

        BackgroundRunnerCallback<ExternalSubtitleFileInfo> callback = result -> {
            try {
                if (result.getParent() != null) {
                    context.getSettings().saveLastDirectoryWithExternalSubtitles(file.getParent());
                }
            } catch (GuiSettings.ConfigException e) {
                log.error(
                        "failed to save last directory , file " + file.getAbsolutePath() + ": "
                                + ExceptionUtils.getStackTrace(e)
                );
            }

            if (result.getFile() != null && result.isDuplicate) {
                guiFileInfo.setResultOnlyError("This file has already been added");
            } else if (result.getIncorrectFileReason() != null) {
                guiFileInfo.setResultOnlyError(getErrorText(result.getIncorrectFileReason()));
            } else {
                FileWithSubtitles externalSubtitleStream = new FileWithSubtitles(
                        file,
                        result.getRawData(),
                        result.getSubtitles(),
                        StandardCharsets.UTF_8
                );

                fileInfo.getSubtitlesFromFiles().add(externalSubtitleStream);

                guiFileInfo.setExternalSubtitleStream(
                        externalSubtitleStreamIndex,
                        externalSubtitleStream.getId(),
                        file.getName(),
                        (int) file.length(),
                        result.getSubtitles() != null
                );

                if (result.getSubtitles() != null) {
                    guiFileInfo.setResultOnlySuccess("Subtitle file has been added to the list successfully");
                } else {
                    guiFileInfo.setResultOnlyWarn(
                            "File was added but it has an incorrect subtitle format, you can try and change the encoding pressing the preview button"
                    );
                }
            }

            onFinish.run();
        };

        runInBackground(backgroundRunner, callback);
    }

    private Optional<File> getFile(GuiFileInfo fileInfo, Stage stage, GuiSettings settings) {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("Please choose the file with the subtitles");

        File initialDirectory = settings.getLastDirectoryWithExternalSubtitles();
        if (initialDirectory == null) {
            File directoryWithFile = new File(fileInfo.getFullPath()).getParentFile();
            if (directoryWithFile != null && directoryWithFile.exists()) {
                initialDirectory = directoryWithFile;
            }
        }

        fileChooser.setInitialDirectory(initialDirectory);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("subrip files (*.srt)", "*.srt")
        );

        return Optional.ofNullable(fileChooser.showOpenDialog(stage));
    }

    private static Optional<ExternalSubtitleFileInfo> getInputFileInfo(File file, File otherSubtitleFile) {
        FileValidator.InputFileInfo validatorFileInfo = FileValidator.getInputFileInfo(
                file.getAbsolutePath(),
                Collections.singletonList("srt"),
                false,
                GuiConstants.INPUT_SUBTITLE_FILE_LIMIT_MEGABYTES * 1024 * 1024,
                true
        ).orElseThrow(IllegalStateException::new);

        if (validatorFileInfo.getIncorrectFileReason() != null) {
            return Optional.of(
                    new ExternalSubtitleFileInfo(
                            validatorFileInfo.getFile(),
                            validatorFileInfo.getParent(),
                            Objects.equals(file, otherSubtitleFile),
                            validatorFileInfo.getContent(),
                            null,
                            validatorFileInfo.getIncorrectFileReason()
                    )
            );
        }

        Subtitles subtitles;
        try {
            subtitles = SubtitleParser.fromSubRipText(
                    new String(validatorFileInfo.getContent(), StandardCharsets.UTF_8),
                    null
            );
        } catch (SubtitleParser.IncorrectFormatException e) {
            subtitles = null;
        }

        return Optional.of(
                new ExternalSubtitleFileInfo(
                        validatorFileInfo.getFile(),
                        validatorFileInfo.getParent(),
                        Objects.equals(file, otherSubtitleFile),
                        validatorFileInfo.getContent(),
                        subtitles,
                        null
                )
        );
    }

    private static String getErrorText(FileValidator.IncorrectInputFileReason reason) {
        switch (reason) {
            case PATH_IS_TOO_LONG:
                return "File path is too long";
            case INVALID_PATH:
                return "File path is invalid";
            case IS_A_DIRECTORY:
                return "Is a directory, not a file";
            case FILE_DOES_NOT_EXIST:
                return "File doesn't exist";
            case FAILED_TO_GET_PARENT_DIRECTORY:
                return "Failed to get parent directory for the file";
            case EXTENSION_IS_NOT_VALID:
                return "File has an incorrect extension";
            case FILE_IS_EMPTY:
                return "File is empty";
            case FILE_IS_TOO_BIG:
                return "File is too big (>" + GuiConstants.INPUT_SUBTITLE_FILE_LIMIT_MEGABYTES + " megabytes)";
            case FAILED_TO_READ_CONTENT:
                return "Failed to read the file";
            default:
                throw new IllegalStateException();
        }
    }

    private void removeExternalSubtitleFileClicked(String streamId, GuiFileInfo guiFileInfo, Runnable onFinish) {
        generalResult.clear();
        clearLastProcessedResult();
        guiFileInfo.clearResult();
        lastProcessedFileInfo = guiFileInfo;

        FileInfo fileInfo = GuiUtils.findMatchingFileInfo(guiFileInfo, filesInfo);

        fileInfo.getSubtitleStreams().removeIf(stream -> Objects.equals(stream.getId(), streamId));
        guiFileInfo.unsetExternalSubtitleStream(streamId);

        onFinish.run();

        guiFileInfo.setResultOnlySuccess("Subtitle file has been removed from the list successfully");
    }

    private void showFfmpegStreamPreview(String streamId, GuiFileInfo guiFileInfo) {
        generalResult.clear();
        clearLastProcessedResult();
        guiFileInfo.clearResult();
        lastProcessedFileInfo = guiFileInfo;

        Stage dialogStage = new Stage();

        FileInfo fileInfo = GuiUtils.findMatchingFileInfo(guiFileInfo, filesInfo);

        FfmpegSubtitleStream stream = FfmpegSubtitleStream.getById(streamId, fileInfo.getFfmpegSubtitleStreams());

        NodeAndController<Pane, SubtitlePreviewController> nodeAndController = GuiUtils.loadNodeAndController(
                "/gui/application_specific/subtitlePreview.fxml"
        );

        nodeAndController.getController().initializeSimple(
                stream.getSubtitles(),
                getStreamTitleForPreview(fileInfo, stream),
                dialogStage
        );

        dialogStage.setTitle("Subtitle preview");
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(stage);
        dialogStage.setResizable(false);

        Scene scene = new Scene(nodeAndController.getNode());
        scene.getStylesheets().add("/gui/style.css");
        dialogStage.setScene(scene);

        dialogStage.showAndWait();
    }

    private static String getStreamTitleForPreview(FileInfo fileInfo, FfmpegSubtitleStream stream) {
        String videoFilePath = GuiUtils.getShortenedStringIfNecessary(
                fileInfo.getFile().getName(),
                0,
                128
        );

        String language = GuiUtils.languageToString(stream.getLanguage()).toUpperCase();

        String streamTitle = "";
        if (!StringUtils.isBlank(stream.getTitle())) {
            streamTitle += String.format(
                    " (%s)",
                    GuiUtils.getShortenedStringIfNecessary(
                            stream.getTitle(),
                            20,
                            0
                    )
            );
        }

        return videoFilePath + ", " + language + streamTitle;
    }

    private static String getStreamTitleForPreview(FileWithSubtitles fileWithSubtitles) {
        return GuiUtils.getShortenedStringIfNecessary(
                fileWithSubtitles.getFile().getAbsolutePath(),
                0,
                128
        );
    }

    private void showExternalFilePreview(String streamId, GuiFileInfo guiFileInfo) {
        generalResult.clear();
        clearLastProcessedResult();
        guiFileInfo.clearResult();
        lastProcessedFileInfo = guiFileInfo;

        FileInfo fileInfo = GuiUtils.findMatchingFileInfo(guiFileInfo, filesInfo);
        FileWithSubtitles subtitleStream = SubtitleOption.getById(streamId, fileInfo.getExternalSubtitleStreams());
        GuiExternalSubtitleStream guiSubtitleStream = GuiSubtitleStream.getById(streamId, guiFileInfo.getExternalSubtitleStreams());

        Stage dialogStage = new Stage();

        NodeAndController<Pane, SubtitlePreviewController> nodeAndController = GuiUtils.loadNodeAndController(
                "/gui/application_specific/subtitlePreview.fxml"
        );

        nodeAndController.getController().initializeWithEncoding(
                subtitleStream.getRawData(),
                subtitleStream.getEncoding(),
                getStreamTitleForPreview(fileInfo, subtitleStream),
                dialogStage
        );

        dialogStage.setTitle("Subtitle preview");
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(stage);
        dialogStage.setResizable(false);

        Scene scene = new Scene(nodeAndController.getNode());
        scene.getStylesheets().add("/gui/style.css");
        dialogStage.setScene(scene);

        dialogStage.showAndWait();

        SubtitlePreviewController.UserSelection userSelection = nodeAndController.getController().getUserSelection();

        if (Objects.equals(subtitleStream.getEncoding(), userSelection.getEncoding())) {
            return;
        }

        subtitleStream.setEncoding(userSelection.getEncoding());
        subtitleStream.setSubtitles(userSelection.getSubtitles());
        guiSubtitleStream.setCorrectFormat(userSelection.getSubtitles() != null);
    }

    private void showMergedPreview(GuiFileInfo guiFileInfo) {
        generalResult.clear();
        clearLastProcessedResult();
        guiFileInfo.clearResult();
        lastProcessedFileInfo = guiFileInfo;

        FileInfo fileInfo = GuiUtils.findMatchingFileInfo(guiFileInfo, filesInfo);

        GuiSubtitleStream guiUpperStream = guiFileInfo.getSubtitleStreams().stream()
                .filter(GuiSubtitleStream::isSelectedAsUpper)
                .findFirst().orElseThrow(IllegalStateException::new);
        SubtitleOption upperStream = SubtitleOption.getById(guiUpperStream.getId(), fileInfo.getSubtitleStreams());
        Charset upperStreamEncoding;
        if (upperStream instanceof FfmpegSubtitleStream) {
            upperStreamEncoding = StandardCharsets.UTF_8;
        } else if (upperStream instanceof FileWithSubtitles) {
            upperStreamEncoding = ((FileWithSubtitles) upperStream).getEncoding();
        } else {
            throw new IllegalStateException();
        }

        GuiSubtitleStream guiLowerStream = guiFileInfo.getSubtitleStreams().stream()
                .filter(GuiSubtitleStream::isSelectedAsLower)
                .findFirst().orElseThrow(IllegalStateException::new);
        SubtitleOption lowerStream = SubtitleOption.getById(guiLowerStream.getId(), fileInfo.getSubtitleStreams());
        Charset lowerStreamEncoding;
        if (lowerStream instanceof FfmpegSubtitleStream) {
            lowerStreamEncoding = StandardCharsets.UTF_8;
        } else if (lowerStream instanceof FileWithSubtitles) {
            lowerStreamEncoding = ((FileWithSubtitles) lowerStream).getEncoding();
        } else {
            throw new IllegalStateException();
        }

        BackgroundRunner<MergedSubtitleInfo> backgroundRunner = runnerManager -> {
            if (fileInfo.getMergedSubtitleInfo() != null) {
                boolean matches = true;

                if (!Objects.equals(fileInfo.getMergedSubtitleInfo().getUpperStreamId(), upperStream.getId())) {
                    matches = false;
                }

                if (!Objects.equals(fileInfo.getMergedSubtitleInfo().getUpperStreamEncoding(), upperStreamEncoding)) {
                    matches = false;
                }

                if (!Objects.equals(fileInfo.getMergedSubtitleInfo().getLowerStreamId(), lowerStream.getId())) {
                    matches = false;
                }

                if (!Objects.equals(fileInfo.getMergedSubtitleInfo().getLowerStreamEncoding(), lowerStreamEncoding)) {
                    matches = false;
                }

                if (matches) {
                    return fileInfo.getMergedSubtitleInfo();
                }
            }

            runnerManager.updateMessage("merging subtitles...");

            Subtitles merged = SubtitleMerger.mergeSubtitles(
                    upperStream.getSubtitles(),
                    lowerStream.getSubtitles()
            );

            return new MergedSubtitleInfo(
                    merged,
                    upperStream.getId(),
                    upperStreamEncoding,
                    lowerStream.getId(),
                    lowerStreamEncoding
            );
        };

        BackgroundRunnerCallback<MergedSubtitleInfo> callback = mergedSubtitleInfo -> {
            fileInfo.setMergedSubtitleInfo(mergedSubtitleInfo);

            Stage dialogStage = new Stage();

            NodeAndController<Pane, SubtitlePreviewController> nodeAndController = GuiUtils.loadNodeAndController(
                    "/gui/application_specific/subtitlePreview.fxml"
            );

            nodeAndController.getController().initializeMerged(
                    mergedSubtitleInfo.getSubtitles(),
                    getStreamTitleForPreview(fileInfo, upperStream),
                    getStreamTitleForPreview(fileInfo, lowerStream),
                    dialogStage
            );

            dialogStage.setTitle("Subtitle preview");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setResizable(false);

            Scene scene = new Scene(nodeAndController.getNode());
            scene.getStylesheets().add("/gui/style.css");
            dialogStage.setScene(scene);

            dialogStage.showAndWait();
        };

        runInBackground(backgroundRunner, callback);
    }

    private void loadAllFileSubtitleSizes(GuiFileInfo guiFileInfo) {
        generalResult.clear();
        clearLastProcessedResult();
        guiFileInfo.clearResult();
        lastProcessedFileInfo = guiFileInfo;

        LoadSingleFileAllSubtitlesTask backgroundRunner = new LoadSingleFileAllSubtitlesTask(
                GuiUtils.findMatchingFileInfo(guiFileInfo, filesInfo),
                guiFileInfo,
                context.getFfmpeg()
        );

        BackgroundRunnerCallback<LoadFilesAllSubtitlesTask.Result> callback =
                result -> guiFileInfo.setResult(LoadFilesAllSubtitlesTask.generateMultiPartResult(result));

        runInBackground(backgroundRunner, callback);
    }

    private void loadSingleFileSubtitleSize(GuiFileInfo guiFileInfo, String streamId) {
        generalResult.clear();
        clearLastProcessedResult();
        guiFileInfo.clearResult();
        lastProcessedFileInfo = guiFileInfo;

        LoadSingleSubtitleTask backgroundRunner = new LoadSingleSubtitleTask(
                streamId,
                GuiUtils.findMatchingFileInfo(guiFileInfo, filesInfo),
                guiFileInfo,
                context.getFfmpeg()
        );

        BackgroundRunnerCallback<LoadSingleSubtitleTask.Result> callback =
                result -> generalResult.set(LoadSingleSubtitleTask.generateMultiPartResult(result));

        runInBackground(backgroundRunner, callback);
    }

    @AllArgsConstructor
    @Getter
    private static class ExternalSubtitleFileInfo {
        private File file;

        private File parent;

        private boolean isDuplicate;

        private byte[] rawData;

        private Subtitles subtitles;

        private FileValidator.IncorrectInputFileReason incorrectFileReason;
    }
}
