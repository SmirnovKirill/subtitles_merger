package kirill.subtitlemerger.gui.tabs.videos.regular_content.background_tasks;

import javafx.application.Platform;
import javafx.scene.control.ProgressIndicator;
import kirill.subtitlemerger.gui.GuiSettings;
import kirill.subtitlemerger.gui.core.GuiUtils;
import kirill.subtitlemerger.gui.core.background_tasks.BackgroundTask;
import kirill.subtitlemerger.gui.core.entities.MultiPartResult;
import kirill.subtitlemerger.gui.tabs.videos.regular_content.table_with_files.GuiFileInfo;
import kirill.subtitlemerger.gui.tabs.videos.regular_content.table_with_files.GuiSubtitleStream;
import kirill.subtitlemerger.logic.core.Parser;
import kirill.subtitlemerger.logic.work_with_files.entities.FileInfo;
import kirill.subtitlemerger.logic.work_with_files.entities.SubtitleStream;
import kirill.subtitlemerger.logic.work_with_files.ffmpeg.Ffmpeg;
import kirill.subtitlemerger.logic.work_with_files.ffmpeg.FfmpegException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AutoSelectSubtitlesTask extends BackgroundTask<AutoSelectSubtitlesTask.Result> {
    private static final Comparator<? super SubtitleStream> STREAM_COMPARATOR = Comparator.comparing(
            (SubtitleStream stream) -> stream.getSubtitles().getSize()
    ).reversed();

    private List<FileInfo> allFilesInfo;

    private List<GuiFileInfo> displayedGuiFilesInfo;

    private Ffmpeg ffmpeg;

    private GuiSettings guiSettings;

    private Consumer<Result> onFinish;

    public AutoSelectSubtitlesTask(
            List<FileInfo> allFilesInfo,
            List<GuiFileInfo> displayedGuiFilesInfo,
            Ffmpeg ffmpeg,
            GuiSettings guiSettings,
            Consumer<Result> onFinish
    ) {
        this.allFilesInfo = allFilesInfo;
        this.displayedGuiFilesInfo = displayedGuiFilesInfo;
        this.ffmpeg = ffmpeg;
        this.guiSettings = guiSettings;
        this.onFinish = onFinish;
    }

    @Override
    protected Result run() {
        LoadDirectoryFilesTask.clearFileInfoResults(displayedGuiFilesInfo, this);

        List<GuiFileInfo> guiFilesInfoToWorkWith = getGuiFilesInfoToWorkWith(displayedGuiFilesInfo, this);

        Result result = new Result(
                guiFilesInfoToWorkWith.size(),
                0,
                0,
                0,
                0
        );
        setCancellationPossible(true);

        for (GuiFileInfo guiFileInfo : guiFilesInfoToWorkWith) {
            if (super.isCancelled()) {
                return result;
            }

            FileInfo fileInfo = GuiUtils.findMatchingFileInfo(guiFileInfo, allFilesInfo);
            if (CollectionUtils.isEmpty(fileInfo.getSubtitleStreams())) {
                result.setNotPossibleCount(result.getNotPossibleCount() + 1);
                result.setProcessedCount(result.getProcessedCount() + 1);
                continue;
            }

            List<SubtitleStream> matchingUpperSubtitles = getMatchingUpperSubtitles(fileInfo, guiSettings);
            List<SubtitleStream> matchingLowerSubtitles = getMatchingLowerSubtitles(fileInfo, guiSettings);
            if (CollectionUtils.isEmpty(matchingUpperSubtitles) || CollectionUtils.isEmpty(matchingLowerSubtitles)) {
                result.setNotPossibleCount(result.getNotPossibleCount() + 1);
                result.setProcessedCount(result.getProcessedCount() + 1);
                continue;
            }

            try {
                boolean loadedSuccessfully = loadSizesIfNecessary(
                        fileInfo.getFile(),
                        matchingUpperSubtitles,
                        matchingLowerSubtitles,
                        guiFileInfo.getSubtitleStreams(),
                        result
                );
                if (!loadedSuccessfully) {
                    result.setFailedCount(result.getFailedCount() + 1);
                    result.setProcessedCount(result.getProcessedCount() + 1);
                    continue;
                }

                if (matchingUpperSubtitles.size() > 1) {
                    matchingUpperSubtitles.sort(STREAM_COMPARATOR);
                }
                if (matchingLowerSubtitles.size() > 1) {
                    matchingLowerSubtitles.sort(STREAM_COMPARATOR);
                }

                GuiUtils.findMatchingGuiStream(
                        matchingUpperSubtitles.get(0).getFfmpegIndex(),
                        guiFileInfo.getSubtitleStreams()
                ).setSelectedAsUpper(true);

                GuiUtils.findMatchingGuiStream(
                        matchingLowerSubtitles.get(0).getFfmpegIndex(),
                        guiFileInfo.getSubtitleStreams()
                ).setSelectedAsLower(true);

                guiFileInfo.setHaveSubtitleSizesToLoad(fileInfo.haveSubtitlesToLoad());

                result.setFinishedSuccessfullyCount(result.getFinishedSuccessfullyCount() + 1);
                result.setProcessedCount(result.getProcessedCount() + 1);
            } catch (FfmpegException e) {
                if (e.getCode() == FfmpegException.Code.INTERRUPTED) {
                    return result;
                } else {
                    throw new IllegalStateException();
                }
            }
        }

        return result;
    }

    private static List<GuiFileInfo> getGuiFilesInfoToWorkWith(
            List<GuiFileInfo> displayedGuiFilesInfo,
            AutoSelectSubtitlesTask task
    ) {
        task.updateProgress(ProgressIndicator.INDETERMINATE_PROGRESS, ProgressIndicator.INDETERMINATE_PROGRESS);
        task.updateMessage("getting list of files to work with...");

        return displayedGuiFilesInfo.stream().filter(GuiFileInfo::isSelected).collect(Collectors.toList());
    }

    private static List<SubtitleStream> getMatchingUpperSubtitles(FileInfo fileInfo, GuiSettings guiSettings) {
        return fileInfo.getSubtitleStreams().stream()
                .filter(stream -> stream.getUnavailabilityReason() == null)
                .filter(stream -> stream.getLanguage() == guiSettings.getUpperLanguage())
                .collect(Collectors.toList());
    }

    private static List<SubtitleStream> getMatchingLowerSubtitles(FileInfo fileInfo, GuiSettings guiSettings) {
        return fileInfo.getSubtitleStreams().stream()
                .filter(stream -> stream.getUnavailabilityReason() == null)
                .filter(stream -> stream.getLanguage() == guiSettings.getLowerLanguage())
                .collect(Collectors.toList());
    }

    private boolean loadSizesIfNecessary(
            File file,
            List<SubtitleStream> upperSubtitleStreams,
            List<SubtitleStream> lowerSubtitleStreams,
            List<GuiSubtitleStream> guiSubtitleStreams,
            Result taskResult
    ) throws FfmpegException {
        boolean result = true;

        List<SubtitleStream> subtitlesToLoad = new ArrayList<>();
        if (upperSubtitleStreams.size() > 1) {
            subtitlesToLoad.addAll(upperSubtitleStreams);
        }
        if (lowerSubtitleStreams.size() > 1) {
            subtitlesToLoad.addAll(lowerSubtitleStreams);
        }

        for (SubtitleStream subtitleStream : subtitlesToLoad) {
            updateMessage(
                    getUpdateMessage(
                            taskResult.getProcessedCount(),
                            taskResult.getAllFileCount(),
                            subtitleStream,
                            file
                    )
            );

            if (subtitleStream.getSubtitles() != null) {
                continue;
            }

            GuiSubtitleStream guiSubtitleStream = GuiUtils.findMatchingGuiStream(
                    subtitleStream.getFfmpegIndex(),
                    guiSubtitleStreams
            );

            try {
                String subtitleText = ffmpeg.getSubtitlesText(subtitleStream.getFfmpegIndex(), file);
                subtitleStream.setSubtitles(
                        Parser.fromSubRipText(
                                subtitleText,
                                subtitleStream.getTitle(),
                                subtitleStream.getLanguage()
                        )
                );

                /*
                 * Have to call this in the JavaFX thread because this change can lead to updates on the screen.
                 */
                Platform.runLater(() -> guiSubtitleStream.setSize(subtitleStream.getSubtitles().getSize()));
            } catch (FfmpegException e) {
                if (e.getCode() == FfmpegException.Code.INTERRUPTED) {
                    throw e;
                }

                result = false;
                Platform.runLater(() -> guiSubtitleStream.setFailedToLoadReason(LoadSubtitlesTask.guiTextFrom(e)));
            } catch (Parser.IncorrectFormatException e) {
                result = false;
                Platform.runLater(() -> guiSubtitleStream.setFailedToLoadReason("subtitles seem to have incorrect format"));
            }
        }

        return result;
    }

    private static String getUpdateMessage(
            int processedCount,
            int allSubtitleCount,
            SubtitleStream subtitleStream,
            File file
    ) {
        String language = subtitleStream.getLanguage() != null
                ? subtitleStream.getLanguage().toString().toUpperCase()
                : "UNKNOWN LANGUAGE";

        String progressPrefix = allSubtitleCount > 1
                ? (processedCount + 1) + "/" + allSubtitleCount + " "
                : "";

        return progressPrefix + "getting subtitle "
                + language
                + (StringUtils.isBlank(subtitleStream.getTitle()) ? "" : " " + subtitleStream.getTitle())
                + " in " + file.getName();
    }

    public static MultiPartResult generateMultiPartResult(Result taskResult) {
        String success = null;
        String warn = null;
        String error = null;

        if (taskResult.getProcessedCount() == 0) {
            warn = "Task has been cancelled, nothing was done";
        } else if (taskResult.getFinishedSuccessfullyCount() == taskResult.getAllFileCount()) {
            success = GuiUtils.getTextDependingOnTheCount(
                    taskResult.getFinishedSuccessfullyCount(),
                    "Auto-selection has finished successfully for the file",
                    "Auto-selection has finished successfully for all %d files"
            );
        } else if (taskResult.getNotPossibleCount() == taskResult.getAllFileCount()) {
            warn = GuiUtils.getTextDependingOnTheCount(
                    taskResult.getNotPossibleCount(),
                    "Auto-selection is not possible for the file",
                    "Auto-selection is not possible for all %d files"
            );
        } else if (taskResult.getFailedCount() == taskResult.getAllFileCount()) {
            error = GuiUtils.getTextDependingOnTheCount(
                    taskResult.getFailedCount(),
                    "Failed to perform auto-selection for the file",
                    "Failed to perform auto-selection for all %d files"
            );
        } else {
            if (taskResult.getFinishedSuccessfullyCount() != 0) {
                success = String.format(
                        "Auto-selection has finished for %d/%d files successfully",
                        taskResult.getFinishedSuccessfullyCount(),
                        taskResult.getAllFileCount()
                );
            }

            if (taskResult.getProcessedCount() != taskResult.getAllFileCount()) {
                if (taskResult.getFinishedSuccessfullyCount() == 0) {
                    warn = String.format(
                            "Auto-selection has been canceled for %d/%d files",
                            taskResult.getAllFileCount() - taskResult.getProcessedCount(),
                            taskResult.getAllFileCount()
                    );
                } else {
                    warn = String.format(
                            "canceled for %d/%d",
                            taskResult.getAllFileCount() - taskResult.getProcessedCount(),
                            taskResult.getAllFileCount()
                    );
                }
            }

            if (taskResult.getNotPossibleCount() != 0) {
                if (taskResult.getProcessedCount() != taskResult.getAllFileCount()) {
                    warn += String.format(
                            ", not possible for %d/%d",
                            taskResult.getNotPossibleCount(),
                            taskResult.getAllFileCount()
                    );
                } else if (taskResult.getFinishedSuccessfullyCount() != 0) {
                    warn = String.format(
                            "not possible for %d/%d",
                            taskResult.getNotPossibleCount(),
                            taskResult.getAllFileCount()
                    );
                } else {
                    warn = String.format(
                            "Auto-selection is not possible for %d/%d files",
                            taskResult.getNotPossibleCount(),
                            taskResult.getAllFileCount()
                    );
                }
            }

            if (taskResult.getFailedCount() != 0) {
                error = String.format(
                        "failed for %d/%d",
                        taskResult.getFailedCount(),
                        taskResult.getAllFileCount()
                );
            }
        }

        return new MultiPartResult(success, warn, error);
    }

    @Override
    protected void onFinish(Result result) {
        onFinish.accept(result);
    }

    @AllArgsConstructor
    @Getter
    @Setter
    public static class Result {
        private int allFileCount;

        private int processedCount;

        private int finishedSuccessfullyCount;

        private int notPossibleCount;

        private int failedCount;
    }
}
