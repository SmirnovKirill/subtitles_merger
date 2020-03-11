package kirill.subtitlemerger.gui.application_specific.videos_tab.table_with_files;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import kirill.subtitlemerger.gui.GuiConstants;
import kirill.subtitlemerger.gui.utils.GuiHelperMethods;
import kirill.subtitlemerger.gui.utils.custom_controls.ActionResultLabels;
import kirill.subtitlemerger.gui.utils.entities.ActionResult;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.*;

import static kirill.subtitlemerger.gui.GuiConstants.PANE_ERROR_CLASS;
import static kirill.subtitlemerger.gui.GuiConstants.PANE_UNAVAILABLE_CLASS;
import static kirill.subtitlemerger.gui.application_specific.videos_tab.table_with_files.TableSubtitleOption.UNKNOWN_SIZE;

@CommonsLog
public class TableWithFiles extends TableView<TableFileInfo> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormat.forPattern("dd.MM.YYYY HH:mm");

    private static final int CELL_PADDING = 4;

    private static final int SIZE_AND_PREVIEW_PANE_WIDTH = 95;

    private static final int SELECT_OPTION_PANE_WIDTH = 110;

    private final Map<String, Map<CellType, Pane>> cellCache;

    private ToggleGroup sortByGroup;

    private ToggleGroup sortDirectionGroup;

    private ObjectProperty<AllSelectedHandler> allSelectedHandler;

    private ObjectProperty<SortByChangeHandler> sortByChangeHandler;

    private ObjectProperty<SortDirectionChangeHandler> sortDirectionChangeHandler;

    private ObjectProperty<RemoveSubtitleOptionHandler> removeSubtitleOptionHandler;

    private ObjectProperty<SingleSubtitleLoader> singleSubtitleLoader;

    private ObjectProperty<SubtitleOptionPreviewHandler> subtitleOptionPreviewHandler;

    private ObjectProperty<AddFileWithSubtitlesHandler> addFileWithSubtitlesHandler;

    private ObjectProperty<AllFileSubtitleLoader> allFileSubtitleLoader;

    private ObjectProperty<MergedSubtitlePreviewHandler> mergedSubtitlePreviewHandler;

    private int allSelectableCount;

    private ReadOnlyIntegerWrapper allSelectedCount;

    @Getter
    private int selectedAvailableCount;

    @Getter
    private int selectedUnavailableCount;

    private CheckBox allSelectedCheckBox;

    @Getter
    private Mode mode;

    public TableWithFiles() {
        cellCache = new HashMap<>();

        sortByGroup = new ToggleGroup();
        sortDirectionGroup = new ToggleGroup();

        allSelectedHandler = new SimpleObjectProperty<>();
        sortByChangeHandler = new SimpleObjectProperty<>();
        sortDirectionChangeHandler = new SimpleObjectProperty<>();
        removeSubtitleOptionHandler = new SimpleObjectProperty<>();
        singleSubtitleLoader = new SimpleObjectProperty<>();
        subtitleOptionPreviewHandler = new SimpleObjectProperty<>();
        addFileWithSubtitlesHandler = new SimpleObjectProperty<>();
        allFileSubtitleLoader = new SimpleObjectProperty<>();
        mergedSubtitlePreviewHandler = new SimpleObjectProperty<>();

        allSelectedCount = new ReadOnlyIntegerWrapper();

        allSelectedCheckBox = generateAlSelectedCheckBox();
        addColumns(allSelectedCheckBox);
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        setSelectionModel(null);
        setPlaceholder(new Label("there are no files to display"));

        setContextMenu(generateContextMenu(sortByGroup, sortDirectionGroup));
        sortByGroup.selectedToggleProperty().addListener(this::sortByChanged);
        sortDirectionGroup.selectedToggleProperty().addListener(this::sortDirectionChanged);
    }

    private CheckBox generateAlSelectedCheckBox() {
        CheckBox result = new CheckBox();

        result.setOnAction(event -> {
            AllSelectedHandler handler = allSelectedHandler.get();
            if (handler == null) {
                return;
            }

            handler.handle(result.isSelected());
        });

        return result;
    }

    private void addColumns(CheckBox allSelectedCheckBox) {
        TableColumn<TableFileInfo, ?> selectedColumn = new TableColumn<>();
        selectedColumn.setCellFactory(
                column -> new TableWithFilesCell<>(CellType.SELECTED, this::generateSelectedCellPane)
        );
        selectedColumn.setGraphic(allSelectedCheckBox);
        selectedColumn.setMaxWidth(26);
        selectedColumn.setMinWidth(26);
        selectedColumn.setReorderable(false);
        selectedColumn.setResizable(false);
        selectedColumn.setSortable(false);

        TableColumn<TableFileInfo, ?> fileDescriptionColumn = new TableColumn<>("file");
        fileDescriptionColumn.setCellFactory(
                column -> new TableWithFilesCell<>(CellType.FILE_DESCRIPTION, this::generateFileDescriptionCellPane)
        );
        fileDescriptionColumn.setMinWidth(200);
        fileDescriptionColumn.setReorderable(false);
        fileDescriptionColumn.setSortable(false);

        TableColumn<TableFileInfo, ?> subtitleColumn = new TableColumn<>("subtitles");
        subtitleColumn.setCellFactory(
                column -> new TableWithFilesCell<>(CellType.SUBTITLES, this::generateSubtitleCellPane)
        );
        subtitleColumn.setMinWidth(300);
        subtitleColumn.setReorderable(false);
        subtitleColumn.setSortable(false);

        getColumns().addAll(Arrays.asList(selectedColumn, fileDescriptionColumn, subtitleColumn));
    }

    private Pane generateSelectedCellPane(TableFileInfo fileInfo) {
        HBox result = new HBox();

        result.setPadding(new Insets(CELL_PADDING, 0, CELL_PADDING, 0));
        result.setAlignment(Pos.TOP_CENTER);

        /*
         * We should stop here if in the directory mode, checkbox isn't needed because there is no point in selecting an
         * unavailable file. On the contrary, in the files mode it's possible to select the unavailable file to remove
         * it. Because of the ability to remove the file the behaviour is different.
         */
        if (fileInfo.getUnavailabilityReason() != null && mode == Mode.DIRECTORY) {
            return result;
        }

        CheckBox selectedCheckBox = new CheckBox();

        selectedCheckBox.selectedProperty().bindBidirectional(fileInfo.selectedProperty());
        selectedCheckBox.setOnAction(event -> handlerFileSelectionChange(selectedCheckBox.isSelected(), fileInfo));

        result.getChildren().add(selectedCheckBox);

        return result;
    }

    private void handlerFileSelectionChange(boolean selected, TableFileInfo fileInfo) {
        int addValue = selected ? 1 : -1;

        if (StringUtils.isBlank(fileInfo.getUnavailabilityReason())) {
            selectedAvailableCount += addValue;
        } else {
            selectedUnavailableCount += addValue;
        }

        /*
         * It's very important that this line goes after the modification of the previous counters
         * (selectedAvailableCount and selectedUnavailableCount) because this property will have subscribers and they
         * need updated counter values there.
         */
        allSelectedCount.setValue(getAllSelectedCount() + addValue);

        allSelectedCheckBox.setSelected(getAllSelectedCount() > 0 && getAllSelectedCount() == allSelectableCount);
    }

    private Pane generateFileDescriptionCellPane(TableFileInfo fileInfo) {
        VBox result = new VBox();

        result.setPadding(new Insets(CELL_PADDING, CELL_PADDING + 1, CELL_PADDING, CELL_PADDING));
        result.setSpacing(10);

        Label pathLabel = new Label(fileInfo.getFilePath());
        pathLabel.getStyleClass().add("path-label");

        Pane sizeAndLastModifiedPane = generateSizeAndLastModifiedPane(fileInfo);

        result.getChildren().addAll(pathLabel, sizeAndLastModifiedPane);

        return result;
    }

    private static Pane generateSizeAndLastModifiedPane(TableFileInfo fileInfo) {
        GridPane result = new GridPane();

        result.setHgap(30);
        result.setGridLinesVisible(GuiConstants.DEBUG);

        Label sizeTitle = new Label("size");
        Label lastModifiedTitle = new Label("last modified");

        Label size = new Label(GuiHelperMethods.getFileSizeTextual(fileInfo.getSize()));
        Label lastModified = new Label(FORMATTER.print(fileInfo.getLastModified()));

        result.addRow(0, sizeTitle, size);
        result.addRow(1, lastModifiedTitle, lastModified);

        GridPane.setMargin(sizeTitle, new Insets(0, 0, 3, 0));
        GridPane.setMargin(size, new Insets(0, 0, 3, 0));

        return result;
    }

    private Pane generateSubtitleCellPane(TableFileInfo fileInfo) {
        if (fileInfo.getUnavailabilityReason() != null) {
            return generateSubtitleUnavailableCellPane(fileInfo);
        }

        VBox result = new VBox();

        result.setPadding(new Insets(CELL_PADDING, CELL_PADDING, CELL_PADDING, CELL_PADDING + 1));
        result.setSpacing(2);

        for (TableSubtitleOption subtitleOption : fileInfo.getSubtitleOptions()) {
            result.getChildren().addAll(
                    generateSubtitleOptionPane(
                            subtitleOption,
                            fileInfo,
                            removeSubtitleOptionHandler,
                            singleSubtitleLoader,
                            subtitleOptionPreviewHandler
                    )
            );
        }

        result.getChildren().addAll(
                GuiHelperMethods.createFixedHeightSpacer(1),
                generateRowWithActionsPane(
                        fileInfo,
                        addFileWithSubtitlesHandler,
                        allFileSubtitleLoader,
                        mergedSubtitlePreviewHandler
                ),
                GuiHelperMethods.createFixedHeightSpacer(6),
                generateActionResultLabels(fileInfo)
        );

        return result;
    }

    private static Pane generateSubtitleUnavailableCellPane(TableFileInfo fileInfo) {
        HBox result = new HBox();

        result.setAlignment(Pos.CENTER_LEFT);
        result.setPadding(new Insets(CELL_PADDING, CELL_PADDING, CELL_PADDING, CELL_PADDING + 1));

        result.getChildren().add(new Label(fileInfo.getUnavailabilityReason()));

        return result;
    }

    private static Pane generateSubtitleOptionPane(
            TableSubtitleOption subtitleOption,
            TableFileInfo fileInfo,
            ObjectProperty<RemoveSubtitleOptionHandler> removeSubtitleOptionHandler,
            ObjectProperty<SingleSubtitleLoader> singleSubtitleLoader,
            ObjectProperty<SubtitleOptionPreviewHandler> subtitleOptionPreviewHandler
    ) {
        HBox result = new HBox();

        result.setAlignment(Pos.CENTER_LEFT);

        if (subtitleOption.isHideable()) {
            GuiHelperMethods.bindVisibleAndManaged(result, Bindings.not(fileInfo.someOptionsHiddenProperty()));
        } else if (subtitleOption.isRemovable()) {
            GuiHelperMethods.bindVisibleAndManaged(result, subtitleOption.titleProperty().isNotEmpty());
        }

        result.getChildren().add(generateOptionTitleLabel(subtitleOption));

        //noinspection SimplifyOptionalCallChains
        Button removeButton = generateRemoveButton(
                subtitleOption,
                fileInfo,
                removeSubtitleOptionHandler
        ).orElse(null);
        if (removeButton != null) {
            result.getChildren().addAll(GuiHelperMethods.createFixedWidthSpacer(10), removeButton);
        }

        result.getChildren().addAll(
                GuiHelperMethods.createFixedWidthSpacer(15),
                generateSizeAndPreviewPane(
                        subtitleOption,
                        fileInfo,
                        subtitleOptionPreviewHandler,
                        singleSubtitleLoader
                ),

                GuiHelperMethods.createFixedWidthSpacer(15),
                generateSelectOptionPane(subtitleOption)
        );

        HBox.setHgrow(result.getChildren().get(0), Priority.ALWAYS);

        return result;
    }

    private static Label generateOptionTitleLabel(TableSubtitleOption subtitleOption) {
        Label result = new Label(subtitleOption.getTitle());

        result.setMaxWidth(Double.MAX_VALUE);

        return result;
    }

    private static Optional<Button> generateRemoveButton(
            TableSubtitleOption subtitleOption,
            TableFileInfo fileInfo,
            ObjectProperty<RemoveSubtitleOptionHandler> removeSubtitleOptionHandler
    ) {
        if (!subtitleOption.isRemovable()) {
            return Optional.empty();
        }

        Button result = GuiHelperMethods.createImageButton(
                null,
                "/gui/icons/remove.png",
                8,
                8
        );

        result.setOnAction(event -> {
            RemoveSubtitleOptionHandler handler = removeSubtitleOptionHandler.get();
            if (handler == null) {
                return;
            }

            handler.remove(subtitleOption, fileInfo);
        });

        return Optional.of(result);
    }

    private static Pane generateSizeAndPreviewPane(
            TableSubtitleOption subtitleOption,
            TableFileInfo fileInfo,
            ObjectProperty<SubtitleOptionPreviewHandler> subtitleOptionPreviewHandler,
            ObjectProperty<SingleSubtitleLoader> singleSubtitleLoader
    ) {
        StackPane result = new StackPane();

        GuiHelperMethods.setFixedWidth(result, SIZE_AND_PREVIEW_PANE_WIDTH);

        Pane knownSizeAndPreviewPane = generateKnownSizeAndPreviewPane(
                subtitleOption,
                fileInfo,
                subtitleOptionPreviewHandler
        );
        knownSizeAndPreviewPane.visibleProperty().bind(subtitleOption.sizeProperty().isNotEqualTo(UNKNOWN_SIZE));

        Pane unknownSizePane = generateUnknownSizePane(subtitleOption, fileInfo, singleSubtitleLoader);
        unknownSizePane.visibleProperty().bind(subtitleOption.sizeProperty().isEqualTo(UNKNOWN_SIZE));

        result.getChildren().addAll(knownSizeAndPreviewPane, unknownSizePane);

        return result;
    }

    private static Pane generateKnownSizeAndPreviewPane(
            TableSubtitleOption subtitleOption,
            TableFileInfo fileInfo,
            ObjectProperty<SubtitleOptionPreviewHandler> subtitleOptionPreviewHandler
    ) {
        HBox result = new HBox();

        result.setAlignment(Pos.CENTER_LEFT);
        result.setSpacing(10);

        Label sizeLabel = new Label();
        sizeLabel.setMaxWidth(Double.MAX_VALUE);
        sizeLabel.textProperty().bind(
                Bindings.createStringBinding(() ->
                        "Size: " + GuiHelperMethods.getFileSizeTextual(subtitleOption.getSize()), subtitleOption.sizeProperty()
                )
        );

        result.getChildren().addAll(
                sizeLabel,
                generatePreviewButton(subtitleOption, fileInfo, subtitleOptionPreviewHandler)
        );

        HBox.setHgrow(sizeLabel, Priority.ALWAYS);

        return result;
    }

    private static Button generatePreviewButton(
            TableSubtitleOption subtitleOption,
            TableFileInfo fileInfo,
            ObjectProperty<SubtitleOptionPreviewHandler> subtitleOptionPreviewHandler
    ) {
        Button result = GuiHelperMethods.createImageButton("", "/gui/icons/eye.png", 15, 10);

        result.setOnAction(event -> {
            SubtitleOptionPreviewHandler handler = subtitleOptionPreviewHandler.get();
            if (handler == null) {
                return;
            }

            handler.showPreview(subtitleOption, fileInfo);
        });

        return result;
    }

    private static Pane generateUnknownSizePane(
            TableSubtitleOption subtitleOption,
            TableFileInfo fileInfo,
            ObjectProperty<SingleSubtitleLoader> singleSubtitleLoader
    ) {
        HBox result = new HBox();

        result.setAlignment(Pos.CENTER_LEFT);

        Label sizeLabel = new Label("Size: ? KB");
        sizeLabel.setMaxWidth(Double.MAX_VALUE);

        result.getChildren().addAll(
                sizeLabel,
                GuiHelperMethods.createFixedWidthSpacer(10),
                generateLoadSubtitleLink(subtitleOption, fileInfo, singleSubtitleLoader),
                GuiHelperMethods.createFixedWidthSpacer(5),
                generateFailedToLoadLabel(subtitleOption)
        );

        HBox.setHgrow(sizeLabel, Priority.ALWAYS);

        return result;
    }

    private static Hyperlink generateLoadSubtitleLink(
            TableSubtitleOption subtitleOption,
            TableFileInfo fileInfo,
            ObjectProperty<SingleSubtitleLoader> singleSubtitleLoader
    ) {
        Hyperlink result = new Hyperlink("load");

        result.visibleProperty().bind(subtitleOption.sizeProperty().isEqualTo(UNKNOWN_SIZE));

        result.setOnAction(event -> {
            SingleSubtitleLoader loader = singleSubtitleLoader.get();
            if (loader == null) {
                return;
            }

            loader.loadSubtitles(subtitleOption, fileInfo);
        });

        return result;
    }

    private static Label generateFailedToLoadLabel(TableSubtitleOption subtitleOption) {
        Label result = new Label();

        result.setAlignment(Pos.CENTER);
        result.setGraphic(GuiHelperMethods.createImageView("/gui/icons/error.png", 12, 12));

        result.setTooltip(GuiHelperMethods.generateTooltip(subtitleOption.failedToLoadReasonProperty()));
        GuiHelperMethods.bindVisibleAndManaged(result, subtitleOption.failedToLoadReasonProperty().isNotEmpty());

        return result;
    }

    private static Pane generateSelectOptionPane(TableSubtitleOption subtitleOption) {
        HBox result = new HBox();

        result.setAlignment(Pos.CENTER);
        result.setSpacing(5);

        GuiHelperMethods.setFixedWidth(result, SELECT_OPTION_PANE_WIDTH);

        setSelectOptionPaneTooltip(result, subtitleOption.getUnavailabilityReason());
        subtitleOption.unavailabilityReasonProperty().addListener(
                observable -> setSelectOptionPaneTooltip(result, subtitleOption.getUnavailabilityReason())
        );

        RadioButton upper = new RadioButton("upper");
        upper.selectedProperty().bindBidirectional(subtitleOption.selectedAsUpperProperty());
        upper.disableProperty().bind(subtitleOption.unavailabilityReasonProperty().isNotEmpty());

        RadioButton lower = new RadioButton("lower");
        lower.selectedProperty().bindBidirectional(subtitleOption.selectedAsLowerProperty());
        lower.disableProperty().bind(subtitleOption.unavailabilityReasonProperty().isNotEmpty());

        result.getChildren().addAll(upper, lower);

        return result;
    }

    private static void setSelectOptionPaneTooltip(Pane selectOptionPane, String unavailabilityReason) {
        if (StringUtils.isBlank(unavailabilityReason)) {
            Tooltip.install(selectOptionPane, null);
        } else {
            Tooltip.install(selectOptionPane, GuiHelperMethods.generateTooltip(unavailabilityReason));
        }
    }

    private static Pane generateRowWithActionsPane(
            TableFileInfo fileInfo,
            ObjectProperty<AddFileWithSubtitlesHandler> addFileWithSubtitlesHandler,
            ObjectProperty<AllFileSubtitleLoader> allFileSubtitleLoader,
            ObjectProperty<MergedSubtitlePreviewHandler> mergedSubtitlePreviewHandler
    ) {
        HBox result = new HBox();

        result.setAlignment(Pos.CENTER_LEFT);

        Hyperlink showHideLink = generateShowHideLink(fileInfo).orElse(null);
        if (showHideLink != null) {
            result.getChildren().add(showHideLink);
            result.getChildren().add(GuiHelperMethods.createFixedWidthSpacer(25));
        }

        Region spacer = new Region();

        result.getChildren().addAll(
                generateAddFileButton(fileInfo, addFileWithSubtitlesHandler),
                spacer,
                generateLoadAllSubtitlesPane(fileInfo, allFileSubtitleLoader),
                GuiHelperMethods.createFixedWidthSpacer(15),
                generateMergedPreviewPane(fileInfo, mergedSubtitlePreviewHandler)
        );

        HBox.setHgrow(spacer, Priority.ALWAYS);

        return result;
    }

    private static Optional<Hyperlink> generateShowHideLink(TableFileInfo fileInfo) {
        if (fileInfo.getHideableOptionCount() == 0) {
            return Optional.empty();
        }

        Hyperlink result = new Hyperlink();

        result.textProperty().bind(
                Bindings.when(fileInfo.someOptionsHiddenProperty())
                        .then("show " + fileInfo.getHideableOptionCount() + " hidden")
                        .otherwise("hide extra")
        );
        result.setOnAction(event -> fileInfo.setSomeOptionsHidden(!fileInfo.isSomeOptionsHidden()));

        return Optional.of(result);
    }

    private static Button generateAddFileButton(
            TableFileInfo fileInfo,
            ObjectProperty<AddFileWithSubtitlesHandler> addFileWithSubtitlesHandler
    ) {
        Button result = GuiHelperMethods.createImageButton(
                "Add subtitles",
                "/gui/icons/add.png",
                9,
                9
        );

        int optionCount = fileInfo.getSubtitleOptions().size();
        TableSubtitleOption lastButOneOption = fileInfo.getSubtitleOptions().get(optionCount - 2);
        TableSubtitleOption lastOption = fileInfo.getSubtitleOptions().get(optionCount - 1);

        BooleanBinding canAddMoreFiles = lastButOneOption.titleProperty().isEmpty()
                .or(lastOption.titleProperty().isEmpty());
        result.visibleProperty().bind(canAddMoreFiles);

        result.setOnAction(event -> {
            AddFileWithSubtitlesHandler handler = addFileWithSubtitlesHandler.get();
            if (handler == null) {
                return;
            }

            handler.addFile(fileInfo);
        });

        return result;
    }

    private static Pane generateLoadAllSubtitlesPane(
            TableFileInfo fileInfo,
            ObjectProperty<AllFileSubtitleLoader> allFileSubtitleLoader
    ) {
        HBox result = new HBox();

        result.setAlignment(Pos.CENTER);
        GuiHelperMethods.setFixedWidth(result, SIZE_AND_PREVIEW_PANE_WIDTH);

        Hyperlink loadAllLink = new Hyperlink("load all subtitles");
        loadAllLink.visibleProperty().bind(fileInfo.optionsWithUnknownSizeCountProperty().greaterThan(0));
        loadAllLink.setOnAction(event -> {
            AllFileSubtitleLoader loader = allFileSubtitleLoader.get();
            if (loader == null) {
                return;
            }

            loader.loadSubtitles(fileInfo);
        });

        result.getChildren().add(loadAllLink);

        return result;
    }

    private static Pane generateMergedPreviewPane(
            TableFileInfo fileInfo,
            ObjectProperty<MergedSubtitlePreviewHandler> mergedSubtitlePreviewHandler
    ) {
        HBox result = new HBox();

        result.setAlignment(Pos.CENTER);
        GuiHelperMethods.setFixedWidth(result, SELECT_OPTION_PANE_WIDTH);
        result.visibleProperty().bind(fileInfo.visibleOptionCountProperty().greaterThanOrEqualTo(2));

        Button previewButton = GuiHelperMethods.createImageButton(
                "result",
                "/gui/icons/eye.png",
                15,
                10
        );

        previewButton.setOnAction(event -> {
            MergedSubtitlePreviewHandler handler = mergedSubtitlePreviewHandler.get();
            if (handler == null) {
                return;
            }

            handler.showPreview(fileInfo);
        });

        setMergedPreviewDisabledAndTooltip(previewButton, result, fileInfo);

        InvalidationListener listener = observable ->
                setMergedPreviewDisabledAndTooltip(previewButton, result, fileInfo);

        fileInfo.upperOptionProperty().addListener(listener);
        fileInfo.lowerOptionProperty().addListener(listener);
        for (TableSubtitleOption option : fileInfo.getSubtitleOptions()) {
            if (!option.isSizeAlwaysKnown()) {
                option.sizeProperty().addListener(listener);
            }
        }

        result.getChildren().add(previewButton);

        return result;
    }

    private static void setMergedPreviewDisabledAndTooltip(
            Button previewButton,
            Pane previewPane,
            TableFileInfo fileInfo
    ) {
        TableSubtitleOption upperOption = fileInfo.getUpperOption();
        TableSubtitleOption lowerOption = fileInfo.getLowerOption();

        if (upperOption == null || lowerOption == null) {
            previewButton.setDisable(true);
            Tooltip.install(previewPane, GuiHelperMethods.generateTooltip("Please select subtitles to merge first"));
        } else {
            boolean notLoaded = upperOption.getSize() == UNKNOWN_SIZE
                    || lowerOption.getSize() == UNKNOWN_SIZE;
            if (notLoaded) {
                previewButton.setDisable(true);
                Tooltip.install(previewPane, GuiHelperMethods.generateTooltip("Please load selected subtitles first"));
            } else {
                previewButton.setDisable(false);
                Tooltip.install(previewPane, null);
            }
        }
    }

    private static ActionResultLabels generateActionResultLabels(TableFileInfo fileInfo) {
        ActionResultLabels result = new ActionResultLabels();

        result.setAlignment(Pos.CENTER);
        result.setWrapText(true);

        setActionResultLabels(result, fileInfo);
        fileInfo.actionResultProperty().addListener(observable -> setActionResultLabels(result, fileInfo));

        return result;
    }

    private static void setActionResultLabels(ActionResultLabels actionResultLabels, TableFileInfo fileInfo) {
        if (fileInfo.getActionResult() == null) {
            return;
        }

        actionResultLabels.set(fileInfo.getActionResult());
    }

    private static ContextMenu generateContextMenu(ToggleGroup sortByGroup, ToggleGroup sortDirectionGroup) {
        ContextMenu result = new ContextMenu();

        Menu menu = new Menu("_Sort files");

        RadioMenuItem byName = new RadioMenuItem("By _Name");
        byName.setToggleGroup(sortByGroup);
        byName.setUserData(SortBy.NAME);

        RadioMenuItem byModificationTime = new RadioMenuItem("By _Modification Time");
        byModificationTime.setToggleGroup(sortByGroup);
        byModificationTime.setUserData(SortBy.MODIFICATION_TIME);

        RadioMenuItem bySize = new RadioMenuItem("By _Size");
        bySize.setToggleGroup(sortByGroup);
        bySize.setUserData(SortBy.SIZE);

        RadioMenuItem ascending = new RadioMenuItem("_Ascending");
        ascending.setToggleGroup(sortDirectionGroup);
        ascending.setUserData(SortDirection.ASCENDING);

        RadioMenuItem descending = new RadioMenuItem("_Descending");
        descending.setToggleGroup(sortDirectionGroup);
        descending.setUserData(SortDirection.DESCENDING);

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

    private void sortByChanged(Observable observable, Toggle oldValue, Toggle newValue) {
        if (oldValue == null || newValue == null) {
            return;
        }

        getSortByChangeHandler().handle((SortBy) newValue.getUserData());
    }

    private void sortDirectionChanged(Observable observable, Toggle oldValue, Toggle newValue) {
        if (oldValue == null || newValue == null) {
            return;
        }

        getSortDirectionChangeHandler().handle((SortDirection) newValue.getUserData());
    }

    public AllSelectedHandler getAllSelectedHandler() {
        return allSelectedHandler.get();
    }

    public ObjectProperty<AllSelectedHandler> allSelectedHandlerProperty() {
        return allSelectedHandler;
    }

    public void setAllSelectedHandler(AllSelectedHandler allSelectedHandler) {
        this.allSelectedHandler.set(allSelectedHandler);
    }

    public SortByChangeHandler getSortByChangeHandler() {
        return sortByChangeHandler.get();
    }

    public ObjectProperty<SortByChangeHandler> sortByChangeHandlerProperty() {
        return sortByChangeHandler;
    }

    public void setSortByChangeHandler(SortByChangeHandler sortByChangeHandler) {
        this.sortByChangeHandler.set(sortByChangeHandler);
    }

    public SortDirectionChangeHandler getSortDirectionChangeHandler() {
        return sortDirectionChangeHandler.get();
    }

    public ObjectProperty<SortDirectionChangeHandler> sortDirectionChangeHandlerProperty() {
        return sortDirectionChangeHandler;
    }

    public void setSortDirectionChangeHandler(SortDirectionChangeHandler sortDirectionChangeHandler) {
        this.sortDirectionChangeHandler.set(sortDirectionChangeHandler);
    }

    public RemoveSubtitleOptionHandler getRemoveSubtitleOptionHandler() {
        return removeSubtitleOptionHandler.get();
    }

    public ObjectProperty<RemoveSubtitleOptionHandler> removeSubtitleOptionHandlerProperty() {
        return removeSubtitleOptionHandler;
    }

    public void setRemoveSubtitleOptionHandler(RemoveSubtitleOptionHandler removeSubtitleOptionHandler) {
        this.removeSubtitleOptionHandler.set(removeSubtitleOptionHandler);
    }

    public SingleSubtitleLoader getSingleSubtitleLoader() {
        return singleSubtitleLoader.get();
    }

    public ObjectProperty<SingleSubtitleLoader> singleSubtitleLoaderProperty() {
        return singleSubtitleLoader;
    }

    public void setSingleSubtitleLoader(SingleSubtitleLoader singleSubtitleLoader) {
        this.singleSubtitleLoader.set(singleSubtitleLoader);
    }

    public SubtitleOptionPreviewHandler getSubtitleOptionPreviewHandler() {
        return subtitleOptionPreviewHandler.get();
    }

    public ObjectProperty<SubtitleOptionPreviewHandler> subtitleOptionPreviewHandlerProperty() {
        return subtitleOptionPreviewHandler;
    }

    public void setSubtitleOptionPreviewHandler(SubtitleOptionPreviewHandler subtitleOptionPreviewHandler) {
        this.subtitleOptionPreviewHandler.set(subtitleOptionPreviewHandler);
    }

    public AddFileWithSubtitlesHandler getAddFileWithSubtitlesHandler() {
        return addFileWithSubtitlesHandler.get();
    }

    public ObjectProperty<AddFileWithSubtitlesHandler> addFileWithSubtitlesHandlerProperty() {
        return addFileWithSubtitlesHandler;
    }

    public void setAddFileWithSubtitlesHandler(AddFileWithSubtitlesHandler addFileWithSubtitlesHandler) {
        this.addFileWithSubtitlesHandler.set(addFileWithSubtitlesHandler);
    }

    public AllFileSubtitleLoader getAllFileSubtitleLoader() {
        return allFileSubtitleLoader.get();
    }

    public ObjectProperty<AllFileSubtitleLoader> allFileSubtitleLoaderProperty() {
        return allFileSubtitleLoader;
    }

    public void setAllFileSubtitleLoader(AllFileSubtitleLoader allFileSubtitleLoader) {
        this.allFileSubtitleLoader.set(allFileSubtitleLoader);
    }

    public void setFilesInfo(
            List<TableFileInfo> filesInfo,
            SortBy sortBy,
            SortDirection sortDirection,
            int allSelectableCount,
            int selectedAvailableCount,
            int selectedUnavailableCount,
            Mode mode,
            boolean clearCache
    ) {
        if (clearCache) {
            clearCache();
        }

        updateSortToggles(sortBy, sortDirection);
        this.allSelectableCount = allSelectableCount;
        this.selectedAvailableCount = selectedAvailableCount;
        this.selectedUnavailableCount = selectedUnavailableCount;

        /*
         * It's very important that this line goes after the modification of the previous counters
         * (selectedAvailableCount and selectedUnavailableCount) because this property will have subscribers and they
         * need updated counter values there.
         */
        this.allSelectedCount.setValue(selectedAvailableCount + selectedUnavailableCount);

        allSelectedCheckBox.setSelected(getAllSelectedCount() > 0 && getAllSelectedCount() == allSelectableCount);
        this.mode = mode;
        setItems(FXCollections.observableArrayList(filesInfo));
    }

    private void clearCache() {
        cellCache.clear();
    }

    private void updateSortToggles(SortBy sortBy, SortDirection sortDirection) {
        for (Toggle toggle : sortByGroup.getToggles()) {
            if (Objects.equals(toggle.getUserData(), sortBy)) {
                toggle.setSelected(true);
            }
        }

        for (Toggle toggle : sortDirectionGroup.getToggles()) {
            if (Objects.equals(toggle.getUserData(), sortDirection)) {
                toggle.setSelected(true);
            }
        }
    }

    public void clearTable() {
        clearCache();
        sortByGroup.selectToggle(null);
        sortDirectionGroup.selectToggle(null);
        allSelectableCount = 0;
        selectedAvailableCount = 0;
        selectedUnavailableCount = 0;

        /*
         * It's very important that this line goes after the modification of the previous counters
         * (selectedAvailableCount and selectedUnavailableCount) because this property will have subscribers and they
         * need updated counter values there.
         */
        allSelectedCount.setValue(0);

        allSelectedCheckBox.setSelected(false);
        this.mode = null;
        setItems(FXCollections.emptyObservableList());
        System.gc();
    }

    public void setSelected(boolean selected, TableFileInfo fileInfo) {
        if (fileInfo.isSelected() == selected) {
            return;
        }

        fileInfo.setSelected(selected);

        handlerFileSelectionChange(selected, fileInfo);
    }

    public void removeFileWithSubtitles(TableSubtitleOption option, TableFileInfo fileInfo) {
        if (!option.isRemovable()) {
            log.error("option " + option.getId() + " is not removable");
            throw new IllegalStateException();
        }

        option.setId(null);
        option.setTitle(null);
        option.setSize(UNKNOWN_SIZE);
        option.setUnavailabilityReason(null);
        option.setSelectedAsUpper(false);
        option.setSelectedAsLower(false);

        fileInfo.setVisibleOptionCount(fileInfo.getVisibleOptionCount() - 1);

        TableSubtitleOption upperOption = fileInfo.getUpperOption();
        if (upperOption != null && Objects.equals(option.getId(), upperOption.getId())) {
            fileInfo.setUpperOption(null);
        }

        TableSubtitleOption lowerOption = fileInfo.getLowerOption();
        if (lowerOption != null && Objects.equals(option.getId(), lowerOption.getId())) {
            fileInfo.setLowerOption(null);
        }

        fileInfo.setActionResult(ActionResult.onlySuccess("Subtitle file has been removed from the list successfully"));
    }

    public void subtitlesLoaded(
            boolean cancelled,
            LoadedSubtitles loadedSubtitles,
            TableSubtitleOption subtitleOption,
            TableFileInfo fileInfo
    ) {
        if (cancelled) {
            fileInfo.setActionResult(ActionResult.onlyWarn("Task has been cancelled"));
            return;
        }

        processSingleLoadedSubtitles(loadedSubtitles, subtitleOption, fileInfo);

        //todo maybe create an enum
        if (StringUtils.isBlank(loadedSubtitles.getFailedToLoadReason())) {
            fileInfo.setActionResult(ActionResult.onlySuccess("Subtitles have been loaded successfully"));
        } else {
            fileInfo.setActionResult(ActionResult.onlyError("Failed to load subtitles"));
        }
    }

    private void processSingleLoadedSubtitles(
            LoadedSubtitles loadedSubtitles,
            TableSubtitleOption subtitleOption,
            TableFileInfo fileInfo
    ) {
        subtitleOption.setSize(
                loadedSubtitles.getSize() != null
                        ? loadedSubtitles.getSize()
                        : UNKNOWN_SIZE
        );
        subtitleOption.setFailedToLoadReason(loadedSubtitles.getFailedToLoadReason());
        subtitleOption.setUnavailabilityReason(loadedSubtitles.getUnavailabilityReason());
        if (StringUtils.isBlank(loadedSubtitles.getFailedToLoadReason()) && loadedSubtitles.getSize() != null) {
            fileInfo.setOptionsWithUnknownSizeCount(fileInfo.getOptionsWithUnknownSizeCount() - 1);
        }
    }

    void subtitleOptionPreviewClosed(String unavailabilityReason, TableSubtitleOption subtitleOption) {
        subtitleOption.setUnavailabilityReason(unavailabilityReason);
    }

    void addFileWithSubtitles(
            String id,
            String title,
            int size,
            String unavailabilityReason,
            TableFileInfo fileInfo
    ) {
        TableSubtitleOption subtitleOption = getFirstEmptySubtitleOption(fileInfo);

        subtitleOption.setId(id);
        subtitleOption.setTitle(title);
        subtitleOption.setSize(size);
        subtitleOption.setUnavailabilityReason(unavailabilityReason);

        fileInfo.setVisibleOptionCount(fileInfo.getVisibleOptionCount() + 1);

        if (StringUtils.isBlank(unavailabilityReason)) {
            fileInfo.setActionResult(ActionResult.onlySuccess("Subtitle file has been added to the list successfully"));
        } else {
            fileInfo.setActionResult(
                    ActionResult.onlyWarn(
                            "File was added but it has an incorrect subtitle format, you can try and change the "
                                    + "encoding pressing the preview button"
                    )
            );
        }
    }

    private TableSubtitleOption getFirstEmptySubtitleOption(TableFileInfo fileInfo) {
        for (TableSubtitleOption option : fileInfo.getSubtitleOptions()) {
            if (!option.isRemovable()) {
                continue;
            }

            if (StringUtils.isBlank(option.getTitle())) {
                return option;
            }
        }

        log.error("no empty options, that shouldn't happen");
        throw new IllegalStateException();
    }

    void subtitlesLoaded(
            int subtitlesToLoadCount,
            int processedCount,
            int loadedSuccessfullyCount,
            int failedToLoadCount,
            Map<String, LoadedSubtitles> allLoadedSubtitles,
            TableFileInfo fileInfo
    ) {
        for (String optionId: allLoadedSubtitles.keySet()) {
            TableSubtitleOption subtitleOption = TableSubtitleOption.getById(optionId, fileInfo.getSubtitleOptions());

            processSingleLoadedSubtitles(allLoadedSubtitles.get(optionId), subtitleOption, fileInfo);
        }

        fileInfo.setActionResult(
                generateSubtitleLoadingActionResult(
                        subtitlesToLoadCount,
                        processedCount,
                        loadedSuccessfullyCount,
                        failedToLoadCount
                )
        );
    }

    private static ActionResult generateSubtitleLoadingActionResult(
            int subtitlesToLoadCount,
            int processedCount,
            int loadedSuccessfullyCount,
            int failedToLoadCount
    ) {
        String success = null;
        String warn = null;
        String error = null;

        if (subtitlesToLoadCount == 0) {
            warn = "There are no subtitles to load";
        } else if (processedCount == 0) {
            warn = "Task has been cancelled, nothing was loaded";
        } else if (loadedSuccessfullyCount == subtitlesToLoadCount) {
            success = GuiHelperMethods.getTextDependingOnTheCount(
                    loadedSuccessfullyCount,
                    "Subtitles have been loaded successfully",
                    "All %d subtitles have been loaded successfully"
            );
        } else if (failedToLoadCount == subtitlesToLoadCount) {
            error = GuiHelperMethods.getTextDependingOnTheCount(
                    failedToLoadCount,
                    "Failed to load subtitles",
                    "Failed to load all %d subtitles"
            );
        } else {
            if (loadedSuccessfullyCount != 0) {
                success = String.format(
                        "%d/%d subtitles have been loaded successfully",
                        loadedSuccessfullyCount,
                        subtitlesToLoadCount
                );
            }

            if (processedCount != subtitlesToLoadCount) {
                if (loadedSuccessfullyCount == 0) {
                    warn = GuiHelperMethods.getTextDependingOnTheCount(
                            subtitlesToLoadCount - processedCount,
                            String.format(
                                    "1/%d subtitle loadings has been cancelled",
                                    subtitlesToLoadCount
                            ),
                            String.format(
                                    "%%d/%d subtitle loadings have been cancelled",
                                    subtitlesToLoadCount
                            )
                    );
                } else {
                    warn = String.format(
                            "%d/%d cancelled",
                            subtitlesToLoadCount - processedCount,
                            subtitlesToLoadCount
                    );
                }
            }

            if (failedToLoadCount != 0) {
                error = String.format(
                        "%d/%d failed",
                        failedToLoadCount,
                        subtitlesToLoadCount
                );
            }
        }

        return new ActionResult(success, warn, error);
    }

    @AllArgsConstructor
    @Getter
    public static class LoadedSubtitles {
        private Integer size;

        private String failedToLoadReason;

        private String unavailabilityReason;
    }

    @AllArgsConstructor
    @Getter
    public enum SortBy {
        NAME,
        MODIFICATION_TIME,
        SIZE
    }

    @AllArgsConstructor
    @Getter
    public enum SortDirection {
        ASCENDING,
        DESCENDING
    }

    @FunctionalInterface
    public interface AllSelectedHandler {
        void handle(boolean allSelected);
    }

    @FunctionalInterface
    public interface SortByChangeHandler {
        void handle(SortBy sortBy);
    }

    @FunctionalInterface
    public interface SortDirectionChangeHandler {
        void handle(SortDirection sortDirection);
    }

    @FunctionalInterface
    public interface RemoveSubtitleOptionHandler {
        void remove(TableSubtitleOption subtitleOption, TableFileInfo fileInfo);
    }

    @FunctionalInterface
    public interface SingleSubtitleLoader {
        void loadSubtitles(TableSubtitleOption subtitleOption, TableFileInfo fileInfo);
    }

    @FunctionalInterface
    public interface SubtitleOptionPreviewHandler {
        void showPreview(TableSubtitleOption subtitleOption, TableFileInfo fileInfo);
    }

    @FunctionalInterface
    public interface AddFileWithSubtitlesHandler {
        void addFile(TableFileInfo fileInfo);
    }

    @FunctionalInterface
    public interface AllFileSubtitleLoader {
        void loadSubtitles(TableFileInfo fileInfo);
    }

    @FunctionalInterface
    public interface MergedSubtitlePreviewHandler {
        void showPreview(TableFileInfo fileInfo);
    }

    public int getAllSelectedCount() {
        return allSelectedCount.get();
    }

    public ReadOnlyIntegerProperty allSelectedCountProperty() {
        return allSelectedCount.getReadOnlyProperty();
    }

    @AllArgsConstructor
    private class TableWithFilesCell<T> extends TableCell<TableFileInfo, T> {
        private CellType cellType;

        private CellPaneGenerator cellPaneGenerator;

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);

            TableFileInfo fileInfo = getTableRow().getItem();

            if (empty || fileInfo == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            Pane pane = null;
            Map<CellType, Pane> fileInfoPanes = cellCache.get(fileInfo.getId());
            if (fileInfoPanes != null) {
                pane = cellCache.get(fileInfo.getId()).get(cellType);
            }

            if (pane == null) {
                Pane newPane = cellPaneGenerator.generatePane(fileInfo);

                if (fileInfo.getUnavailabilityReason() != null) {
                    if (cellType != CellType.SELECTED) {
                        newPane.getStyleClass().add(PANE_UNAVAILABLE_CLASS);
                    }
                } else {
                    setOrRemoveErrorClass(fileInfo, newPane);
                    fileInfo.actionResultProperty().addListener(observable -> setOrRemoveErrorClass(fileInfo, newPane));
                }

                pane = newPane;
                cellCache.putIfAbsent(fileInfo.getId(), new HashMap<>());
                cellCache.get(fileInfo.getId()).put(cellType, pane);
            }

            setGraphic(pane);
            setText(null);
        }

        private void setOrRemoveErrorClass(TableFileInfo fileInfo, Pane pane) {
            if (fileInfo.getActionResult() != null && !StringUtils.isBlank(fileInfo.getActionResult().getError())) {
                pane.getStyleClass().add(PANE_ERROR_CLASS);
            } else {
                pane.getStyleClass().remove(PANE_ERROR_CLASS);
            }
        }
    }

    @FunctionalInterface
    interface CellPaneGenerator {
        Pane generatePane(TableFileInfo fileInfo);
    }

    private enum CellType {
        SELECTED,
        FILE_DESCRIPTION,
        SUBTITLES
    }

    public enum Mode {
        SEPARATE_FILES,
        DIRECTORY
    }
}
