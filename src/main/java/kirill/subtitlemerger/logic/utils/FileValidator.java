package kirill.subtitlemerger.logic.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

public class FileValidator {
    private static final int PATH_LENGTH_LIMIT = 4096;

    public static Optional<InputFileInfo> getInputFileInfo(
            String path,
            Collection<String> allowedExtensions,
            long maxAllowedSize,
            boolean loadContent
    ) {
        if (StringUtils.isBlank(path)) {
            return Optional.empty();
        }

        if (path.length() > PATH_LENGTH_LIMIT) {
            return Optional.of(
                    new InputFileInfo(null, null, IncorrectInputFileReason.PATH_IS_TOO_LONG, null)
            );
        }

        File file = new File(path);
        if (!file.isFile()) {
            return Optional.of(new InputFileInfo(file, null, IncorrectInputFileReason.NOT_A_FILE, null));
        }

        if (!file.exists()) {
            return Optional.of(
                    new InputFileInfo(file, null, IncorrectInputFileReason.FILE_DOES_NOT_EXIST, null)
            );
        }

        String parentPath = file.getParent();
        if (StringUtils.isBlank(parentPath)) {
            return Optional.of(
                    new InputFileInfo(
                            file,
                            null,
                            IncorrectInputFileReason.FAILED_TO_GET_PARENT_DIRECTORY,
                            null
                    )
            );
        }

        File parent = new File(parentPath);
        if (!parent.exists() || !parent.isDirectory()) {
            new InputFileInfo(
                    file,
                    null,
                    IncorrectInputFileReason.FAILED_TO_GET_PARENT_DIRECTORY,
                    null
            );
        }

        String extension = FilenameUtils.getExtension(file.getAbsolutePath());
        if (!allowedExtensions.contains(extension)) {
            return Optional.of(
                    new InputFileInfo(file, parent, IncorrectInputFileReason.EXTENSION_IS_NOT_VALID, null)
            );
        }

        if (file.length() > maxAllowedSize) {
            return Optional.of(new InputFileInfo(file, parent, IncorrectInputFileReason.FILE_IS_TOO_BIG, null));
        }

        byte[] content = null;
        if (loadContent) {
            try {
                content = FileUtils.readFileToByteArray(file);
            } catch (IOException e) {
                return Optional.of(
                        new InputFileInfo(file, parent, IncorrectInputFileReason.FAILED_TO_READ_CONTENT, null)
                );
            }
        }

        return Optional.of(new InputFileInfo(file, parent, null, content));
    }

    public static Optional<OutputFileInfo> getOutputFileInfo(
            String path,
            Collection<String> allowedExtensions,
            boolean allowNonExistent
    ) {
        if (StringUtils.isBlank(path)) {
            return Optional.empty();
        }

        if (path.length() > PATH_LENGTH_LIMIT) {
            return Optional.of(new OutputFileInfo(null, null, IncorrectOutputFileReason.PATH_IS_TOO_LONG));
        }

        File file = new File(path);
        if (!file.isFile()) {
            return Optional.of(new OutputFileInfo(file, null, IncorrectOutputFileReason.NOT_A_FILE));
        }

        if (!allowNonExistent && !file.exists()) {
            return Optional.of(new OutputFileInfo(file, null, IncorrectOutputFileReason.FILE_DOES_NOT_EXIST));
        }

        String parentPath = file.getParent();
        if (StringUtils.isBlank(parentPath)) {
            return Optional.of(
                    new OutputFileInfo(file, null, IncorrectOutputFileReason.FAILED_TO_GET_PARENT_DIRECTORY)
            );
        }

        File parent = new File(parentPath);
        if (!parent.exists() || !parent.isDirectory()) {
            return Optional.of(
                    new OutputFileInfo(file, null, IncorrectOutputFileReason.FAILED_TO_GET_PARENT_DIRECTORY)
            );
        }

        String extension = FilenameUtils.getExtension(file.getAbsolutePath());
        if (!allowedExtensions.contains(extension)) {
            return Optional.of(new OutputFileInfo(file, parent, IncorrectOutputFileReason.EXTENSION_IS_NOT_VALID));
        }

        return Optional.of(new OutputFileInfo(file, parent, null));
    }

    @AllArgsConstructor
    @Getter
    public static class InputFileInfo {
        private File file;

        private File parent;

        private IncorrectInputFileReason incorrectFileReason;

        private byte[] content;
    }

    public enum IncorrectInputFileReason {
        PATH_IS_TOO_LONG,
        NOT_A_FILE,
        FILE_DOES_NOT_EXIST,
        FAILED_TO_GET_PARENT_DIRECTORY,
        EXTENSION_IS_NOT_VALID,
        FILE_IS_TOO_BIG,
        FAILED_TO_READ_CONTENT
    }

    @AllArgsConstructor
    @Getter
    public static class OutputFileInfo {
        private File file;

        private File parent;

        private IncorrectOutputFileReason incorrectFileReason;
    }

    public enum IncorrectOutputFileReason {
        PATH_IS_TOO_LONG,
        NOT_A_FILE,
        FILE_DOES_NOT_EXIST,
        FAILED_TO_GET_PARENT_DIRECTORY,
        EXTENSION_IS_NOT_VALID
    }
}
