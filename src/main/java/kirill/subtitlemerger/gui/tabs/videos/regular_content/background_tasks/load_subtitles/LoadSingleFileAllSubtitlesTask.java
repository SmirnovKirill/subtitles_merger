package kirill.subtitlemerger.gui.tabs.videos.regular_content.background_tasks.load_subtitles;

import javafx.beans.property.BooleanProperty;
import kirill.subtitlemerger.gui.tabs.videos.regular_content.table_with_files.GuiFileInfo;
import kirill.subtitlemerger.logic.work_with_files.entities.FileInfo;
import kirill.subtitlemerger.logic.work_with_files.entities.SubtitleStream;
import kirill.subtitlemerger.logic.work_with_files.ffmpeg.Ffmpeg;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;

public class LoadSingleFileAllSubtitlesTask extends LoadSubtitlesTask {
    private FileInfo fileInfo;

    private GuiFileInfo guiFileInfo;

    public LoadSingleFileAllSubtitlesTask(
            FileInfo fileInfo,
            GuiFileInfo guiFileInfo,
            Ffmpeg ffmpeg,
            BooleanProperty cancelTaskPaneVisible
    ) {
        super(ffmpeg, cancelTaskPaneVisible);

        this.fileInfo = fileInfo;
        this.guiFileInfo = guiFileInfo;
    }

    @Override
    protected Void call() {
        allSubtitleCount = getAllSubtitleCount(fileInfo);
        loadedBeforeCount = getLoadedBeforeCount(fileInfo);

        load(null, Collections.singletonList(guiFileInfo), Collections.singletonList(fileInfo));

        return null;
    }

    private static int getAllSubtitleCount(FileInfo fileInfo) {
        int result = 0;

        if (!CollectionUtils.isEmpty(fileInfo.getSubtitleStreams())) {
            for (SubtitleStream subtitleStream : fileInfo.getSubtitleStreams()) {
                if (subtitleStream.getUnavailabilityReason() != null) {
                    continue;
                }

                result++;
            }
        }

        return result;
    }

    private static int getLoadedBeforeCount(FileInfo fileInfo) {
        int result = 0;

        if (!CollectionUtils.isEmpty(fileInfo.getSubtitleStreams())) {
            for (SubtitleStream subtitleStream : fileInfo.getSubtitleStreams()) {
                if (subtitleStream.getUnavailabilityReason() != null) {
                    continue;
                }

                if (subtitleStream.getSubtitles() == null) {
                    continue;
                }

                result++;
            }
        }

        return result;
    }
}

