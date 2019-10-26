package kirill.subtitles_merger.ffmpeg;

import com.neovisionaries.i18n.LanguageAlpha3Code;
import kirill.subtitles_merger.logic.Subtitles;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@CommonsLog
public class Ffmpeg {
    private static final File TEMP_SUBTITLE_FILE = new File(
            System.getProperty("java.io.tmpdir"),
            "subtitles_merger_temp.srt"
    );

    private String path;

    public Ffmpeg(String path) throws FfmpegException {
        validate(path);

        this.path = path;
    }

    public static void validate(String ffmpegPath) throws FfmpegException {
        try {
            String consoleOutput = ProcessRunner.run(
                    Arrays.asList(
                            ffmpegPath,
                            "-version"
                    )
            );

            if (!consoleOutput.startsWith("ffmpeg version")) {
                log.info("console output doesn't start with the ffmpeg version");
                throw new FfmpegException(FfmpegException.Code.INCORRECT_FFMPEG_PATH);
            }
        } catch (ProcessException e) {
            log.info("failed to check ffmpeg: " + e.getCode());
            throw new FfmpegException(FfmpegException.Code.INCORRECT_FFMPEG_PATH);
        }
    }

    /*
     * Synchronized потому что работаем с одним временным файлом.
     */
    public synchronized String getSubtitlesText(int streamIndex, File videoFile) throws FfmpegException {
        try {
            ProcessRunner.run(
                    /*
                     * -y нужно передавать чтобы дать согласие на перезаписывание файла, это всегда нужно делать
                     * потому что временный файл уже будет создан джавой на момент вызова ffmpeg.
                     */
                    Arrays.asList(
                            path,
                            "-y",
                            "-i",
                            videoFile.getAbsolutePath(),
                            "-map",
                            "0:" + streamIndex,
                            TEMP_SUBTITLE_FILE.getAbsolutePath()
                    )
            );
        } catch (ProcessException e) {
            log.warn("failed to extract subtitles with ffmpeg: " + e.getCode());
            throw new FfmpegException(FfmpegException.Code.GENERAL_ERROR);
        }

        try {
            return FileUtils.readFileToString(TEMP_SUBTITLE_FILE);
        } catch (IOException e) {
            log.warn("failed to read subtitles from the file: " + ExceptionUtils.getStackTrace(e));
            throw new FfmpegException(FfmpegException.Code.GENERAL_ERROR);
        }
    }

    /*
     * Synchronized потому что работаем с одним временным файлом для субтитров.
     */
    public synchronized void injectSubtitlesToFile(
            Subtitles subtitles,
            int existingSubtitlesLength,
            File videoFile
    ) throws FfmpegException {
        /*
         * Ffmpeg не может добавить к файлу субтитры и записать это в тот же файл.
         * Поэтому нужно сначала записать результат во временный файл а потом его переименовать.
         * На всякий случай еще сделаем проверку что новый файл больше чем старый, а то нехорошо будет если испортим
         * видео, его могли долго качать.
         */
        File outputTemp = new File(videoFile.getParentFile(), "temp_" + videoFile.getName());

        try {
            FileUtils.writeStringToFile(TEMP_SUBTITLE_FILE, subtitles.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("failed to write merged subtiutles to the temp file: " + ExceptionUtils.getStackTrace(e));
            throw new FfmpegException(FfmpegException.Code.GENERAL_ERROR);
        }

        LanguageAlpha3Code language = null;
        if (!CollectionUtils.isEmpty(subtitles.getLanguages())) {
            language = subtitles.getLanguages().get(0);
        }

        try {
            ProcessRunner.run(
                    getArgumentsInjectToFile(
                            videoFile,
                            TEMP_SUBTITLE_FILE,
                            outputTemp,
                            existingSubtitlesLength,
                            language
                    )
            );
        } catch (ProcessException e) {
            log.warn("failed to inject subtitles with ffmpeg: " + e.getCode());
            throw new FfmpegException(FfmpegException.Code.GENERAL_ERROR);
        }

        if (outputTemp.length() <= videoFile.length()) {
            log.warn("resulting file size is less than the original one");
            throw new FfmpegException(FfmpegException.Code.GENERAL_ERROR);
        }

        /*
         * Сохраним сначала этот признак чтобы потом вернуть все как было, а то если изначально файл рид онли
         * а мы его в процессе работы сделаем записываемым, нехорошо это так оставлять.
         */
        boolean originallyWritable = videoFile.canWrite();

        if (!videoFile.setWritable(true, true)) {
            log.warn("failed to make video file " + videoFile.getAbsolutePath() + " writable");
            throw new FfmpegException(FfmpegException.Code.GENERAL_ERROR);
        }

        try {
            Files.move(outputTemp.toPath(), videoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("failed to move temp video: " + ExceptionUtils.getStackTrace(e));

            if (!outputTemp.delete()) {
                log.error("failed to delete temp file " + outputTemp.getAbsolutePath());
            }

            throw new FfmpegException(FfmpegException.Code.FAILED_TO_MOVE_TEMP_VIDEO);
        }

        if (!originallyWritable) {
            if (!videoFile.setWritable(false, true)) {
                log.warn("failed to make video file " + videoFile.getAbsolutePath() + " not writable");
            }
        }
    }

    private List<String> getArgumentsInjectToFile(
            File videoFile,
            File subtitlesTemp,
            File outputTemp,
            int existingSubtitlesLength,
            LanguageAlpha3Code language
    ) {
        List<String> result = new ArrayList<>();

        result.add(path);
        result.add("-y");

        /*
         * Не сталкивался с таким, но прочел в документации ffmpeg и мне показалось что стоит добавить.
         * Потому что вдруг есть какие то неизвестные стримы в файле, пусть они без изменений копируются,
         * потому что задача метода просто добавить субтитры не меняя ничего другого.
         */
        result.add("-copy_unknown");

        result.addAll(Arrays.asList("-i", videoFile.getAbsolutePath()));
        result.addAll(Arrays.asList("-i", subtitlesTemp.getAbsolutePath()));
        result.addAll(Arrays.asList("-c", "copy"));

        /*
         * Очень важный аргумент, без него во многих видео может быть проблема.
         * https://video.stackexchange.com/questions/28719/srt-subtitles-added-to-mkv-with-ffmpeg-are-not-displayed
         */
        result.addAll(Arrays.asList("-max_interleave_delta", "0"));

        if (language != null) {
            result.addAll(Arrays.asList("-metadata:s:s:" + existingSubtitlesLength, "language=" + language.toString()));
        }
        result.addAll(Arrays.asList("-metadata:s:s:" + existingSubtitlesLength, "title=Merged subtitles"));
        result.addAll(Arrays.asList("-disposition:s:" + existingSubtitlesLength, "default"));
        result.addAll(Arrays.asList("-map", "0"));
        result.addAll(Arrays.asList("-map", "1"));
        result.add(outputTemp.getAbsolutePath());

        return result;
    }
}
