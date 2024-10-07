import com.thebuzzmedia.exiftool.ExifToolOptions;
import com.thebuzzmedia.exiftool.core.StandardOptions;

import java.util.List;

public class SupportUtf8FileNameOptions implements ExifToolOptions {
    private StandardOptions standardOptions;

    public SupportUtf8FileNameOptions(StandardOptions standardOptions) {
        this.standardOptions = standardOptions;
    }

    @Override
    public Iterable<String> serialize() {
        List<String> arguments = (List<String>) standardOptions.serialize();
        arguments.add("-charset");
        arguments.add("filename=UTF8");
        return arguments;
    }
}
