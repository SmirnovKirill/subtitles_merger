package kirill.subtitlesmerger.logic.core.entities;

import com.neovisionaries.i18n.LanguageAlpha3Code;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class Subtitles {
    private List<Subtitle> subtitles;

    private List<LanguageAlpha3Code> languages;
}
