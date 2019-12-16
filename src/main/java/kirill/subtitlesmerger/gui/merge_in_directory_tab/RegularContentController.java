package kirill.subtitlesmerger.gui.merge_in_directory_tab;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import kirill.subtitlesmerger.logic.AppContext;
import kirill.subtitlesmerger.logic.Config;
import kirill.subtitlesmerger.logic.Constants;
import kirill.subtitlesmerger.logic.work_with_files.FileInfoGetter;
import kirill.subtitlesmerger.logic.work_with_files.entities.FileInfo;
import kirill.subtitlesmerger.logic.work_with_files.ffmpeg.Ffprobe;
import lombok.extern.apachecommons.CommonsLog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@CommonsLog
public class RegularContentController {
    private Stage stage;

    private AppContext appContext;

    @FXML
    private Pane pane;

    @FXML
    private Button directoryChooseButton;

    @FXML
    private Label directoryPathLabel;

    private DirectoryChooser directoryChooser;

    @FXML
    private CheckBox hideUnavailableCheckbox;

    @FXML
    private TableWithFiles tableWithFiles;

    public void init(Stage stage, AppContext appContext) {
        this.stage = stage;
        this.appContext = appContext;

        this.directoryChooser = generateDirectoryChooser();
    }

    private static DirectoryChooser generateDirectoryChooser() {
        DirectoryChooser result = new DirectoryChooser();

        result.setTitle("choose the directory with videos");

        return result;
    }

    @FXML
    private void directoryButtonClicked() {
        File directory = directoryChooser.showDialog(stage);
        if (directory == null) {
            return;
        }

        directoryPathLabel.setText(directory.getAbsolutePath());

        try {
            appContext.getConfig().saveLastDirectoryWithVideos(directory.getAbsolutePath());
        } catch (Config.ConfigException e) {
            log.error("failed to save last directory with videos, that shouldn't be possible");
            throw new IllegalStateException();
        }
        directoryChooser.setInitialDirectory(directory);

//        filesInfo = getBriefFilesInfo(directory.listFiles(), appContext.getFfprobe());

  //      tabView.getRegularContentPane().showFiles(filesInfo, stage, appContext);
    }

    private static List<FileInfo> getBriefFilesInfo(File[] files, Ffprobe ffprobe) {
        List<FileInfo> result = new ArrayList<>();

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            result.add(
                    FileInfoGetter.getFileInfoWithoutSubtitles(
                            file,
                            Constants.ALLOWED_VIDEO_EXTENSIONS,
                            Constants.ALLOWED_VIDEO_MIME_TYPES,
                            ffprobe
                    )
            );
        }

        return result;
    }

 /*   private void showOnlyValidCheckBoxChanged(
            ObservableValue<? extends Boolean> observable,
            Boolean oldValue,
            Boolean newValue
    ) {
        tabView.showProgressIndicator();
        if (Boolean.TRUE.equals(newValue)) {
            tabView.getRegularContentPane().showFiles(
                    filesInfo.stream()
                            .filter(file -> file.getUnavailabilityReason() == null)
                            .collect(Collectors.toList()),
                    stage,
                    appContext
            );
        } else {
            tabView.getRegularContentPane().showFiles(filesInfo, stage, appContext);
        }
        tabView.hideProgressIndicator();
    }*/

    void show() {
        pane.setVisible(true);
    }

    void hide() {
        pane.setVisible(false);
    }
}
