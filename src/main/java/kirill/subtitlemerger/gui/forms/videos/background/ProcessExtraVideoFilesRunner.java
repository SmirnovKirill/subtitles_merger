package kirill.subtitlemerger.gui.forms.videos.background;

import kirill.subtitlemerger.gui.GuiConstants;
import kirill.subtitlemerger.gui.GuiContext;
import kirill.subtitlemerger.gui.forms.videos.table.TableData;
import kirill.subtitlemerger.gui.forms.videos.table.TableMode;
import kirill.subtitlemerger.gui.forms.videos.table.TableVideo;
import kirill.subtitlemerger.gui.forms.videos.table.TableWithVideos;
import kirill.subtitlemerger.gui.utils.background.BackgroundManager;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunner;
import kirill.subtitlemerger.logic.utils.Utils;
import kirill.subtitlemerger.logic.utils.entities.ActionResult;
import kirill.subtitlemerger.logic.videos.entities.Video;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.ListUtils;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static kirill.subtitlemerger.gui.forms.videos.background.VideosBackgroundUtils.getSortedVideos;
import static kirill.subtitlemerger.gui.forms.videos.background.VideosBackgroundUtils.getTableData;

@AllArgsConstructor
public class ProcessExtraVideoFilesRunner implements BackgroundRunner<ProcessExtraVideoFilesRunner.Result> {
    private List<File> videoFilesToAdd;

    private List<Video> allVideosOriginal;

    private List<TableVideo> allTableVideosOriginal;

    private boolean hideUnavailable;

    private TableWithVideos table;

    private GuiContext context;

    @Override
    public Result run(BackgroundManager backgroundManager) {
        List<Video> videosToAdd = VideosBackgroundUtils.getVideos(
                videoFilesToAdd,
                context.getFfprobe(),
                backgroundManager
        );
        removeAlreadyAdded(videosToAdd, allVideosOriginal, backgroundManager);

        if (videosToAdd.size() + allVideosOriginal.size() > GuiConstants.VIDEO_TABLE_LIMIT) {
            return new Result(
                    ActionResult.onlyError("There will be too many videos (>" + GuiConstants.VIDEO_TABLE_LIMIT + ")"),
                    null,
                    null,
                    null
            );
        }

        List<TableVideo> tableVideosToAdd = VideosBackgroundUtils.tableVideosFrom(
                videosToAdd,
                true,
                true,
                table,
                context.getSettings(),
                backgroundManager
        );

        List<Video> allVideos = ListUtils.union(allVideosOriginal, videosToAdd);
        List<TableVideo> allTableVideos = ListUtils.union(allTableVideosOriginal, tableVideosToAdd);
        allTableVideos = getSortedVideos(allTableVideos, context.getSettings().getSort(), backgroundManager);

        return new Result(
                getActionResult(videoFilesToAdd.size(), videosToAdd.size()),
                allVideos,
                allTableVideos,
                getTableData(
                        allTableVideos,
                        hideUnavailable,
                        TableMode.SEPARATE_VIDEOS,
                        context.getSettings().getSort(),
                        backgroundManager
                )
        );
    }

    private static void removeAlreadyAdded(
            List<Video> videosToAdd,
            List<Video> allVideos,
            BackgroundManager backgroundManager
    ) {
        backgroundManager.setCancelPossible(false);
        backgroundManager.setIndeterminateProgress();
        backgroundManager.updateMessage("Removing already added videos...");

        Iterator<Video> iterator = videosToAdd.iterator();
        while (iterator.hasNext()) {
            Video video = iterator.next();

            boolean alreadyAdded = allVideos.stream()
                    .anyMatch(currentVideo -> Objects.equals(currentVideo.getFile(), video.getFile()));
            if (alreadyAdded) {
                iterator.remove();
            }
        }
    }

    private static ActionResult getActionResult(int videosToAddCount, int actuallyAddedCount) {
        String message;

        if (actuallyAddedCount == 0) {
            message = Utils.getTextDependingOnCount(
                    videosToAddCount,
                    "The video has been added already",
                    "All %d videos have been added already"
            );
        } else if (actuallyAddedCount == videosToAddCount) {
            message = Utils.getTextDependingOnCount(
                    actuallyAddedCount,
                    "The video has been added successfully",
                    "All %d videos have been added successfully"
            );
        } else {
            message = Utils.getTextDependingOnCount(
                    actuallyAddedCount,
                    String.format("1/%d videos has been added successfully, ", videosToAddCount),
                    String.format("%%d/%d videos have been added successfully, ", videosToAddCount)
            );
            message += (videosToAddCount - actuallyAddedCount) + "/" + videosToAddCount + " added before";
        }

        return new ActionResult(message, null, null);
    }

    @AllArgsConstructor
    @Getter
    public static class Result {
        private ActionResult actionResult;

        private List<Video> allVideos;

        private List<TableVideo> allTableVideos;

        private TableData tableData;
    }
}
