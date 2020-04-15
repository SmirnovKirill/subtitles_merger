package kirill.subtitlemerger.gui.tabs.videos.background;

import com.neovisionaries.i18n.LanguageAlpha3Code;
import javafx.application.Platform;
import kirill.subtitlemerger.logic.settings.MergeMode;
import kirill.subtitlemerger.logic.settings.Settings;
import kirill.subtitlemerger.gui.tabs.videos.table_with_files.TableFileInfo;
import kirill.subtitlemerger.gui.tabs.videos.table_with_files.TableSubtitleOption;
import kirill.subtitlemerger.gui.tabs.videos.table_with_files.TableWithFiles;
import kirill.subtitlemerger.gui.utils.GuiUtils;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunner;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunnerManager;
import kirill.subtitlemerger.gui.utils.entities.ActionResult;
import kirill.subtitlemerger.logic.core.SubRipWriter;
import kirill.subtitlemerger.logic.core.entities.Subtitles;
import kirill.subtitlemerger.logic.ffmpeg.Ffmpeg;
import kirill.subtitlemerger.logic.ffmpeg.FfmpegException;
import kirill.subtitlemerger.logic.ffmpeg.FfmpegInjectInfo;
import kirill.subtitlemerger.logic.ffmpeg.Ffprobe;
import kirill.subtitlemerger.logic.ffmpeg.json.JsonFfprobeFileInfo;
import kirill.subtitlemerger.logic.files.FileInfoGetter;
import kirill.subtitlemerger.logic.files.entities.FfmpegSubtitleStream;
import kirill.subtitlemerger.logic.files.entities.FileInfo;
import kirill.subtitlemerger.logic.files.entities.FileWithSubtitles;
import kirill.subtitlemerger.logic.files.entities.SubtitleOption;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.util.stream.Collectors.toList;

@CommonsLog
@AllArgsConstructor
public class MergeRunner implements BackgroundRunner<MergeRunner.Result> {
    private List<MergePreparationRunner.FileMergeInfo> filesMergeInfo;

    private List<File> confirmedFilesToOverwrite;

    private File directoryForTempFile;

    private List<TableFileInfo> displayedTableFilesInfo;

    private List<FileInfo> allFilesInfo;

    private TableWithFiles tableWithFiles;

    private Ffprobe ffprobe;

    private Ffmpeg ffmpeg;

    private Settings settings;

    @Override
    public Result run(BackgroundRunnerManager runnerManager) {
        int allFileCount = filesMergeInfo.size();
        int processedCount = 0;
        int finishedSuccessfullyCount = 0;
        int noAgreementCount = 0;
        int alreadyMergedCount = 0;
        int failedCount = 0;

        for (MergePreparationRunner.FileMergeInfo fileMergeInfo : filesMergeInfo) {
            TableFileInfo tableFileInfo = TableFileInfo.getById(fileMergeInfo.getId(), displayedTableFilesInfo);

            String progressMessagePrefix = getProgressMessagePrefix(processedCount, allFileCount, tableFileInfo);
            runnerManager.updateMessage(progressMessagePrefix + "...");

            FileInfo fileInfo = FileInfo.getById(fileMergeInfo.getId(), allFilesInfo);

            if (fileMergeInfo.getStatus() == MergePreparationRunner.FileMergeStatus.FAILED_TO_LOAD_SUBTITLES) {
                String message = GuiUtils.getTextDependingOnTheCount(
                        fileMergeInfo.getFailedToLoadSubtitlesCount(),
                        "Merge is unavailable because failed to load subtitles",
                        "Merge is unavailable because failed to load %d subtitles"
                );

                Platform.runLater(() -> tableWithFiles.setActionResult(ActionResult.onlyError(message), tableFileInfo));
                failedCount++;
            } else if (fileMergeInfo.getStatus() == MergePreparationRunner.FileMergeStatus.DUPLICATE) {
                String message = "Selected subtitles have already been merged";
                Platform.runLater(() -> tableWithFiles.setActionResult(ActionResult.onlyWarn(message), tableFileInfo));
                alreadyMergedCount++;
            } else if (fileMergeInfo.getStatus() == MergePreparationRunner.FileMergeStatus.OK) {
                if (noPermission(fileMergeInfo, confirmedFilesToOverwrite, settings)) {
                    String message = "Merge is unavailable because you need to agree to the file overwriting";
                    Platform.runLater(
                            () -> tableWithFiles.setActionResult(ActionResult.onlyWarn(message), tableFileInfo)
                    );
                    noAgreementCount++;
                } else {
                    try {
                        if (settings.getMergeMode() == MergeMode.ORIGINAL_VIDEOS) {
                            if (directoryForTempFile == null) {
                                String message = "Failed to get the directory for temp files";
                                Platform.runLater(
                                        () -> tableWithFiles.setActionResult(
                                                ActionResult.onlyError(message), tableFileInfo
                                        )
                                );
                                failedCount++;
                            } else {
                                String subtitleText = SubRipWriter.toText(
                                        fileMergeInfo.getMergedSubtitles(),
                                        settings.isPlainTextSubtitles()
                                );

                                FfmpegInjectInfo injectInfo = new FfmpegInjectInfo(
                                        subtitleText,
                                        fileInfo.getFfmpegSubtitleStreams().size(),
                                        getMergedSubtitleLanguage(fileMergeInfo),
                                        "merged-" + getOptionTitleForFfmpeg(fileMergeInfo.getUpperSubtitles())
                                                + "-" + getOptionTitleForFfmpeg(fileMergeInfo.getLowerSubtitles()),
                                        settings.isMakeMergedStreamsDefault(),
                                        fileInfo.getFfmpegSubtitleStreams().stream()
                                                .filter(FfmpegSubtitleStream::isDefaultDisposition)
                                                .map(FfmpegSubtitleStream::getFfmpegIndex)
                                                .collect(toList()),
                                        fileInfo.getFile(),
                                        directoryForTempFile
                                );

                                ffmpeg.injectSubtitlesToFile(injectInfo);

                                try {
                                    updateFileInfo(
                                            fileMergeInfo.getMergedSubtitles(),
                                            subtitleText.getBytes().length,
                                            fileInfo,
                                            tableFileInfo,
                                            ffprobe,
                                            settings
                                    );
                                } catch (FfmpegException | IllegalStateException e) {
                                    String message = "Subtitles have been merged but failed to update stream list";
                                    Platform.runLater(
                                            () -> tableWithFiles.setActionResult(
                                                    ActionResult.onlyWarn(message), tableFileInfo
                                            )
                                    );
                                }

                                finishedSuccessfullyCount++;
                            }
                        } else if (settings.getMergeMode() == MergeMode.SEPARATE_SUBTITLE_FILES) {
                            FileUtils.writeStringToFile(
                                    fileMergeInfo.getFileWithResult(),
                                    SubRipWriter.toText(
                                            fileMergeInfo.getMergedSubtitles(),
                                            settings.isPlainTextSubtitles()
                                    ),
                                    StandardCharsets.UTF_8
                            );

                            finishedSuccessfullyCount++;
                        } else {
                            throw new IllegalStateException();
                        }
                    } catch (IOException e) {
                        String message = "Failed to write the result, probably there is no access to the file";
                        Platform.runLater(
                                () -> tableWithFiles.setActionResult(ActionResult.onlyError(message), tableFileInfo)
                        );
                        failedCount++;
                    } catch (FfmpegException e) {
                        log.warn("failed to merge: " + e.getCode() + ", console output " + e.getConsoleOutput());
                        String message = "Ffmpeg returned an error";
                        Platform.runLater(
                                () -> tableWithFiles.setActionResult(ActionResult.onlyError(message), tableFileInfo)
                        );
                        failedCount++;
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } else {
                throw new IllegalStateException();
            }

            processedCount++;
        }

        List<TableFileInfo> filesToShowInfo = VideoTabBackgroundUtils.getSortedFilesInfo(
                displayedTableFilesInfo,
                settings.getSortBy(),
                settings.getSortDirection(),
                runnerManager
        );

        return new Result(
                new TableFilesToShowInfo(
                        filesToShowInfo,
                        tableWithFiles.getAllSelectableCount(),
                        tableWithFiles.getSelectedAvailableCount(),
                        tableWithFiles.getSelectedUnavailableCount()
                ),
                generateActionResult(
                        allFileCount,
                        processedCount,
                        finishedSuccessfullyCount,
                        noAgreementCount,
                        alreadyMergedCount,
                        failedCount
                )
        );
    }

    private static String getProgressMessagePrefix(int processedCount, int allFileCount, TableFileInfo fileInfo) {
        String progressPrefix = allFileCount > 1
                ? String.format("%d/%d ", processedCount + 1, allFileCount) + "stage 2 of 2: processing file "
                : "Stage 2 of 2: processing file ";

        return progressPrefix + fileInfo.getFilePath();
    }

    private static boolean noPermission(
            MergePreparationRunner.FileMergeInfo fileMergeInfo,
            List<File> confirmedFilesToOverwrite,
            Settings settings
    ) {
        if (settings.getMergeMode() != MergeMode.SEPARATE_SUBTITLE_FILES) {
            return false;
        }

        File fileWithResult = fileMergeInfo.getFileWithResult();

        return fileWithResult.exists() && !confirmedFilesToOverwrite.contains(fileWithResult);
    }

    private static LanguageAlpha3Code getMergedSubtitleLanguage(MergePreparationRunner.FileMergeInfo mergeInfo) {
        if (mergeInfo.getUpperSubtitles() instanceof FfmpegSubtitleStream) {
            return ((FfmpegSubtitleStream) mergeInfo.getUpperSubtitles()).getLanguage();
        } else if (mergeInfo.getLowerSubtitles() instanceof FfmpegSubtitleStream) {
            return ((FfmpegSubtitleStream) mergeInfo.getUpperSubtitles()).getLanguage();
        } else {
            return LanguageAlpha3Code.undefined;
        }
    }

    private static String getOptionTitleForFfmpeg(SubtitleOption subtitleOption) {
        if (subtitleOption instanceof FileWithSubtitles) {
            return "external";
        } else if (subtitleOption instanceof FfmpegSubtitleStream) {
            LanguageAlpha3Code language = ((FfmpegSubtitleStream) subtitleOption).getLanguage();
            return language != null ? language.toString() : "unknown";
        } else {
            throw new IllegalStateException();
        }
    }

    private static void updateFileInfo(
            Subtitles subtitles,
            int subtitleSize,
            FileInfo fileInfo,
            TableFileInfo tableFileInfo,
            Ffprobe ffprobe,
            Settings settings
    ) throws FfmpegException, InterruptedException {
        //todo diagnostics
        JsonFfprobeFileInfo ffprobeInfo = ffprobe.getFileInfo(fileInfo.getFile());
        List<FfmpegSubtitleStream> subtitleOptions = FileInfoGetter.getSubtitleOptions(ffprobeInfo);

        List<String> currentOptionsIds = fileInfo.getSubtitleOptions().stream()
                .map(SubtitleOption::getId)
                .collect(toList());
        List<FfmpegSubtitleStream> newSubtitleOptions = subtitleOptions.stream()
                .filter(option -> !currentOptionsIds.contains(option.getId()))
                .collect(toList());
        if (newSubtitleOptions.size() != 1) {
            throw new IllegalStateException();
        }

        FfmpegSubtitleStream subtitleOption = newSubtitleOptions.get(0);
        subtitleOption.setSubtitlesAndSize(subtitles, subtitleSize);

        fileInfo.getSubtitleOptions().add(subtitleOption);
        fileInfo.setCurrentSizeAndLastModified();

        boolean haveHideableOptions = tableFileInfo.getSubtitleOptions().stream()
                .anyMatch(TableSubtitleOption::isHideable);
        TableSubtitleOption newSubtitleOption = VideoTabBackgroundUtils.tableSubtitleOptionFrom(
                subtitleOption,
                haveHideableOptions,
                settings
        );

        tableFileInfo.addSubtitleOption(newSubtitleOption);
        tableFileInfo.updateSizeAndLastModified(fileInfo.getSize(), fileInfo.getLastModified());
    }

    private static ActionResult generateActionResult(
            int allFileCount,
            int processedCount,
            int finishedSuccessfullyCount,
            int noAgreementCount,
            int alreadyMergedCount,
            int failedCount
    ) {
        String success = null;
        String warn = null;
        String error = null;

        if (processedCount == 0) {
            warn = "Task has been cancelled, nothing was done";
        } else if (finishedSuccessfullyCount == allFileCount) {
            success = GuiUtils.getTextDependingOnTheCount(
                    finishedSuccessfullyCount,
                    "Merge has finished successfully for the file",
                    "Merge has finished successfully for all %d files"
            );
        } else if (noAgreementCount == allFileCount) {
            warn = GuiUtils.getTextDependingOnTheCount(
                    noAgreementCount,
                    "Merge is not possible because you didn't agree to overwrite the file",
                    "Merge is not possible because you didn't agree to overwrite all %d files"
            );
        } else if (alreadyMergedCount == allFileCount) {
            warn = GuiUtils.getTextDependingOnTheCount(
                    alreadyMergedCount,
                    "Selected subtitles have already been merged",
                    "Selected subtitles have already been merged for all %d files"
            );
        } else if (failedCount == allFileCount) {
            error = GuiUtils.getTextDependingOnTheCount(
                    failedCount,
                    "Failed to merge subtitles for the file",
                    "Failed to merge subtitles for all %d files"
            );
        } else {
            if (finishedSuccessfullyCount != 0) {
                success = String.format(
                        "Merge has finished for %d/%d files successfully",
                        finishedSuccessfullyCount,
                        allFileCount
                );
            }

            if (processedCount != allFileCount) {
                if (finishedSuccessfullyCount == 0) {
                    warn = String.format(
                            "Merge has been cancelled for %d/%d files",
                            allFileCount - processedCount,
                            allFileCount
                    );
                } else {
                    warn = String.format("cancelled for %d/%d", allFileCount - processedCount, allFileCount);
                }
            }

            if (noAgreementCount != 0) {
                if (processedCount != allFileCount) {
                    warn += String.format(", no agreement for %d/%d", noAgreementCount, allFileCount);
                } else if (finishedSuccessfullyCount != 0) {
                    warn = String.format("no agreement for %d/%d", noAgreementCount, allFileCount);
                } else {
                    warn = String.format(
                            "No agreement for %d/%d files",
                            noAgreementCount,
                            allFileCount
                    );
                }
            }

            if (alreadyMergedCount != 0) {
                if (processedCount != allFileCount || noAgreementCount != 0) {
                    warn += String.format(", already merged for %d/%d", alreadyMergedCount, allFileCount);
                } else if (finishedSuccessfullyCount != 0) {
                    warn = String.format("already merged for %d/%d", alreadyMergedCount, allFileCount);
                } else {
                    warn = String.format(
                            "Subtitles have already been merged for %d/%d files",
                            alreadyMergedCount,
                            allFileCount
                    );
                }
            }

            if (failedCount != 0) {
                error = String.format("failed for %d/%d", failedCount, allFileCount);
            }
        }

        return new ActionResult(success, warn, error);
    }

    @AllArgsConstructor
    @Getter
    public static class Result {
        private TableFilesToShowInfo tableFilesToShowInfo;

        private ActionResult actionResult;
    }
}
