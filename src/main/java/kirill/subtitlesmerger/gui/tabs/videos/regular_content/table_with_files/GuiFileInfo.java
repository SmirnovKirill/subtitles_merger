package kirill.subtitlesmerger.gui.tabs.videos.regular_content.table_with_files;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.joda.time.LocalDateTime;

import java.io.File;

@AllArgsConstructor
@Getter
public class GuiFileInfo {
    private File file;

    private LocalDateTime lastModified;

    /*
     * Time when file was added to the table. Helps to keep the order when files are added after initial selection.
     */
    private LocalDateTime added;

    private long size;

    private String unavailabilityReason;

    private String error;
}
