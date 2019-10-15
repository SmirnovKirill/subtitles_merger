package kirill.subtitles_merger.ffprobe.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class JsonFfprobeFileInfo {
    @JsonProperty(value = "streams")
    private List<JsonStream> streams;
}
