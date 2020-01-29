package kirill.subtitlemerger.gui.tabs.videos.regular_content;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import kirill.subtitlemerger.gui.GuiContext;
import kirill.subtitlemerger.gui.GuiSettings;
import kirill.subtitlemerger.gui.GuiUtils;
import kirill.subtitlemerger.gui.tabs.videos.regular_content.background_tasks.*;
import kirill.subtitlemerger.gui.tabs.videos.regular_content.background_tasks.load_subtitles.LoadSeveralFilesAllSubtitlesTask;
import kirill.subtitlemerger.gui.tabs.videos.regular_content.background_tasks.load_subtitles.LoadSingleFileAllSubtitlesTask;
import kirill.subtitlemerger.gui.tabs.videos.regular_content.background_tasks.load_subtitles.LoadSingleSubtitleTask;
import kirill.subtitlemerger.gui.tabs.videos.regular_content.background_tasks.load_subtitles.LoadSubtitlesTask;
import kirill.subtitlemerger.gui.tabs.videos.regular_content.table_with_files.GuiFileInfo;
import kirill.subtitlemerger.gui.tabs.videos.regular_content.table_with_files.GuiSubtitleStream;
import kirill.subtitlemerger.gui.tabs.videos.regular_content.table_with_files.TableWithFiles;
import kirill.subtitlemerger.logic.work_with_files.SubtitleInjector;
import kirill.subtitlemerger.logic.work_with_files.entities.FileInfo;
import kirill.subtitlemerger.logic.work_with_files.entities.SubtitleStream;
import kirill.subtitlemerger.logic.work_with_files.ffmpeg.FfmpegException;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

@CommonsLog
public class RegularContentController {
    private static final String SORT_BY_NAME_TEXT = "By _Name";

    private static final String SORT_BY_MODIFICATION_TIME_TEXT = "By _Modification Time";

    private static final String SORT_BY_SIZE_TEXT = "By _Size";

    private static final String SORT_ASCENDING_TEXT = "_Ascending";

    private static final String SORT_DESCENDING_TEXT = "_Descending";

    private Stage stage;

    private GuiContext guiContext;

    @FXML
    private Pane pane;

    @FXML
    private Pane choicePane;

    @FXML
    private Pane progressPane;

    @FXML
    private ProgressIndicator progressIndicator;

    @FXML
    private Label progressLabel;

    @FXML
    private Pane resultPane;

    @FXML
    private Pane chosenDirectoryPane;

    @FXML
    private TextField chosenDirectoryField;

    @FXML
    private Label resultLabelSuccess;

    @FXML
    private Label resultLabelWarn;

    @FXML
    private Label resultLabelError;

    @FXML
    private Label selectedForMergeLabel;

    @FXML
    private CheckBox hideUnavailableCheckbox;

    @FXML
    private Pane autoSelectButtonWrapper;

    @FXML
    private Button autoSelectButton;

    @FXML
    private Pane getAllSizesButtonWrapper;

    @FXML
    private Button getAllSizesButton;

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

    private BooleanProperty cancelTaskPaneVisible = new SimpleBooleanProperty(false);

    private ToggleGroup sortByGroup;

    private ToggleGroup sortDirectionGroup;

    private CancellableBackgroundTask<?> currentCancellableTask;

    private File directory;

    private List<FileInfo> filesInfo;

    private List<GuiFileInfo> allGuiFilesInfo;

    public boolean getCancelTaskPaneVisible() {
        return cancelTaskPaneVisible.get();
    }

    public BooleanProperty cancelTaskPaneVisibleProperty() {
        return cancelTaskPaneVisible;
    }

    public void setCancelTaskPaneVisible(boolean cancelTaskPaneVisible) {
        this.cancelTaskPaneVisible.set(cancelTaskPaneVisible);
    }

    public void initialize(Stage stage, GuiContext guiContext) {
        this.stage = stage;
        this.guiContext = guiContext;
        saveDefaultSortSettingsIfNotSet(guiContext.getSettings());
        bindSelectedForMergeText();
        this.sortByGroup = new ToggleGroup();
        this.sortDirectionGroup = new ToggleGroup();
        this.tableWithFiles.initialize(
                this::loadAllFileSubtitleSizes,
                this::loadSingleFileSubtitleSize
        );
        this.tableWithFiles.setContextMenu(
                generateContextMenu(
                        this.sortByGroup,
                        this.sortDirectionGroup,
                        guiContext.getSettings()
                )
        );
        this.tableWithFiles.selectedProperty().addListener(this::selectedCountChangeListener);

        this.sortByGroup.selectedToggleProperty().addListener(this::sortByChanged);
        this.sortDirectionGroup.selectedToggleProperty().addListener(this::sortDirectionChanged);
        this.removeSelectedButton.disableProperty().bind(
                Bindings.isEmpty(tableWithFiles.getSelectionModel().getSelectedIndices())
        );
    }

    private static void saveDefaultSortSettingsIfNotSet(GuiSettings settings) {
        try {
            if (settings.getSortBy() == null) {
                settings.saveSortBy(GuiSettings.SortBy.MODIFICATION_TIME.toString());
            }

            if (settings.getSortDirection() == null) {
                settings.saveSortDirection(GuiSettings.SortDirection.ASCENDING.toString());
            }
        } catch (GuiSettings.ConfigException e) {
            log.error("failed to save sort parameters, should not happen: " + ExceptionUtils.getStackTrace(e));
        }
    }

    private void bindSelectedForMergeText() {
        String suffix = "selected for merge";

        StringBinding oneItemBinding = Bindings.createStringBinding(
                () -> "1 video " + suffix, tableWithFiles.selectedProperty()
        );
        StringBinding zeroOrMultipleItemsBinding = Bindings.createStringBinding(
                () -> tableWithFiles.getSelected() + " videos " + suffix, tableWithFiles.selectedProperty()
        );

        selectedForMergeLabel.textProperty().bind(
                Bindings.when(tableWithFiles.selectedProperty().isEqualTo(1))
                        .then(oneItemBinding)
                        .otherwise(zeroOrMultipleItemsBinding)
        );
    }

    private void selectedCountChangeListener(Observable observable) {
        setActionButtonsVisibility();
    }

    private void setActionButtonsVisibility() {
        if (tableWithFiles.getSelected() == 0) {
            String tooltipText = "no videos are selected for merge";

            autoSelectButton.setDisable(true);
            Tooltip.install(autoSelectButtonWrapper, GuiUtils.generateTooltip(tooltipText));

            getAllSizesButton.setDisable(true);
            Tooltip.install(getAllSizesButtonWrapper, GuiUtils.generateTooltip(tooltipText));

            goButton.setDisable(true);
            Tooltip.install(goButtonWrapper, GuiUtils.generateTooltip(tooltipText));
        } else {
            autoSelectButton.setDisable(false);
            Tooltip.install(autoSelectButtonWrapper, null);

            getAllSizesButton.setDisable(false);
            Tooltip.install(getAllSizesButtonWrapper, null);

            goButton.setDisable(false);
            Tooltip.install(goButtonWrapper, null);
        }
    }

    @FXML
    private void autoSelectButtonClicked() {
        CancellableBackgroundTask<?> task = new AutoSelectSubtitlesTask(
                filesInfo,
                tableWithFiles.getItems(),
                guiContext.getFfmpeg(),
                guiContext.getSettings(),
                cancelTaskPaneVisible
        );
        task.setOnFinished(() -> {
            //todo implement
            // showResult(task, false);
            stopProgress();
        });

        currentCancellableTask = task;

        showProgress(task, true);
        GuiUtils.startTask(task);
    }

    @FXML
    private void getAllSizesButtonClicked() {
        runLoadSubtitlesTask(
                new LoadSeveralFilesAllSubtitlesTask(
                        filesInfo,
                        tableWithFiles.getItems(),
                        guiContext.getFfmpeg(),
                        cancelTaskPaneVisible
                )
        );
    }

    private void runLoadSubtitlesTask(LoadSubtitlesTask task) {
        task.setOnFinished(() -> {
            showResult(task);
            stopProgress();
        });

        currentCancellableTask = task;

        showProgress(task, true);
        GuiUtils.startTask(task);
    }

    @FXML
    private void goButtonClicked() throws FfmpegException {
        GuiFileInfo guiFileInfo = tableWithFiles.getItems().get(0);
        FileInfo fileInfo = findMatchingFileInfo(guiFileInfo, filesInfo);

        GuiSubtitleStream guiUpperSubtitles = guiFileInfo.getSubtitleStreams().stream()
                .filter(GuiSubtitleStream::isSelectedAsUpper)
                .findFirst().orElseThrow(IllegalStateException::new);
        SubtitleStream upperSubtitles = SubtitleStream.getByFfmpegIndex(
                guiUpperSubtitles.getFfmpegIndex(),
                fileInfo.getSubtitleStreams()
        );

        GuiSubtitleStream guiLowerSubtitles = guiFileInfo.getSubtitleStreams().stream()
                .filter(GuiSubtitleStream::isSelectedAsLower)
                .findFirst().orElseThrow(IllegalStateException::new);
        SubtitleStream lowerSubtitles = SubtitleStream.getByFfmpegIndex(
                guiLowerSubtitles.getFfmpegIndex(),
                fileInfo.getSubtitleStreams()
        );

        SubtitleInjector.mergeAndInjectSubtitlesToFile(
                upperSubtitles.getSubtitles(),
                lowerSubtitles.getSubtitles(),
                guiContext.getSettings().isMarkMergedStreamAsDefault(),
                fileInfo,
                guiContext.getFfmpeg()
        );
    }

    private void loadAllFileSubtitleSizes(GuiFileInfo guiFileInfo) {
        runLoadSubtitlesTask(
                new LoadSingleFileAllSubtitlesTask(
                        findMatchingFileInfo(guiFileInfo, filesInfo),
                        guiFileInfo,
                        guiContext.getFfmpeg(),
                        cancelTaskPaneVisible
                )
        );
    }

    private void loadSingleFileSubtitleSize(GuiFileInfo guiFileInfo, int ffmpegIndex) {
        runLoadSubtitlesTask(
                new LoadSingleSubtitleTask(
                        ffmpegIndex,
                        findMatchingFileInfo(guiFileInfo, filesInfo),
                        guiFileInfo,
                        guiContext.getFfmpeg(),
                        cancelTaskPaneVisible
                )
        );
    }

    //todo test
    //todo make reusable probably
    private void showResult(LoadSubtitlesTask task) {
        if (task.getAllSubtitleCount() == 0) {
            if (tableWithFiles.getSelected() == 1) {
                setResult("Nothing to load for the selected file", null, null);
            } else {
                setResult("Nothing to load for the selected files", null, null);
            }
        } else if (task.getProcessedCount() == 0) {
            setResult(null, "Haven't load anything because of the cancellation", null);
        } else if (task.getLoadedSuccessfullyCount() == task.getAllSubtitleCount()) {
            if (task.getAllSubtitleCount() == 1) {
                setResult("Subtitle size has been loaded successfully", null, null);
            } else {
                setResult("All " + task.getAllSubtitleCount() + " subtitle sizes have been loaded successfully", null, null);
            }
        } else if (task.getLoadedBeforeCount() == task.getAllSubtitleCount()) {
            if (task.getAllSubtitleCount() == 1) {
                setResult("Subtitle size has already been loaded successfully", null, null);
            } else {
                setResult("All " + task.getAllSubtitleCount() + " subtitle sizes have already been loaded successfully", null, null);
            }
        } else if (task.getFailedToLoadCount() == task.getAllSubtitleCount()) {
            if (task.getAllSubtitleCount() == 1) {
                setResult(null, null, "Failed to load subtitle size");
            } else {
                setResult(null, null, "Failed to load all " + task.getAllSubtitleCount() + " subtitle sizes");
            }
        } else {
            String success = "";
            if (task.getLoadedSuccessfullyCount() != 0) {
                success += task.getLoadedSuccessfullyCount() + "/" + task.getAllSubtitleCount();
                success += " loaded successfully";
            }

            if (task.getLoadedBeforeCount() != 0) {
                if (!StringUtils.isBlank(success)) {
                    success += ", ";
                }

                success += task.getLoadedBeforeCount() + "/" + task.getAllSubtitleCount();
                success += " loaded before";
            }

            String warn = "";
            if (task.getProcessedCount() != task.getAllSubtitleCount()) {
                warn += (task.getAllSubtitleCount() - task.getProcessedCount()) + "/" + task.getAllSubtitleCount();
                warn += " cancelled";
            }

            String error = "";
            if (task.getFailedToLoadCount() != 0) {
                error += "failed to load " + task.getFailedToLoadCount() + "/" + task.getAllSubtitleCount();
                error += " subtitles";
            }

            setResult(success, warn, error);
        }
    }

    private void sortByChanged(Observable observable) {
        clearResult();

        RadioMenuItem radioMenuItem = (RadioMenuItem) sortByGroup.getSelectedToggle();

        try {
            switch (radioMenuItem.getText()) {
                case SORT_BY_NAME_TEXT:
                    guiContext.getSettings().saveSortBy(GuiSettings.SortBy.NAME.toString());
                    break;
                case SORT_BY_MODIFICATION_TIME_TEXT:
                    guiContext.getSettings().saveSortBy(GuiSettings.SortBy.MODIFICATION_TIME.toString());
                    break;
                case SORT_BY_SIZE_TEXT:
                    guiContext.getSettings().saveSortBy(GuiSettings.SortBy.SIZE.toString());
                    break;
                default:
                    throw new IllegalStateException();
            }
        } catch (GuiSettings.ConfigException e) {
            log.error("failed to save sort by, should not happen: " + ExceptionUtils.getStackTrace(e));
        }

        SortOrShowHideUnavailableTask task = new SortOrShowHideUnavailableTask(
                allGuiFilesInfo,
                hideUnavailableCheckbox.isSelected(),
                guiContext.getSettings().getSortBy(),
                guiContext.getSettings().getSortDirection()
        );

        task.setOnSucceeded(e -> {
            updateTableContent(task.getValue());
            stopProgress();
        });

        showProgress(task, false);
        GuiUtils.startTask(task);
    }

    private void clearResult() {
        resultLabelSuccess.setText("");
        resultLabelWarn.setText("");
        resultLabelError.setText("");
    }

    private void updateTableContent(List<GuiFileInfo> guiFilesToShowInfo) {
        long oldSelected = tableWithFiles.getSelected();
        tableWithFiles.setSelected(guiFilesToShowInfo.stream().filter(GuiFileInfo::isSelected).count());
        if (oldSelected == tableWithFiles.getSelected()) {
            setActionButtonsVisibility();
        }

        int allAvailableCount = (int) guiFilesToShowInfo.stream()
                .filter(filesInfo -> StringUtils.isBlank(filesInfo.getUnavailabilityReason()))
                .count();

        tableWithFiles.setItems(FXCollections.observableArrayList(guiFilesToShowInfo));
        tableWithFiles.setAllAvailableCount(allAvailableCount);
        tableWithFiles.setAllSelected(tableWithFiles.getSelected() == allAvailableCount);
    }

    private void stopProgress() {
        progressPane.setVisible(false);
        resultPane.setDisable(false);
    }

    private void showProgress(Task<?> task, boolean canCancel) {
        setCancelTaskPaneVisible(canCancel);
        choicePane.setVisible(false);
        progressPane.setVisible(true);
        resultPane.setVisible(true);
        resultPane.setDisable(true);

        progressIndicator.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(task.messageProperty());
    }

    private void sortDirectionChanged(Observable observable) {
        clearResult();

        RadioMenuItem radioMenuItem = (RadioMenuItem) sortDirectionGroup.getSelectedToggle();

        try {
            switch (radioMenuItem.getText()) {
                case SORT_ASCENDING_TEXT:
                    guiContext.getSettings().saveSortDirection(GuiSettings.SortDirection.ASCENDING.toString());
                    break;
                case SORT_DESCENDING_TEXT:
                    guiContext.getSettings().saveSortDirection(GuiSettings.SortDirection.DESCENDING.toString());
                    break;
                default:
                    throw new IllegalStateException();
            }
        } catch (GuiSettings.ConfigException e) {
            log.error("failed to save sort direction, should not happen: " + ExceptionUtils.getStackTrace(e));
        }

        SortOrShowHideUnavailableTask task = new SortOrShowHideUnavailableTask(
                allGuiFilesInfo,
                hideUnavailableCheckbox.isSelected(),
                guiContext.getSettings().getSortBy(),
                guiContext.getSettings().getSortDirection()
        );

        task.setOnSucceeded(e -> {
            updateTableContent(task.getValue());
            stopProgress();
        });

        showProgress(task, false);
        GuiUtils.startTask(task);
    }

    private static ContextMenu generateContextMenu(
            ToggleGroup sortByGroup,
            ToggleGroup sortDirectionGroup,
            GuiSettings settings
    ) {
        ContextMenu result = new ContextMenu();

        Menu menu = new Menu("_Sort files");

        GuiSettings.SortBy sortBy = settings.getSortBy();
        GuiSettings.SortDirection sortDirection = settings.getSortDirection();

        RadioMenuItem byName = new RadioMenuItem(SORT_BY_NAME_TEXT);
        byName.setToggleGroup(sortByGroup);
        if (sortBy == GuiSettings.SortBy.NAME) {
            byName.setSelected(true);
        }

        RadioMenuItem byModificationTime = new RadioMenuItem(SORT_BY_MODIFICATION_TIME_TEXT);
        byModificationTime.setToggleGroup(sortByGroup);
        if (sortBy == GuiSettings.SortBy.MODIFICATION_TIME) {
            byModificationTime.setSelected(true);
        }

        RadioMenuItem bySize = new RadioMenuItem(SORT_BY_SIZE_TEXT);
        bySize.setToggleGroup(sortByGroup);
        if (sortBy == GuiSettings.SortBy.SIZE) {
            bySize.setSelected(true);
        }

        RadioMenuItem ascending = new RadioMenuItem(SORT_ASCENDING_TEXT);
        ascending.setToggleGroup(sortDirectionGroup);
        if (sortDirection == GuiSettings.SortDirection.ASCENDING) {
            ascending.setSelected(true);
        }

        RadioMenuItem descending = new RadioMenuItem(SORT_DESCENDING_TEXT);
        descending.setToggleGroup(sortDirectionGroup);
        if (sortDirection == GuiSettings.SortDirection.DESCENDING) {
            descending.setSelected(true);
        }

        menu.getItems().addAll(
                byName,
                byModificationTime,
                bySize,
                new SeparatorMenuItem(),
                ascending,
                descending
        );

        result.getItems().add(menu);

        return result;
    }

    @FXML
    private void cancelTaskClicked() {
        if (currentCancellableTask == null) {
            log.error("task is null, that shouldn't happen");
            return;
        }

        currentCancellableTask.cancel();
    }

    public void show() {
        pane.setVisible(true);
    }

    public void hide() {
        pane.setVisible(false);
    }

    void setResult(String success, String warn, String error) {
        if (!StringUtils.isBlank(success) && (!StringUtils.isBlank(warn) || !StringUtils.isBlank(error))) {
            resultLabelSuccess.setText(success + ", ");
        } else if (!StringUtils.isBlank(success)) {
            resultLabelSuccess.setText(success);
        } else {
            resultLabelSuccess.setText("");
        }

        if (!StringUtils.isBlank(warn) && !StringUtils.isBlank(error)) {
            resultLabelWarn.setText(warn + ", ");
        } else if (!StringUtils.isBlank(warn)) {
            resultLabelWarn.setText(warn);
        } else {
            resultLabelWarn.setText("");
        }

        if (!StringUtils.isBlank(error)) {
            resultLabelError.setText(error);
        } else {
            resultLabelError.setText("");
        }
    }

    @FXML
    private void separateFilesButtonClicked() {
        clearResult();

        List<File> files = getFiles(stage, guiContext.getSettings());
        if (CollectionUtils.isEmpty(files)) {
            return;
        }

        guiContext.setWorkWithVideosInProgress(true);

        try {
            guiContext.getSettings().saveLastDirectoryWithVideos(files.get(0).getParent());
        } catch (GuiSettings.ConfigException e) {
            log.error("failed to save last directory with videos, shouldn't happen: " + getStackTrace(e));
        }

        directory = null;

        LoadSeparateFilesTask task = new LoadSeparateFilesTask(
                files,
                guiContext.getSettings().getSortBy(),
                guiContext.getSettings().getSortDirection(),
                guiContext
        );

        task.setOnSucceeded(e -> {
            filesInfo = task.getValue().getFilesInfo();
            allGuiFilesInfo = task.getValue().getAllGuiFilesInfo();
            updateTableContent(task.getValue().getGuiFilesToShowInfo());
            hideUnavailableCheckbox.setSelected(task.getValue().isHideUnavailable());

            chosenDirectoryPane.setVisible(false);
            chosenDirectoryPane.setManaged(false);
            addRemoveFilesPane.setVisible(true);
            addRemoveFilesPane.setManaged(true);

            stopProgress();
        });

        showProgress(task, false);
        GuiUtils.startTask(task);
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
    private void directoryButtonClicked() {
        clearResult();

        File directory = getDirectory(stage, guiContext.getSettings()).orElse(null);
        if (directory == null) {
            return;
        }

        guiContext.setWorkWithVideosInProgress(true);

        try {
            guiContext.getSettings().saveLastDirectoryWithVideos(directory.getAbsolutePath());
        } catch (GuiSettings.ConfigException e) {
            log.error("failed to save last directory with videos, that shouldn't happen: " + getStackTrace(e));
        }

        this.directory = directory;

        LoadDirectoryFilesTask task = new LoadDirectoryFilesTask(
                this.directory,
                guiContext.getSettings().getSortBy(),
                guiContext.getSettings().getSortDirection(),
                guiContext
        );

        task.setOnSucceeded(e -> {
            filesInfo = task.getValue().getFilesInfo();
            allGuiFilesInfo = task.getValue().getAllGuiFilesInfo();
            updateTableContent(task.getValue().getGuiFilesToShowInfo());
            hideUnavailableCheckbox.setSelected(task.getValue().isHideUnavailable());
            chosenDirectoryField.setText(directory.getAbsolutePath());

            chosenDirectoryPane.setVisible(true);
            chosenDirectoryPane.setManaged(true);
            addRemoveFilesPane.setVisible(false);
            addRemoveFilesPane.setManaged(false);

            stopProgress();
        });

        showProgress(task, false);
        GuiUtils.startTask(task);
    }

    private static Optional<File> getDirectory(Stage stage, GuiSettings settings) {
        DirectoryChooser directoryChooser = new DirectoryChooser();

        directoryChooser.setTitle("choose directory with videos");
        directoryChooser.setInitialDirectory(settings.getLastDirectoryWithVideos());

        return Optional.ofNullable(directoryChooser.showDialog(stage));
    }

    @FXML
    private void backToSelectionClicked() {
        /* Just in case. See the huge comment in the hideUnavailableClicked() method. */
        tableWithFiles.setSelected(0);
        tableWithFiles.setItems(FXCollections.emptyObservableList());
        tableWithFiles.setAllAvailableCount(0);
        tableWithFiles.setAllSelected(false);
        guiContext.setWorkWithVideosInProgress(false);

        choicePane.setVisible(true);
        resultPane.setVisible(false);
    }

    @FXML
    private void refreshButtonClicked() {
        clearResult();

        LoadDirectoryFilesTask task = new LoadDirectoryFilesTask(
                this.directory,
                guiContext.getSettings().getSortBy(),
                guiContext.getSettings().getSortDirection(),
                guiContext
        );

        task.setOnSucceeded(e -> {
            filesInfo = task.getValue().getFilesInfo();
            allGuiFilesInfo = task.getValue().getAllGuiFilesInfo();
            updateTableContent(task.getValue().getGuiFilesToShowInfo());
            hideUnavailableCheckbox.setSelected(task.getValue().isHideUnavailable());

            /* See the huge comment in the hideUnavailableClicked() method. */
            tableWithFiles.scrollTo(0);

            stopProgress();
        });

        showProgress(task, false);
        GuiUtils.startTask(task);
    }

    @FXML
    private void hideUnavailableClicked() {
        clearResult();

        SortOrShowHideUnavailableTask task = new SortOrShowHideUnavailableTask(
                allGuiFilesInfo,
                hideUnavailableCheckbox.isSelected(),
                guiContext.getSettings().getSortBy(),
                guiContext.getSettings().getSortDirection()
        );

        task.setOnSucceeded(e -> {
            updateTableContent(task.getValue());

            /*
             * There is a strange bug with TableView - when the list is shrunk in size (because for example
             * "hide unavailable" checkbox is checked but it can also happen when refresh is clicked I suppose) and both
             * big list and shrunk list have vertical scrollbars table isn't shrunk unless you move the scrollbar.
             * I've tried many workaround but this one seems the best so far - just show the beginning of the table.
             * I couldn't find a bug with precise description but these ones fit quite well -
             * https://bugs.openjdk.java.net/browse/JDK-8095384, https://bugs.openjdk.java.net/browse/JDK-8087833.
             */
            tableWithFiles.scrollTo(0);

            stopProgress();
        });

        showProgress(task, false);
        GuiUtils.startTask(task);
    }

    @FXML
    private void removeButtonClicked() {
        clearResult();

        List<Integer> indices = tableWithFiles.getSelectionModel().getSelectedIndices();
        if (CollectionUtils.isEmpty(indices)) {
            return;
        }

        RemoveFilesTask task = new RemoveFilesTask(
                filesInfo,
                allGuiFilesInfo,
                tableWithFiles.getItems(),
                indices
        );

        task.setOnSucceeded(e -> {
            filesInfo = task.getValue().getFilesInfo();
            allGuiFilesInfo = task.getValue().getAllGuiFilesInfo();
            updateTableContent(task.getValue().getGuiFilesToShowInfo());

            stopProgress();
        });

        showProgress(task, false);
        GuiUtils.startTask(task);
    }

    @FXML
    private void addButtonClicked() {
        clearResult();

        List<File> filesToAdd = getFiles(stage, guiContext.getSettings());
        if (CollectionUtils.isEmpty(filesToAdd)) {
            return;
        }

        try {
            guiContext.getSettings().saveLastDirectoryWithVideos(filesToAdd.get(0).getParent());
        } catch (GuiSettings.ConfigException e) {
            log.error("failed to save last directory with videos, that shouldn't happen: " + getStackTrace(e));
        }

        AddFilesTask task = new AddFilesTask(
                filesInfo,
                filesToAdd,
                allGuiFilesInfo,
                hideUnavailableCheckbox.isSelected(),
                guiContext.getSettings().getSortBy(),
                guiContext.getSettings().getSortDirection(),
                guiContext
        );

        task.setOnSucceeded(e -> {
            filesInfo = task.getValue().getFilesInfo();
            allGuiFilesInfo = task.getValue().getAllGuiFilesInfo();
            updateTableContent(task.getValue().getGuiFilesToShowInfo());

            stopProgress();
        });

        showProgress(task, false);
        GuiUtils.startTask(task);
    }

    public static GuiFileInfo findMatchingGuiFileInfo(FileInfo fileInfo, List<GuiFileInfo> guiFilesInfo) {
        return guiFilesInfo.stream()
                .filter(guiFileInfo -> Objects.equals(guiFileInfo.getFullPath(), fileInfo.getFile().getAbsolutePath()))
                .findFirst().orElseThrow(IllegalStateException::new);
    }

    public static FileInfo findMatchingFileInfo(GuiFileInfo guiFileInfo, List<FileInfo> filesInfo) {
        return filesInfo.stream()
                .filter(fileInfo -> Objects.equals(fileInfo.getFile().getAbsolutePath(), guiFileInfo.getFullPath()))
                .findFirst().orElseThrow(IllegalStateException::new);
    }

    public static GuiSubtitleStream findMatchingGuiStream(int ffmpegIndex, List<GuiSubtitleStream> guiStreams) {
        return guiStreams.stream()
                .filter(stream -> stream.getFfmpegIndex() == ffmpegIndex)
                .findFirst().orElseThrow(IllegalStateException::new);
    }

    public static boolean haveSubtitlesToLoad(FileInfo fileInfo) {
        if (CollectionUtils.isEmpty(fileInfo.getSubtitleStreams())) {
            return false;
        }

        return fileInfo.getSubtitleStreams().stream()
                .anyMatch(stream -> stream.getSubtitles() == null);
    }

    public static boolean isExtra(SubtitleStream subtitleStream, GuiSettings guiSettings) {
        return subtitleStream.getLanguage() != guiSettings.getUpperLanguage()
                && subtitleStream.getLanguage() != guiSettings.getLowerLanguage();
    }

    public static int getSubtitleCanBeHiddenCount(FileInfo fileInfo, GuiSettings guiSettings) {
        if (CollectionUtils.isEmpty(fileInfo.getSubtitleStreams())) {
            return 0;
        }

        boolean hasSubtitlesWithUpperLanguage = fileInfo.getSubtitleStreams().stream()
                .filter(stream -> stream.getUnavailabilityReason() == null)
                .anyMatch(stream -> stream.getLanguage() == guiSettings.getUpperLanguage());
        boolean hasSubtitlesWithLowerLanguage = fileInfo.getSubtitleStreams().stream()
                .filter(stream -> stream.getUnavailabilityReason() == null)
                .anyMatch(stream -> stream.getLanguage() == guiSettings.getLowerLanguage());
        int subtitlesWithOtherLanguage = (int) fileInfo.getSubtitleStreams().stream()
                .filter(stream -> stream.getUnavailabilityReason() == null)
                .filter(stream -> stream.getLanguage() != guiSettings.getUpperLanguage())
                .filter(stream -> stream.getLanguage() != guiSettings.getLowerLanguage())
                .count();

        if (!hasSubtitlesWithUpperLanguage || !hasSubtitlesWithLowerLanguage) {
            return 0;
        }

        return subtitlesWithOtherLanguage;
    }
}
