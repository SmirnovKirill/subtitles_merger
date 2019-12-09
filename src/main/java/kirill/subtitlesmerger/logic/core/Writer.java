package kirill.subtitlesmerger.logic.core;

import kirill.subtitlesmerger.logic.core.entities.Subtitle;
import kirill.subtitlesmerger.logic.core.entities.SubtitleLine;
import kirill.subtitlesmerger.logic.core.entities.Subtitles;
import org.joda.time.format.DateTimeFormat;

public class Writer {
    public static String toSubRipText(Subtitles subtitles) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < subtitles.getSubtitles().size(); i++) {
            Subtitle subtitle = subtitles.getSubtitles().get(i);

            result.append(subtitle.getNumber());
            result.append("\n");

            result.append(DateTimeFormat.forPattern("HH:mm:ss,SSS").print(subtitle.getFrom()));
            result.append(" --> ");
            result.append(DateTimeFormat.forPattern("HH:mm:ss,SSS").print(subtitle.getTo()));
            result.append("\n");

            for (int j = 0; j < subtitle.getLines().size(); j++) {
                SubtitleLine line = subtitle.getLines().get(j);

                result.append(line.getText());

                if (j != subtitle.getLines().size() - 1 || i != subtitles.getSubtitles().size() - 1) {
                    result.append("\n");
                }
            }

            if (i != subtitles.getSubtitles().size() - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }
}
